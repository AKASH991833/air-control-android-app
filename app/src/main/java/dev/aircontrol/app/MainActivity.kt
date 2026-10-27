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
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) }
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
        root.addView(card().apply {
            addView(chip.apply { textSize = 16f })
            addView(text("Start runs a visible background camera service with a Stop button in the notification.", 12f, Ui.MUTED))
            addView(start)
        })
        root.addView(Ui.header(this, "QUICK ACTIONS"))
        root.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL
            addView(Ui.tile(this@MainActivity, android.R.drawable.ic_menu_camera, "Camera mode", "Photo and video by gesture") { startActivity(Intent(this@MainActivity, CaptureActivity::class.java)) })
            addView(Ui.tile(this@MainActivity, android.R.drawable.ic_menu_add, "Add my gesture", "Teach a custom pose") { startActivity(Intent(this@MainActivity, EnrollActivity::class.java)) }) })
        root.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL
            addView(Ui.tile(this@MainActivity, android.R.drawable.ic_menu_call, "Favourite call", "One saved number") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) })
            addView(Ui.tile(this@MainActivity, android.R.drawable.ic_menu_preferences, "Settings", "Activation pose, timing") { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }) })
        root.addView(Ui.header(this, "PERMISSIONS"))
        root.addView(card().apply {
            addView(a11y)
            addView(btn("Allow camera") { ActivityCompat.requestPermissions(this@MainActivity, perms(), 1) })
            addView(btn("Turn on AirControl in Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            addView(text("The accessibility service sends swipes and taps only. It does not read your screen.", 12f, Ui.MUTED))
        })
        root.addView(Ui.header(this, "GESTURE ACTIONS"))
        root.addView(card().apply {
            val sw = Switch(this@MainActivity).apply { text = "Camera mode actions instead of Reels mode"; setTextColor(Color.WHITE)
                setOnCheckedChangeListener { _, on -> mode = if (on) Mode.CAMERA else Mode.REELS; renderMap() } }
            addView(sw)
            mapBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(mapBox)
        })
        root.addView(text("Not tested on a real phone yet. Gesture accuracy, battery, background calls and camera sharing with other apps depend on your device.", 12f, Ui.MUTED).apply { setPadding(0, dp(14), 0, 0) })
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
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
            row.addView(text(Builtin.pretty(id), 14f))
            val sp = Spinner(this)
            sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, acts.map { it.label })
            sp.setSelection(acts.indexOf(store.action(mode, id)).coerceAtLeast(0))
            var first = true
            sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, i: Long) { if (first) { first = false; return }; store.setAction(mode, id, acts[pos]) }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            row.addView(sp); mapBox.addView(row)
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

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val on = GestureService.running
        chip.text = if (on) "Camera: ON (tracking in background)" else "Camera: OFF"
        chip.setTextColor(if (on) 0xFF86EFAC.toInt() else 0xFFFCA5A5.toInt())
        start.text = if (on) "Stop" else "Start"
        a11y.text = if (AirAccessibilityService.instance != null) "Accessibility: ON" else "Accessibility: OFF (needed for scroll / tap / back / home)"
    }
}
