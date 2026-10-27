package dev.aircontrol.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import kotlin.math.sqrt

class HandResult(val canned: String, val cannedScore: Float, val pts: FloatArray /* 21*3 normalized image coords */)

/** Wraps MediaPipe GestureRecognizer. All processing is on-device; frames are never stored. */
class HandAnalyzer(ctx: Context) {
    private val recognizer: GestureRecognizer = GestureRecognizer.createFromOptions(
        ctx,
        GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("gesture_recognizer.task").build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .build()
    )

    fun analyze(src: Bitmap, rotationDeg: Int, tsMs: Long): HandResult? {
        val bmp = if (rotationDeg != 0) {
            val m = Matrix(); m.postRotate(rotationDeg.toFloat())
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        } else src
        val res = recognizer.recognizeForVideo(BitmapImageBuilder(bmp).build(), tsMs)
        if (res.landmarks().isEmpty()) return null
        val lm = res.landmarks()[0]
        val pts = FloatArray(63)
        for (i in 0 until 21) { pts[i * 3] = lm[i].x(); pts[i * 3 + 1] = lm[i].y(); pts[i * 3 + 2] = lm[i].z() }
        var name = "None"; var score = 0f
        if (res.gestures().isNotEmpty() && res.gestures()[0].isNotEmpty()) {
            val c = res.gestures()[0][0]; name = c.categoryName(); score = c.score()
        }
        return HandResult(name, score, pts)
    }

    fun close() = recognizer.close()

    companion object {
        /** Wrist-relative, scale-normalized landmark vector (translation and size invariant; not rotation invariant). */
        fun template(pts: FloatArray): FloatArray {
            val ox = pts[0]; val oy = pts[1]; val oz = pts[2]
            val dx = pts[27] - ox; val dy = pts[28] - oy; val dz = pts[29] - oz
            val s = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
            return FloatArray(63) { i ->
                val o = when (i % 3) { 0 -> ox; 1 -> oy; else -> oz }
                (pts[i] - o) / s
            }
        }
        fun distance(a: FloatArray, b: FloatArray): Float {
            var sum = 0f
            for (i in 0 until 21) {
                val x = a[i * 3] - b[i * 3]; val y = a[i * 3 + 1] - b[i * 3 + 1]; val z = a[i * 3 + 2] - b[i * 3 + 2]
                sum += sqrt(x * x + y * y + z * z)
            }
            return sum / 21f
        }
        /** Index finger up, other three fingers curled (y grows downward). */
        fun indexOnly(p: FloatArray): Boolean {
            fun y(i: Int) = p[i * 3 + 1]
            return y(8) < y(6) && y(12) > y(10) && y(16) > y(14) && y(20) > y(18)
        }
    }
}
