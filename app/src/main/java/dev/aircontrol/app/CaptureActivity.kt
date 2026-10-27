package dev.aircontrol.app

import android.content.ContentValues
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import java.util.concurrent.Executors

/** Camera mode: this app owns the camera, so gesture tracking and capture run together. */
class CaptureActivity : AppCompatActivity() {
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var preview: PreviewView
    private var analyzer: HandAnalyzer? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var front = true
    private lateinit var store: Store
    private lateinit var engine: GestureEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // this screen needs the camera; the background tracker would be evicted anyway, so stop it cleanly
        if (GestureService.running) startService(android.content.Intent(this, GestureService::class.java).setAction(GestureService.ACTION_STOP))
        store = Store(this)
        val root = FrameLayout(this)
        preview = PreviewView(this)
        root.addView(preview)
        status = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; setBackgroundColor(0xAA0A0F1E.toInt()); setPadding(32, 24, 32, 24)
            text = "Camera mode. Show your arm pose, then a gesture."
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)
        engine = GestureEngine(store, { id -> runOnUiThread { handle(store.action(Mode.CAMERA, id)) } }, { s -> runOnUiThread { status.text = s } })
        if (PermissionChecker.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PermissionChecker.PERMISSION_GRANTED) {
            status.text = "Camera permission missing. Grant it from the main screen."; return
        }
        analyzer = HandAnalyzer(this)
        bind()
    }

    private fun bind() {
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            val p = fut.get()
            val prev = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            analysis.setAnalyzer(exec) { img ->
                try {
                    val r = analyzer?.analyze(img.toBitmap(), img.imageInfo.rotationDegrees, SystemClock.uptimeMillis())
                    engine.onFrame(r, SystemClock.uptimeMillis())
                } catch (_: Exception) {} finally { img.close() }
            }
            val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val ic = ImageCapture.Builder().build()
            val vc = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build())
            p.unbindAll()
            try {
                p.bindToLifecycle(this, sel, prev, analysis, ic, vc)
                imageCapture = ic; videoCapture = vc
            } catch (e: Exception) {
                try {
                    p.unbindAll(); p.bindToLifecycle(this, sel, prev, analysis, ic)
                    imageCapture = ic; videoCapture = null
                    status.text = "This phone can't run photo + video + tracking together. Video gesture disabled."
                } catch (e2: Exception) { status.text = "Camera could not start: ${e2.message}" }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handle(a: Act) {
        when (a) {
            Act.PHOTO -> photo()
            Act.VIDEO_TOGGLE -> video()
            Act.SWITCH_CAMERA -> { front = !front; bind() }
            else -> {}
        }
    }

    private fun photo() {
        val ic = imageCapture ?: return
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "air_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AirControl")
        }
        val o = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv).build()
        ic.takePicture(o, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) { status.text = "Photo saved to Pictures/AirControl" }
            override fun onError(e: ImageCaptureException) { status.text = "Photo failed: ${e.message}" }
        })
    }

    private fun video() {
        val vc = videoCapture ?: run { Toast.makeText(this, "Video not available on this phone in this mode", Toast.LENGTH_SHORT).show(); return }
        val cur = recording
        if (cur != null) { cur.stop(); recording = null; return }
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "air_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/AirControl")
        }
        val o = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(cv).build()
        var pending = vc.output.prepareRecording(this, o)
        if (PermissionChecker.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { ev ->
            if (ev is VideoRecordEvent.Start) status.text = "Recording... show the same gesture to stop"
            if (ev is VideoRecordEvent.Finalize) status.text = if (ev.hasError()) "Video error ${ev.error}" else "Video saved to Movies/AirControl"
        }
    }

    override fun onDestroy() {
        recording?.stop(); analyzer?.close(); exec.shutdown(); super.onDestroy()
    }
}
