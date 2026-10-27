package dev.aircontrol.app

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {
    private lateinit var store: Store
    private fun dp(v: Int) = Ui.dp(this, v)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)); setBackgroundColor(Ui.BG) }
        val sv = ScrollView(this).apply { addView(root); setBackgroundColor(Ui.BG); clipToPadding = false }
        setContentView(sv)
        ViewCompat.setOnApplyWindowInsetsListener(sv) { v, i -> val sb = i.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(0, sb.top, 0, sb.bottom); i }
        root.addView(Ui.text(this, "<  Settings", 22f, Ui.ACCENT).apply { setPadding(0, dp(8), 0, dp(4)); setOnClickListener { finish() } })
        root.addView(Ui.card(this@SettingsActivity).apply {
            addView(Ui.text(this@SettingsActivity, "Favourite call (one number)", 16f))
            val nm = android.widget.EditText(this@SettingsActivity).apply { hint = "Name"; setText(store.favName); setTextColor(Color.WHITE); setHintTextColor(0x88FFFFFF.toInt()) }
            val no = android.widget.EditText(this@SettingsActivity).apply { hint = "Phone number"; setText(store.favNumber); inputType = android.text.InputType.TYPE_CLASS_PHONE; setTextColor(Color.WHITE); setHintTextColor(0x88FFFFFF.toInt()) }
            addView(nm); addView(no)
            addView(Ui.btn(this@SettingsActivity, "Save number") { if (store.isEmergency(no.text.toString())) { android.widget.Toast.makeText(this@SettingsActivity, "Emergency numbers are not allowed here.", android.widget.Toast.LENGTH_LONG).show(); return@btn }; store.favName = nm.text.toString().trim(); store.favNumber = no.text.toString().trim();  })
            addView(Ui.text(this@SettingsActivity, "Set the gesture for 'Call my favourite contact' in Gesture actions (default: fist held). It places the call directly after the hold time. No contacts are read. A held gesture dials this number directly, with no extra confirmation, so pick a gesture you will not make by accident. Emergency numbers are blocked.", 12f, 0xFF94A3B8.toInt()))
        })
        root.addView(Ui.card(this@SettingsActivity).apply {
            addView(Ui.text(this@SettingsActivity, "Safety", 16f))
            addView(Switch(this@SettingsActivity).apply { text = "Activation pose required first"; setTextColor(Color.WHITE); isChecked = store.armRequired
                setOnCheckedChangeListener { _, on -> store.armRequired = on } })
            addView(Ui.text(this@SettingsActivity, "Show the activation pose (default: I-love-you hand) for the hold time, then gestures work for 10 seconds.", 12f, 0xFF94A3B8.toInt()))
            addView(Ui.text(this@SettingsActivity, "Hold time (ms)"))
            addView(SeekBar(this@SettingsActivity).apply { max = 20; progress = (store.holdMs - 300) / 100
                setOnSeekBarChangeListener(sb { store.holdMs = 300 + it * 100 }) })
            addView(Ui.text(this@SettingsActivity, "Cooldown (ms)"))
            addView(SeekBar(this@SettingsActivity).apply { max = 20; progress = (store.cooldownMs - 500) / 100
                setOnSeekBarChangeListener(sb { store.cooldownMs = 500 + it * 100 }) })
            addView(Ui.text(this@SettingsActivity, "Custom gesture match strictness (lower = stricter)"))
            addView(SeekBar(this@SettingsActivity).apply { max = 40; progress = (store.matchThreshold * 100).toInt() - 10
                setOnSeekBarChangeListener(sb { store.matchThreshold = (it + 10) / 100f }) })
        })
    }
    private fun sb(f: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) f(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}
