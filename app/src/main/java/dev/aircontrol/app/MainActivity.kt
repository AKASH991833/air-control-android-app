package dev.aircontrol.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var store: Store
    private lateinit var chip: TextView
    private lateinit var start: Button
    private lateinit var a11y: TextView
    private lateinit var live: TextView
    private val poll = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { renderLive(); poll.postDelayed(this, 400) } }
    private lateinit var mapBox: LinearLayout
    private var mode = Mode.REELS

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply { setColor(0xFF121A30.toInt()); cornerRadius = dp(16).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }
    private fun text(s: String, size: Float = 15f, c: Int = Color.WHITE) = TextView(this).apply { text = s; textSize = size; setTextColor(c) }
    private fun btn(s: String, f: () -> Unit) = Button(this).apply {
        text = s; setTextColor(Color.BLACK); isAllCaps = false
        background = GradientDrawable().apply { setColor(0xFF22D3EE.toInt()); cornerRadius = dp(24).toFloat() }
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        minHeight = dp(48); minimumHeight = dp(48); setPadding(dp(14), dp(8), dp(14), dp(8))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(24)); setBackgroundColor(0xFF0A0F1E.toInt()) }
        val sv = ScrollView(this).apply { addView(root); setBackgroundColor(0xFF0A0F1E.toInt()) }
        setContentView(sv)

        ViewCompat.setOnApplyWindowInsetsListener(sv) { v, ins -> val sb = ins.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(0, sb.top, 0, sb.bottom); ins }
        root.addView(text("AirControl", 30f))
        root.addView(text("Control your phone with hand gestures", 14f, Ui.MUTED))
        chip = text("Camera: OFF", 14f, 0xFFFCA5A5.toInt())
        start = btn("Start") { toggle() }
        a11y = text("", 13f, 0xFFFDE68A.toInt())
        live = text("", 14f, Ui.MUTED)
        root.addView(card().apply {
            addView(chip.apply { textSize = 16f })
            addView(text("Start runs a visible background camera service with a Stop button in the notification.", 12f, Ui.MUTED))
            addView(start)
            addView(text("LIVE PRACTICE", 11f, Ui.ACCENT).apply { setPadding(0, dp(12), 0, dp(2)) })
            addView(live)
        })
        root.addView(Ui.header(this, "QUICK ACTIONS"))
        root.addView(LinearLayout(this).apply { orientation = if (Ui.narrow(this@MainActivity)) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            addView(Ui.tile(this@MainActivity, android.R.drawable.ic_menu_camera, "Camera mode", "Photo and video by gesture") { startActivity(Intent(this@MainActivity, CaptureActivity::class.java)) })
            addView(Ui.tile(this@MainActivity, android.R.drawable.ic_menu_add, "Add my gesture", "Teach a custom pose") { startActivity(Intent(this@MainActivity, EnrollActivity::class.java)) }) })
        root.addView(LinearLayout(this).apply { orientation = if (Ui.narrow(this@MainActivity)) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            addView(Ui.tile(this@MainActivity, android.R.drawable.ic_menu_call, "Favourite call", "One saved number") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) })
            addView(Ui.tile(this@MainActivity, android.R.drawable.ic_menu_preferences, "Settings", "Activation pose, timing") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }) })
        root.addView(Ui.header(this, "PERMISSIONS"))
        root.addView(card().apply {
            addView(a11y)
            addView(btn("Allow camera") { ActivityCompat.requestPermissions(this@MainActivity, perms(), 1) })
            addView(btn("Turn on AirControl in Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            addView(text("The accessibility service sends swipes and taps only. It does not read your screen.", 12f, Ui.MUTED))
        })
        root.addView(Ui.header(this, "HOW IT WORKS"))
        root.addView(card().apply {
            fun step(d: HandIconView.Demo, title: String, body: String) {
                val nar = resources.configuration.screenWidthDp < 330
                val l = LinearLayout(this@MainActivity).apply { orientation = if (nar) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; gravity = if (nar) android.view.Gravity.CENTER_HORIZONTAL else android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
                l.addView(HandIconView(this@MainActivity).apply { demo = d; layoutParams = LinearLayout.LayoutParams(dp(if (nar) 96 else 84), dp(if (nar) 96 else 84)) })
                val t = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = if (nar) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) } }
                t.addView(text(title, 16f)); t.addView(text(body, 12f, Ui.MUTED)); l.addView(t); addView(l)
            }
            step(HandIconView.Demo.ARM, "1. Wake it up", "Show the I-love-you hand (thumb, index and little finger out) to the front camera and hold it for ${"%.1f".format(store.holdMs / 1000.0)} seconds. Gestures then work for 10 seconds.")
            step(HandIconView.Demo.POINTER, "2. Pointer (peace sign)", "Peace sign turns the pointer on or off. Move your index finger to move it. Touch thumb and index together and hold about 0.3 seconds to tap.")
        })
        root.addView(Ui.header(this, "GESTURES  (tap one to choose its action)"))
        root.addView(card().apply {
            val sw = Switch(this@MainActivity).apply { text = "Camera mode actions instead of Reels mode"; setTextColor(Color.WHITE)
                setOnCheckedChangeListener { _, on -> mode = if (on) Mode.CAMERA else Mode.REELS; renderMap() } }
            addView(sw)
            mapBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(mapBox)
        })
        root.addView(text("Not tested on a real phone yet: gesture accuracy, battery, background calls and camera sharing with other apps are unverified. AirControl cannot see your screen, so it cannot tell what a tap or pinch will press (for example a pay or send button). Use the pointer and favourite call with care.", 12f, Ui.MUTED).apply { setPadding(0, dp(14), 0, 0) })
        renderMap()
        DebugHooks.install(this) { toggle() }
    }

    private fun sb(f: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) f(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    private fun renderMap() {
        mapBox.removeAllViews()
        val acts = Act.values().filter { it.mode == mode }
        for (id in store.allGestureIds()) {
            val cur = TextView(this).apply { textSize = 14f; setTextColor(Ui.ACCENT) }
            fun show() { cur.text = store.action(mode, id).label + "   >" }
            show()
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { setColor(0xFF0F172A.toInt()); cornerRadius = dp(12).toFloat() }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
                setOnClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivity).setTitle(Builtin.pretty(id))
                        .setItems(acts.map { it.label }.toTypedArray()) { _, pos -> store.setAction(mode, id, acts[pos]); show() }.show()
                } }
            val g = GestureGuide.info(id)
            val demo = HandIconView(this).apply { demo = g.demo; layoutParams = LinearLayout.LayoutParams(dp(84), dp(84)) }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) } }
            info.addView(text(g.title, 16f)); if (g.how.isNotEmpty()) info.addView(text(g.how, 12f, Ui.MUTED)); info.addView(cur)
            val nar = resources.configuration.screenWidthDp < 330
            val line = LinearLayout(this).apply { orientation = if (nar) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; gravity = if (nar) android.view.Gravity.CENTER_HORIZONTAL else android.view.Gravity.CENTER_VERTICAL }
            if (nar) { info.layoutParams = LinearLayout.LayoutParams(-1, -2); demo.layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)) }
            line.addView(demo); line.addView(info); row.addView(line)
            mapBox.addView(row)
        }
    }

    private fun perms(): Array<String> {
        val l = arrayListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l.toTypedArray()
    }

    private fun toggle() {
        android.util.Log.i("AirControl", "toggle running=${GestureService.running}")
        if (GestureService.running) { startService(Intent(this, GestureService::class.java).setAction(GestureService.ACTION_STOP)) }
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, perms(), 1); return }
            ContextCompat.startForegroundService(this, Intent(this, GestureService::class.java))
        }
        refresh()
        window.decorView.postDelayed({ refresh() }, 1500)
    }

    override fun onResume() { super.onResume(); refresh(); poll.post(tick) }
    override fun onPause() { poll.removeCallbacks(tick); super.onPause() }

    /** What the tracking service sees right now. Practice uses the same camera as Start, so there is no second camera session. */
    private fun renderLive() {
        if (!GestureService.running) { live.text = "Tap Start, then show a hand to the front camera. This box shows what AirControl sees (the pose it recognises and whether it is armed)."; return }
        val now = android.os.SystemClock.uptimeMillis()
        val sb = StringBuilder()
        if (Live.lastFrameAt == 0L || now - Live.lastFrameAt > 2500) sb.append(if (Live.idle) "Camera is checking slowly to save battery. Show a hand." else "Waiting for camera frames...")
        else if (!Live.hand) sb.append("No hand seen. Hold your hand in front of the front camera, in good light.")
        else sb.append("I see: ").append(if (Live.pose == "None") "a hand (no known pose yet)" else Builtin.pretty(Live.pose))
        if (store.armRequired) {
            val left = Live.armLeftMs
            sb.append("\n").append(if (left > 0) "Armed: ${(left + 999) / 1000} s left" else "Not armed. Show ${Builtin.pretty(store.armPose)} for ${"%.1f".format(store.holdMs / 1000.0)} s to arm.")
        }
        if (Live.lastAction.isNotEmpty()) sb.append("\nLast action: ").append(Live.lastAction).append(" (").append((now - Live.lastActionAt) / 1000).append(" s ago)")
        if (Live.hot) sb.append("\nPhone is warm: slowed down to cool it.")
        if (Live.msg.isNotEmpty()) sb.append("\n").append(Live.msg)
        live.text = sb.toString()
    }

    private fun refresh() {
        val on = GestureService.running
        chip.text = if (on) "Camera: ON (tracking in background)" else "Camera: OFF"
        chip.setTextColor(if (on) 0xFF86EFAC.toInt() else 0xFFFCA5A5.toInt())
        start.text = if (on) "Stop" else "Start"
        a11y.text = if (AirAccessibilityService.instance != null) "Accessibility: ON" else "Accessibility: OFF (needed for scroll / tap / back / home)"
    }
}
