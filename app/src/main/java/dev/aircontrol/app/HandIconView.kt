package dev.aircontrol.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Looping vector demo of a hand pose or motion. Drawn from finger states, no images or fonts.
 * Timing mirrors the engine: static poses are held (progress ring) then fire; swipes are index-only
 * up/down travel; the pointer demo shows peace sign (toggle), index moves the cursor, thumb+index pinch = tap.
 */
class HandIconView(ctx: Context) : View(ctx) {
    enum class Demo { OPEN_PALM, FIST, VICTORY, THUMB_UP, THUMB_DOWN, POINT_UP, LOVE, SWIPE_UP, SWIPE_DOWN, POINTER, ARM, CUSTOM }

    var demo = Demo.OPEN_PALM
    private var frozen = false
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2800; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = ACCENT; isFakeBoldText = true }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!frozen) anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    /** Progress t in [0,1] used by tests to render a given frame. */
    fun setFrame(v: Float) { frozen = true; anim.cancel(); t = v; invalidate() }

    private fun ease(x: Float) = x * x * (3 - 2 * x)
    private fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    private fun lerp(a: Float, b: Float, x: Float) = a + (b - a) * x

    override fun onDraw(c: Canvas) {
        val s = min(width, height) / 100f
        c.save(); c.translate((width - 100 * s) / 2f, (height - 100 * s) / 2f); c.scale(s, s)
        when (demo) {
            Demo.CUSTOM -> drawPlus(c)
            Demo.SWIPE_UP, Demo.SWIPE_DOWN -> swipe(c, demo == Demo.SWIPE_UP)
            Demo.POINTER -> pointer(c)
            else -> staticPose(c)
        }
        c.restore()
    }

    private fun holdRing(c: Canvas, prog: Float, done: Boolean) {
        p.strokeWidth = 3f; p.color = 0x33FFFFFF
        c.drawArc(RectF(4f, 4f, 96f, 96f), 0f, 360f, false, p)
        p.color = if (done) 0xFF86EFAC.toInt() else ACCENT
        c.drawArc(RectF(4f, 4f, 96f, 96f), -90f, 360f * prog, false, p)
    }

    private fun staticPose(c: Canvas) {
        val hold = ease(seg(0.05f, 0.65f)); val done = t > 0.65f && t < 0.95f
        holdRing(c, if (t > 0.65f) 1f else hold, done)
        c.save()
        if (demo == Demo.THUMB_DOWN) c.rotate(180f, 50f, 58f)
        c.scale(0.9f, 0.9f, 50f, 50f)
        hand(c, demo, dy = 0f)
        c.restore()
        if (demo == Demo.ARM && t > 0.65f && t < 0.95f) { txt.textSize = 11f; c.drawText("ARMED 10s", 50f, 99f, txt) }
    }

    private fun swipe(c: Canvas, up: Boolean) {
        val m = ease(seg(0.15f, 0.5f)); val fade = 1f - seg(0.7f, 0.95f)
        val from = if (up) 16f else -16f; val to = -from
        val dy = lerp(from, to, m)
        c.save(); c.scale(0.8f, 0.8f, 50f, 50f)
        hand(c, Demo.POINT_UP, dy = dy, alpha = if (t > 0.7f) fade else 1f)
        c.restore()
        // direction arrow with start and end markers
        val x = 90f; val y0 = if (up) 78f else 22f; val y1 = if (up) 22f else 78f
        p.strokeWidth = 3f; p.color = 0x55FFFFFF
        c.drawLine(x, y0, x, y1, p)
        fill.color = 0x88FFFFFF.toInt(); c.drawCircle(x, y0, 3.5f, fill)
        val ay = lerp(y0, y1, m)
        fill.color = ACCENT; c.drawCircle(x, ay, 4.5f, fill)
        val d = if (up) 1 else -1
        val ph = Path().apply { moveTo(x - 6f, y1 + 7f * d); lineTo(x, y1); lineTo(x + 6f, y1 + 7f * d) }
        p.color = ACCENT; c.drawPath(ph, p)
    }

    private fun pointer(c: Canvas) {
        c.save(); c.scale(0.85f, 0.85f, 50f, 50f); c.translate(0f, -6f)
        val a = seg(0f, 0.25f); val b = seg(0.25f, 0.6f); val pin = seg(0.6f, 0.78f)
        when {
            t < 0.28f -> hand(c, Demo.VICTORY, dy = 0f)
            t < 0.6f -> hand(c, Demo.POINT_UP, dy = 0f, dx = lerp(-10f, 10f, ease(b)))
            else -> hand(c, Demo.POINT_UP, dy = 0f, dx = 10f, pinch = ease(pin), pinchMode = true)
        }
        c.restore()
        txt.textSize = 9f
        when {
            t < 0.28f -> c.drawText("peace = pointer on", 50f, 98f, txt)
            t < 0.6f -> { fill.color = ACCENT; c.drawCircle(lerp(30f, 70f, ease(b)), 18f, 5f, fill); c.drawText("move index finger", 50f, 98f, txt) }
            else -> {
                c.drawText("pinch = tap", 50f, 98f, txt)
                if (t in 0.7f..0.95f) { p.strokeWidth = 2.5f; p.color = ACCENT; c.drawCircle(70f, 18f, 4f + 14f * seg(0.7f, 0.95f), p) }
            }
        }
    }

    private fun drawPlus(c: Canvas) {
        p.strokeWidth = 4f; p.color = ACCENT
        c.drawCircle(50f, 50f, 38f, p)
        p.strokeWidth = 6f; c.drawLine(50f, 32f, 50f, 68f, p); c.drawLine(32f, 50f, 68f, 50f, p)
    }

    private fun ln(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Int) { p.strokeWidth = w; p.color = col; c.drawLine(x1, y1, x2, y2, p) }
    private fun bump(c: Canvas, x: Float, y: Float, r: Float, col: Int) { fill.color = col; c.drawCircle(x, y, r, fill) }

    /** Each pose drawn explicitly (palm facing the camera). Extended parts bright, folded parts dim bumps. */
    private fun hand(c: Canvas, k: Demo, dy: Float, dx: Float = 0f, alpha: Float = 1f, pinch: Float = 0f, pinchMode: Boolean = false) {
        c.save(); c.translate(dx, dy)
        val a = (255 * alpha).toInt()
        val on = Color.argb(a, 241, 245, 249); val off = Color.argb(a, 120, 136, 160); val palm = Color.argb(a, 203, 213, 225); val edge = Color.argb(a, 71, 85, 105)
        fill.color = palm
        c.drawRoundRect(RectF(33f, 56f, 75f, 94f), 13f, 13f, fill)
        val fw = 9f
        // finger bases (x at knuckle y=58): index, middle, ring, little
        val bx = floatArrayOf(41f, 50f, 59f, 68f)
        fun curl(i: Int) { bump(c, bx[i] + 1f, 57f, 5f, off) }
        fun thumbAcross() { ln(c, 38f, 76f, 62f, 72f, 9f, on) }
        when {
            pinchMode -> {
                curl(1); curl(2); curl(3)
                val t = pinch
                // index bends down to meet the thumb tip
                val ix = lerp(41f, 38f, t); val iy = lerp(22f, 44f, t)
                ln(c, 41f, 58f, ix, iy, fw, on)
                ln(c, 36f, 78f, lerp(18f, 37f, t), lerp(60f, 47f, t), fw, on)
                if (t > 0.9f) bump(c, 37f, 45f, 4f, ACCENT)
            }
            k == Demo.OPEN_PALM -> {
                val tx = floatArrayOf(36f, 47f, 58f, 69f); val ln_ = floatArrayOf(31f, 38f, 35f, 27f)
                for (i in 0..3) ln(c, bx[i], 58f, tx[i] + (i - 1.5f) * 3f, 58f - ln_[i], fw, on)
                ln(c, 36f, 80f, 14f, 60f, fw, on)
            }
            k == Demo.FIST -> {
                fill.color = palm; c.drawRoundRect(RectF(30f, 40f, 78f, 92f), 14f, 14f, fill)
                for (i in 0..3) bump(c, bx[i] + 3f, 44f, 6f, off)
                for (xx in floatArrayOf(47f, 56f, 65f)) ln(c, xx, 46f, xx, 62f, 2.5f, edge)
                ln(c, 36f, 72f, 70f, 68f, 11f, on)
            }
            k == Demo.THUMB_UP || k == Demo.THUMB_DOWN -> {
                // side-view fist: finger rolls as horizontal bands, short thick thumb standing up from the top
                fill.color = palm; c.drawRoundRect(RectF(30f, 46f, 78f, 94f), 14f, 14f, fill)
                for (yy in floatArrayOf(58f, 69f, 80f)) ln(c, 46f, yy, 74f, yy, 3f, edge)
                ln(c, 38f, 50f, 38f, 24f, 15f, on)
                ln(c, 38f, 34f, 38f, 30f, 3f, edge)
            }
            k == Demo.VICTORY -> {
                ln(c, 44f, 58f, 34f, 22f, fw, on); ln(c, 56f, 58f, 66f, 22f, fw, on)  // index + middle in a V
                curl(2); curl(3); thumbAcross()
            }
            k == Demo.POINT_UP || k == Demo.SWIPE_UP || k == Demo.SWIPE_DOWN || k == Demo.POINTER -> {
                ln(c, 41f, 58f, 41f, 18f, fw, on); curl(1); curl(2); curl(3); thumbAcross()
            }
            k == Demo.LOVE || k == Demo.ARM -> {
                ln(c, 41f, 58f, 35f, 22f, fw, on)      // index up
                ln(c, 68f, 58f, 72f, 30f, fw, on)      // little finger up
                curl(1); curl(2)                       // middle + ring folded
                ln(c, 36f, 80f, 12f, 62f, fw, on)      // thumb out to the side
            }
            else -> {}
        }
        c.restore()
    }

    companion object { const val ACCENT = 0xFF22D3EE.toInt() }
}
