import com.soywiz.klock.DateTime
import com.soywiz.klock.TimeProvider
import com.soywiz.klock.TimeSpan
import com.soywiz.korge.input.MouseEvents
import com.soywiz.korge.input.mouse
import com.soywiz.korge.view.*
import com.soywiz.korim.color.ColorTransform
import com.soywiz.korma.geom.Point
import com.soywiz.korma.geom.degrees
import com.soywiz.korma.geom.times

open class CardController(
        val view: View,
        var dx: Double = 0.0,
        var dy: Double = 0.0,
        var start: Boolean = false,
        var end: Boolean = false,
        var startTime: DateTime = DateTime.EPOCH,
        var time: DateTime = DateTime.EPOCH,
) {
    lateinit var mouseEvents: MouseEvents
    val elapsed: TimeSpan get() = time - startTime

    val localDX get() = localDX(view)
    val localDY get() = localDY(view)

    fun localDX(view: View) = view.parent?.globalToLocalDX(0.0, 0.0, dx, dy) ?: dx
    fun localDY(view: View) = view.parent?.globalToLocalDY(0.0, 0.0, dx, dy) ?: dy

    private var lastDx: Double = Double.NaN
    private var lastDy: Double = Double.NaN

    var deltaDx: Double = 0.0
    var deltaDy: Double = 0.0

    fun reset() {
        lastDx = Double.NaN
        lastDy = Double.NaN
        deltaDx = 0.0
        deltaDy = 0.0
        dx = 0.0
        dy = 0.0
    }

    fun set(dx: Double, dy: Double, start: Boolean, end: Boolean, time: DateTime): CardController {
        this.dx = dx
        this.dy = dy
        if (!lastDx.isNaN() && !lastDy.isNaN()) {
            this.deltaDx = lastDx - dx
            this.deltaDy = lastDy - dy
        }
        this.lastDx = dx
        this.lastDy = dy
        this.start = start
        this.end = end
        if (start) this.startTime = time
        this.time = time
        return this
    }
}

enum class MouseDragState {
    START, DRAG, END;

    val isDrag get() = this == DRAG
    val isStart get() = this == START
    val isEnd get() = this == END
}

fun <T : View> T.onCardDrag(timeProvider: TimeProvider = TimeProvider, info: CardController = CardController(this), callback: Views.(CardController) -> Unit): T {
    var dragging = false
    var sx = 0.0
    var sy = 0.0
    var cx = 0.0
    var cy = 0.0
    val view = this

    val mousePos = Point()

    fun views() = view.stage!!.views

    fun updateMouse() {
        val views = views()
        //println("views.globalMouse=${views.globalMouseXY}, views.nativeMouse=${views.nativeMouseXY}")
        //mousePos.copyFrom(views.globalMouseXY)
        mousePos.copyFrom(views.nativeMouseXY)
    }

    fun handle(it: MouseEvents, state: MouseDragState) {
        if (state != MouseDragState.START && !dragging) return
        updateMouse()
        info.mouseEvents = it
        val px = mousePos.x
        val py = mousePos.y
        when (state) {
            MouseDragState.START -> {
                dragging = true
                sx = px
                sy = py
                info.reset()
            }
            MouseDragState.END -> {
                dragging = false
            }
        }
        cx = mousePos.x
        cy = mousePos.y
        val dx = cx - sx
        val dy = cy - sy
        callback(views(), info.set(dx, dy, state.isStart, state.isEnd, timeProvider.now()))
    }

    this.mouse {
        onDown { handle(it, MouseDragState.START) }
        onUpAnywhere { handle(it, MouseDragState.END) }
        onMoveAnywhere { handle(it, MouseDragState.DRAG) }
    }
    return this
}

open class DraggableCardInfo(view: View) : CardController(view) {
    val viewStartXY = Point()

    var viewStartX: Double get() = viewStartXY.x ; set(value) { viewStartXY.x = value }
    var viewStartY: Double get() = viewStartXY.y ; set(value) { viewStartXY.y = value }

    val viewPrevXY = Point()

    var viewPrevX: Double get() = viewPrevXY.x ; set(value) { viewPrevXY.x = value }
    var viewPrevY: Double get() = viewPrevXY.y ; set(value) { viewPrevXY.y = value }

    val viewNextXY = Point()

    var viewNextX: Double get() = viewNextXY.x ; set(value) { viewNextXY.x = value }
    var viewNextY: Double get() = viewNextXY.y ; set(value) { viewNextXY.y = value }

    val viewDeltaXY = Point()

    var viewDeltaX: Double get() = viewDeltaXY.x ; set(value) { viewDeltaXY.x = value }
    var viewDeltaY: Double get() = viewDeltaXY.y ; set(value) { viewDeltaXY.y = value }
}

fun <T : View> T.draggableAsCard(sp : Point, selector: View = this,
                                 autoMove: Boolean = true,
                                 onDragAsCard: ((DraggableCardInfo) -> Unit)? = null,
                                 onChooseCard: ((DraggableCardInfo) -> Unit)? = null): T {
    val view = this
    val info = DraggableCardInfo(view)
    selector.onCardDrag(info = info) {
//        view.xy(bx, by) // start
        if (info.start) {
//            info.viewStartXY.copyFrom(view.pos)
            info.viewStartXY.copyFrom(sp)
        }
        //println("localDXY=${info.localDX(view)},${info.localDY(view)}")
        info.viewPrevXY.copyFrom(view.pos)
        info.viewNextXY.setTo(info.viewStartX + info.localDX(view), info.viewStartY + info.localDY(view))
        info.viewDeltaXY.setTo(info.viewNextX - info.viewPrevX, info.viewNextY - info.viewPrevY)
        if (autoMove) {
            view.rotation(45.degrees * ((-info.viewStartX + info.viewNextX) / virtualWidth))
            view.xy(info.viewNextXY)
//            println((-info.viewStartX + info.viewNextX) / virtualWidth)
            when ((-info.viewStartX + info.viewNextX) / virtualWidth) {
                in 0.3..1.0 -> {
                    view.colorTransform = ColorTransform(2)
                    view.alpha = 0.6
                }
                in -1.0..-0.3 -> {
//                    view.colorTransform = ColorTransform(0.2)
                    view.colorTransform = ColorTransform(-2)
                    view.alpha = 0.6
                }
                else -> {
                    view.colorTransform = ColorTransform(1, 1, 1, 1, 0, 0, 0, 0)
                    view.alpha = 0.9999
                }
            }
        }
        if (info.end){
            view.rotation(0.degrees)
            view.xy(sp)
            view.alpha = 1.0
            view.colorTransform = ColorTransform(1, 1, 1, 1, 0, 0, 0, 0)
//            info.viewStartXY.copyFrom(sp)
        }
//        println(view.pos)
        onDragAsCard?.invoke(info)
        onChooseCard?.invoke(info)
        //println("DRAG: $dx, $dy, $start, $end")
    }
    return this
}
