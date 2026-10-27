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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Size

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
        Live.reset()
        try { startCamera() } catch (e: Throwable) { Log.e("AirControl", "startCamera failed", e); Live.msg = "Camera start failed"; note("Camera could not start: ${e.javaClass.simpleName}"); stopSelf(); return START_NOT_STICKY }
        registerScreenOff()
        if (Build.VERSION.SDK_INT >= 29) registerThermal()
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
        analyzer = try { HandAnalyzer(this) } catch (e: Throwable) { Log.e("AirControl", "model load failed", e); note("Gesture model failed to load: ${e.javaClass.simpleName}"); stopSelf(); return }
        Log.i("AirControl", "model loaded, camera starting")
        lateinit var engine: GestureEngine
        engine = GestureEngine(store, onGesture@{ id ->
            val a = store.action(Mode.REELS, id)
            if (a != Act.NONE) { Live.lastAction = Builtin.pretty(id) + " > " + a.label; Live.lastActionAt = SystemClock.uptimeMillis() }
            if (a != Act.NONE && a != Act.CALL_FAVORITE && AirAccessibilityService.instance == null) { note("Accessibility is off, so this action was skipped."); return@onGesture }
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
            val p = try { fut.get() } catch (e: Throwable) { Log.e("AirControl", "camera provider failed", e); note("Camera provider failed: ${e.javaClass.simpleName}"); stopSelf(); return@addListener }
            provider = p
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                .build()
            analysis.setAnalyzer(exec) { img ->
                try {
                    val t = SystemClock.uptimeMillis()
                    // power saving: with no hand seen for 3 s (or a hot phone) look at about 4-5 frames a second instead of every frame
                    val idle = t - lastHandAt > 3000
                    Live.idle = idle
                    val gap = if (hot) 300L else if (idle) 220L else 0L
                    if (gap > 0 && t - lastProc < gap) return@setAnalyzer
                    lastProc = t
                    val r = analyzer?.analyze(img.toBitmap(), img.imageInfo.rotationDegrees, t)
                    frames++; Live.frames = frames; Live.lastFrameAt = t
                    if (frames % 60 == 1L) Log.i("AirControl", "frames=$frames hand=${r != null}")
                    if (r != null) lastHandAt = t
                    engine.onFrame(r, t)
                    Live.armLeftMs = engine.armedLeftMs(t)
                    if (pointerOn && r != null) pointerFrame(r, t, store)
                } catch (e: Exception) {
                    errors++
                    if (errors % 60L == 1L) Log.w("AirControl", "frame error #$errors", e)
                    Live.msg = "Frame problem: ${e.javaClass.simpleName}"
                } finally { img.close() }
            }
            analysisUse = analysis
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private val ui = Handler(Looper.getMainLooper())
    private var analysisUse: ImageAnalysis? = null
    private var retries = 0
    private var lastHandAt = SystemClock.uptimeMillis()
    private var lastProc = 0L
    private var errors = 0L
    @Volatile private var hot = false
    private var screenReceiver: android.content.BroadcastReceiver? = null
    private var thermalListener: Any? = null

    /** Bind the front camera. A camera error (another app took it, or Android closed it) is shown, then retried a few times, never forever. */
    private fun bindCamera() {
        val p = provider ?: return; val a = analysisUse ?: return
        try {
            p.unbindAll()
            val cam = p.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, a)
            running = true
            camLive?.removeObserver(camObs)
            camLive = cam.cameraInfo.cameraState
            camLive!!.observe(this, camObs)
        } catch (e: Throwable) {
            Log.e("AirControl", "bind failed", e)
            note("Camera could not start: ${e.javaClass.simpleName}. Open AirControl and tap Start to try again.")
            stopSelf()
        }
    }

    private var camLive: androidx.lifecycle.LiveData<androidx.camera.core.CameraState>? = null
    private val camObs = androidx.lifecycle.Observer<androidx.camera.core.CameraState> { st ->
        run {
                if (st.error != null) {
                    Live.msg = "Camera problem (code ${st.error?.code})"
                    if (retries < 3) {
                        val delay = longArrayOf(1000, 2000, 5000)[retries]; retries++
                        Log.w("AirControl", "camera error ${st.error?.code}, retry $retries")
                        ui.postDelayed({ if (running) bindCamera() }, delay)
                    } else { note("Camera was lost (another app may be using it). Open AirControl and tap Start to try again."); stopSelf() }
                } else if (st.type == androidx.camera.core.CameraState.Type.OPEN) { retries = 0; if (Live.msg.startsWith("Camera problem")) Live.msg = "" }
        }
    }

    /** Screen off: stop the camera. Android does not allow starting a camera service from the background, so the user restarts it from the app. */
    private fun registerScreenOff() {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                note("Screen turned off, so the camera stopped. Open AirControl and tap Start to use gestures again.")
                stopSelf()
            }
        }
        screenReceiver = r
        val f = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(r, f)
    }

    @RequiresApi(29)
    private fun registerThermal() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val l = PowerManager.OnThermalStatusChangedListener { status ->
            hot = status >= PowerManager.THERMAL_STATUS_MODERATE; Live.hot = hot
            if (status >= PowerManager.THERMAL_STATUS_SEVERE) { note("Phone is too hot, so the camera stopped. Let it cool, then tap Start."); stopSelf() }
        }
        thermalListener = l
        pm.addThermalStatusListener(ContextCompat.getMainExecutor(this), l)
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
        ui.removeCallbacksAndMessages(null)
        try { screenReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 29) { (thermalListener as? PowerManager.OnThermalStatusChangedListener)?.let { (getSystemService(Context.POWER_SERVICE) as PowerManager).removeThermalStatusListener(it) } }
        provider?.unbindAll(); exec.shutdown()
        try { exec.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) {}
        analyzer?.close()
        Live.hand = false; Live.pose = "None"
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "dev.aircontrol.STOP"
        @Volatile var running = false
    }
}
