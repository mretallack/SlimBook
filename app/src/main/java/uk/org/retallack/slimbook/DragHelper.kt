package uk.org.retallack.slimbook

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup

/**
 * Makes a floating overlay (stats badge, Messenger button) draggable so the
 * user can move it away from whatever it covers (e.g. the comment composer
 * icons). The position is stored as a translation relative to the view's
 * layout position and clamped to the parent's bounds, so it survives rotation
 * and different screen sizes.
 *
 * Taps and long-presses still reach the view's own click listeners: the touch
 * listener only consumes the gesture once the finger moved past touch slop.
 */
object DragHelper {

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        view: View,
        initial: Pair<Float, Float>?,
        onMoved: (dx: Float, dy: Float) -> Unit
    ) {
        val parent = view.parent as? ViewGroup ?: return
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop

        fun clampAndApply(dx: Float, dy: Float) {
            if (parent.width == 0 || view.width == 0) return
            view.translationX = dx.coerceIn(-view.left.toFloat(), (parent.width - view.right).toFloat())
            view.translationY = dy.coerceIn(-view.top.toFloat(), (parent.height - view.bottom).toFloat())
        }

        // Re-apply (and re-clamp) whenever the layout changes (rotation, IME).
        var wantedX = initial?.first ?: 0f
        var wantedY = initial?.second ?: 0f
        parent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            clampAndApply(wantedX, wantedY)
        }
        view.post { clampAndApply(wantedX, wantedY) }

        var downRawX = 0f
        var downRawY = 0f
        var startTx = 0f
        var startTy = 0f
        var dragging = false

        view.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    startTx = v.translationX
                    startTy = v.translationY
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = ev.rawX - downRawX
                    val my = ev.rawY - downRawY
                    if (!dragging && (Math.abs(mx) > slop || Math.abs(my) > slop)) {
                        dragging = true
                        v.cancelLongPress()
                        v.isPressed = false
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        wantedX = startTx + mx
                        wantedY = startTy + my
                        clampAndApply(wantedX, wantedY)
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        wantedX = v.translationX
                        wantedY = v.translationY
                        onMoved(wantedX, wantedY)
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    fun reset(view: View) {
        view.translationX = 0f
        view.translationY = 0f
    }
}
