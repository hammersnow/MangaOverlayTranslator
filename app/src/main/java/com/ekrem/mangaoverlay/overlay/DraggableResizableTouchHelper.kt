package com.ekrem.mangaoverlay.overlay
import android.view.MotionEvent; import android.view.View; import android.view.WindowManager
class DraggableResizableTouchHelper(private val wm: WindowManager, private val view: View, private val lp: WindowManager.LayoutParams) : View.OnTouchListener {
  private var lastX = 0; private var lastY = 0; private val edge = 36; private var mode = Mode.DRAG
  private enum class Mode { DRAG, RESIZE }
  override fun onTouch(v: View, e: MotionEvent): Boolean {
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> { lastX = e.rawX.toInt(); lastY = e.rawY.toInt(); mode = if (isInResizeCorner(e.x.toInt(), e.y.toInt())) Mode.RESIZE else Mode.DRAG; return true }
      MotionEvent.ACTION_MOVE -> { val dx = (e.rawX - lastX).toInt(); val dy = (e.rawY - lastY).toInt(); lastX = e.rawX.toInt(); lastY = e.rawY.toInt()
        if (mode == Mode.DRAG) { lp.x += dx; lp.y += dy } else { lp.width = (view.width + dx).coerceAtLeast(160); lp.height = (view.height + dy).coerceAtLeast(100) }
        wm.updateViewLayout(view, lp); return true }
    }
    return false
  }
  private fun isInResizeCorner(x: Int, y: Int): Boolean { val w = view.width; val h = view.height; return (x > w - edge && y > h - edge) }
}
