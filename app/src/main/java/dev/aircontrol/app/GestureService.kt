package dev.aircontrol.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.Executors
import android.util.Log

/** Background (cross-app) gesture tracking. Must be started from a visible activity (Android 14+ camera FGS rule). */
class GestureService : LifecycleService() {
    private val exec = Executors.newSingleThreadExecutor()
    private var analyzer: HandAnalyzer? = null
    private var provider: ProcessCameraProvider? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i("AirControl", "service onStartCommand action=${intent?.action}")
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        try { startForegroundNow() } catch (e: Throwable) { Log.e("AirControl", "startForeground failed", e); stopSelf(); return START_NOT_STICKY }
        try { startCamera() } catch (e: Throwable) { Log.e("AirControl", "startCamera failed", e) }
        running = true
        return START_NOT_STICKY
    }

    private fun startForegroundNow() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("air", "AirControl camera", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, GestureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, "air")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("AirControl is watching for gestures")
            .setContentText("Front camera is ON. Tap Stop to turn it off.")
            .setOngoing(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA) else startForeground(11, n)
    }

    private fun startCamera() {
        val store = Store(this)
        analyzer = try { HandAnalyzer(this) } catch (e: Throwable) { note("Gesture model failed to load: ${e.javaClass.simpleName}"); stopSelf(); return }
        Log.i("AirControl", "model loaded, camera starting")
        lateinit var engine: GestureEngine
        engine = GestureEngine(store, { id ->
            val a = store.action(Mode.REELS, id)
            when (a) {
                Act.NONE -> {}
                Act.POINTER_TOGGLE -> {
                    pointerOn = !pointerOn
                    engine.suspendSwipes = pointerOn
                    AirAccessibilityService.instance?.showPointer(pointerOn)
                }
                Act.CALL_FAVORITE -> callFavorite(store)
                else -> AirAccessibilityService.instance?.perform(a)
            }
        })
        val fut = ProcessCameraProvider.getInstance(this)
        fut.addListener({
            val p = fut.get(); provider = p
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(exec) { img ->
                try {
                    val r = analyzer?.analyze(img.toBitmap(), img.imageInfo.rotationDegrees, SystemClock.uptimeMillis())
                    frames++; if (frames % 60 == 1L) Log.i("AirControl", "frames=$frames hand=${r != null}")
                    engine.onFrame(r, SystemClock.uptimeMillis())
                    if (pointerOn && r != null) pointerFrame(r, SystemClock.uptimeMillis(), store)
                } catch (_: Exception) {} finally { img.close() }
            }
            p.unbindAll()
            p.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private var frames = 0L
    private var lastCall = 0L
    private var pointerOn = false
    private var sx = 0.5f; private var sy = 0.5f
    private var pinchSince = 0L; private var pinchFired = false; private var lastTap = 0L

    /** Index fingertip moves the cursor (x mirrored for front camera, smoothed); pinch (thumb+index) held 300 ms = tap. */
    private fun pointerFrame(r: HandResult, now: Long, store: Store) {
        val svc = AirAccessibilityService.instance ?: return
        val tx = 1f - r.pts[8 * 3]; val ty = r.pts[8 * 3 + 1]
        sx += (tx - sx) * 0.35f; sy += (ty - sy) * 0.35f
        // use the central part of the camera frame so edges of the screen are reachable
        val mx = ((sx - 0.15f) / 0.7f); val my = ((sy - 0.15f) / 0.7f)
        svc.movePointer(mx, my)
        val hand = HandAnalyzer.distance(floatArrayOf(r.pts[0], r.pts[1], r.pts[2]) + FloatArray(60), floatArrayOf(r.pts[27], r.pts[28], r.pts[29]) + FloatArray(60)) * 21f
        val dx = r.pts[12] - r.pts[24]; val dy = r.pts[13] - r.pts[25]
        val pinch = kotlin.math.sqrt(dx * dx + dy * dy) < 0.25f * hand.coerceAtLeast(0.01f)
        if (pinch) {
            if (pinchSince == 0L) pinchSince = now
            if (!pinchFired && now - pinchSince > 300 && now - lastTap > store.cooldownMs.coerceAtMost(800)) {
                pinchFired = true; lastTap = now; svc.tapPointer()
            }
        } else { pinchSince = 0L; pinchFired = false }
    }

    /** Direct call to the one number the user typed in. Needs CALL_PHONE. Blocked paths are reported, not faked. */
    private fun callFavorite(store: Store) {
        val num = store.favNumber
        if (store.isEmergency(num)) { note("Emergency numbers are not allowed for gesture calling."); return }
        if (num.isBlank()) { note("No favourite number set in AirControl."); return }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) { note("Call permission not granted."); return }
        val nowT = SystemClock.uptimeMillis()
        if (nowT - lastCall < 20000) return // one request per 20 s, so a held pose can never redial
        lastCall = nowT
        try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            if (Build.VERSION.SDK_INT >= 34 && !tm.isOutgoingCallPermitted(null)) { note("Android says outgoing calls are not permitted right now."); return }
            tm.placeCall(android.net.Uri.fromParts("tel", num, null), android.os.Bundle())
            // placeCall returns nothing, so this is "requested", not "connected"
            note("Call to ${store.favName.ifBlank { "favourite" }} requested. If nothing dials, Android blocked it from the background.")
        } catch (e: Throwable) { note("Android blocked the call: ${e.javaClass.simpleName}") }
    }

    private fun note(s: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(12, NotificationCompat.Builder(this, "air").setSmallIcon(android.R.drawable.ic_menu_call).setContentTitle("AirControl").setContentText(s).setAutoCancel(true).build())
    }

    override fun onDestroy() {
        AirAccessibilityService.instance?.showPointer(false)
        running = false
        provider?.unbindAll(); analyzer?.close(); exec.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "dev.aircontrol.STOP"
        @Volatile var running = false
    }
}
