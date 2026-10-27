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

        root.addView(text("AirControl", 30f))
        root.addView(text("Control your phone with hand gestures", 14f, 0xFF94A3B8.toInt()))
        chip = text("Camera: OFF", 14f, 0xFFFCA5A5.toInt()).apply { setPadding(0, dp(8), 0, 0) }
        root.addView(chip)
        start = btn("Start") { toggle() }
        root.addView(start)
        a11y = text("", 13f, 0xFFFDE68A.toInt())
        root.addView(card().apply {
            addView(text("Permissions", 16f))
            addView(a11y)
            addView(btn("Allow camera") { ActivityCompat.requestPermissions(this@MainActivity, perms(), 1) })
            addView(btn("Turn on AirControl in Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            addView(text("The accessibility service sends swipes/taps only. It does not read your screen.", 12f, 0xFF94A3B8.toInt()))
        })

        root.addView(card().apply {
            addView(text("Gesture actions", 16f))
            val sw = Switch(this@MainActivity).apply { text = "Camera mode (photo / video) instead of Reels mode"; setTextColor(Color.WHITE)
                setOnCheckedChangeListener { _, on -> mode = if (on) Mode.CAMERA else Mode.REELS; renderMap() } }
            addView(sw)
            mapBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(mapBox)
        })
        root.addView(card().apply {
            addView(text("Favourite call (one number)", 16f))
            val nm = android.widget.EditText(this@MainActivity).apply { hint = "Name"; setText(store.favName); setTextColor(Color.WHITE); setHintTextColor(0x88FFFFFF.toInt()) }
            val no = android.widget.EditText(this@MainActivity).apply { hint = "Phone number"; setText(store.favNumber); inputType = android.text.InputType.TYPE_CLASS_PHONE; setTextColor(Color.WHITE); setHintTextColor(0x88FFFFFF.toInt()) }
            addView(nm); addView(no)
            addView(btn("Save number") { if (store.isEmergency(no.text.toString())) { android.widget.Toast.makeText(this@MainActivity, "Emergency numbers are not allowed here.", android.widget.Toast.LENGTH_LONG).show(); return@btn }; store.favName = nm.text.toString().trim(); store.favNumber = no.text.toString().trim(); refresh() })
            addView(text("Set the gesture for 'Call my favourite contact' in Gesture actions (default: fist held). It places the call directly after the hold time. No contacts are read. A held gesture dials this number directly, with no extra confirmation, so pick a gesture you will not make by accident. Emergency numbers are blocked.", 12f, 0xFF94A3B8.toInt()))
        })
        root.addView(btn("Open Camera mode (photo / video)") { startActivity(Intent(this, CaptureActivity::class.java)) })
        root.addView(btn("Add my gesture") { startActivity(Intent(this, EnrollActivity::class.java)) })

        root.addView(card().apply {
            addView(text("Safety", 16f))
            addView(Switch(this@MainActivity).apply { text = "Activation pose required first"; setTextColor(Color.WHITE); isChecked = store.armRequired
                setOnCheckedChangeListener { _, on -> store.armRequired = on } })
            addView(text("Show the activation pose (default: I-love-you hand) for the hold time, then gestures work for 10 seconds.", 12f, 0xFF94A3B8.toInt()))
            addView(text("Hold time (ms)"))
            addView(SeekBar(this@MainActivity).apply { max = 20; progress = (store.holdMs - 300) / 100
                setOnSeekBarChangeListener(sb { store.holdMs = 300 + it * 100 }) })
            addView(text("Cooldown (ms)"))
            addView(SeekBar(this@MainActivity).apply { max = 20; progress = (store.cooldownMs - 500) / 100
                setOnSeekBarChangeListener(sb { store.cooldownMs = 500 + it * 100 }) })
            addView(text("Custom gesture match strictness (lower = stricter)"))
            addView(SeekBar(this@MainActivity).apply { max = 40; progress = (store.matchThreshold * 100).toInt() - 10
                setOnSeekBarChangeListener(sb { store.matchThreshold = (it + 10) / 100f }) })
        })
        root.addView(text("Not tested on a real phone yet. Gesture accuracy, battery and camera sharing with other apps depend on your device.", 12f, 0xFF94A3B8.toInt()).apply { setPadding(0, dp(12), 0, 0) })
        renderMap()
        val dbg = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (dbg && intent.getBooleanExtra("debug_start", false)) window.decorView.postDelayed({ if (!GestureService.running) toggle() }, 800)
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
