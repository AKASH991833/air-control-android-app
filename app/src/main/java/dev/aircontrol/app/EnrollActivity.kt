package dev.aircontrol.app

import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/** "Add my gesture": records hand-landmark templates (numbers only, no photos/video saved) for a static pose. */
class EnrollActivity : AppCompatActivity() {
    private val exec = Executors.newSingleThreadExecutor()
    private var analyzer: HandAnalyzer? = null
    @Volatile private var last: HandResult? = null
    private val samples = ArrayList<FloatArray>()
    private lateinit var info: TextView
    private lateinit var name: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // this screen needs the camera; the background tracker would be evicted anyway, so stop it cleanly
        if (GestureService.running) startService(android.content.Intent(this, GestureService::class.java).setAction(GestureService.ACTION_STOP))
        val store = Store(this)
        val root = FrameLayout(this)
        val preview = PreviewView(this)
        root.addView(preview)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xDD0A0F1E.toInt()); setPadding(32, 24, 32, 24) }
        info = TextView(this).apply { setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; text = "Name your gesture, hold the pose in view, tap Record sample. Do 6+ with small position/angle changes." }
        name = EditText(this).apply { hint = "Gesture name"; setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0x88FFFFFF.toInt()); inputType = InputType.TYPE_CLASS_TEXT }
        val rec = Button(this).apply { text = "Record sample" }
        val save = Button(this).apply { text = "Save gesture (needs 6 samples)" }
        panel.addView(info); panel.addView(name); panel.addView(rec); panel.addView(save)
        root.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        rec.setOnClickListener {
            val r = last
            if (r == null) info.text = "No hand seen. Show your hand to the front camera."
            else { samples.add(HandAnalyzer.template(r.pts)); info.text = "Samples: ${samples.size}. Built-in guess right now: ${r.canned}" }
        }
        save.setOnClickListener {
            val n = name.text.toString().trim().replace(Regex("[^A-Za-z0-9 _-]"), "")
            when {
                n.isEmpty() -> info.text = "Enter a name first."
                samples.size < 6 -> info.text = "Need at least 6 samples (have ${samples.size})."
                else -> { store.saveCustom(n, samples.toList()); info.text = "Saved \"$n\". Restart tracking, then set its action on the main screen."; samples.clear() }
            }
        }
        analyzer = HandAnalyzer(this)
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            val p = fut.get()
            val prev = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            analysis.setAnalyzer(exec) { img ->
                try { last = analyzer?.analyze(img.toBitmap(), img.imageInfo.rotationDegrees, SystemClock.uptimeMillis()) }
                catch (_: Exception) {} finally { img.close() }
            }
            p.unbindAll(); p.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, prev, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() { analyzer?.close(); exec.shutdown(); super.onDestroy() }
}
