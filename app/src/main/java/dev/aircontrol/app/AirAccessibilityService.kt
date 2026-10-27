package dev.aircontrol.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/** Sends swipes/taps/global actions. Does not read screen content (no canRetrieveWindowContent). */
class AirAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) {
        val p = Path(); p.moveTo(x1, y1); p.lineTo(x2, y2)
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
    }

    private var dot: android.view.View? = null
    private var lp: android.view.WindowManager.LayoutParams? = null
    var px = 0f; var py = 0f

    fun showPointer(show: Boolean) {
        val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        if (show && dot == null) {
            val v = android.view.View(this)
            v.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xAA22D3EE.toInt()); setStroke(4, 0xFFFFFFFF.toInt()) }
            val s = (28 * resources.displayMetrics.density).toInt()
            val l = android.view.WindowManager.LayoutParams(s, s, android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT)
            l.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            wm.addView(v, l); dot = v; lp = l
        } else if (!show && dot != null) { wm.removeView(dot); dot = null; lp = null }
    }

    fun movePointer(nx: Float, ny: Float) {
        val w = resources.displayMetrics.widthPixels; val h = resources.displayMetrics.heightPixels
        px = (nx.coerceIn(0f, 1f) * w); py = (ny.coerceIn(0f, 1f) * h)
        val l = lp ?: return; val v = dot ?: return
        l.x = (px - l.width / 2).toInt(); l.y = (py - l.height / 2).toInt()
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).updateViewLayout(v, l)
    }

    fun tapPointer() { swipe(px, py, px, py, 40) }

    fun perform(a: Act) {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        when (a) {
            Act.NEXT -> swipe(w * 0.5f, h * 0.75f, w * 0.5f, h * 0.25f, 220)
            Act.PREV -> swipe(w * 0.5f, h * 0.25f, w * 0.5f, h * 0.75f, 220)
            Act.PLAY_PAUSE -> swipe(w * 0.5f, h * 0.45f, w * 0.5f, h * 0.45f, 40)
            Act.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            Act.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            Act.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            Act.SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            else -> {}
        }
    }

    companion object { @Volatile var instance: AirAccessibilityService? = null }
}
