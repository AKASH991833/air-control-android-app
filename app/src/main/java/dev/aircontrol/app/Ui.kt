package dev.aircontrol.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    const val BG = 0xFF0A0F1E.toInt(); const val CARD = 0xFF121A30.toInt(); const val ACCENT = 0xFF22D3EE.toInt()
    const val MUTED = 0xFFB6C2D4.toInt()
    fun narrow(c: Context) = c.resources.configuration.screenWidthDp < 360
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun text(c: Context, s: String, size: Float = 15f, col: Int = Color.WHITE) = TextView(c).apply { text = s; textSize = size; setTextColor(col) }
    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(c, 18).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 12) }
    }
    fun btn(c: Context, s: String, f: () -> Unit) = Button(c).apply {
        text = s; setTextColor(Color.BLACK); isAllCaps = false
        background = GradientDrawable().apply { setColor(ACCENT); cornerRadius = dp(c, 24).toFloat() }
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 8) }
        minHeight = dp(c, 48); minimumHeight = dp(c, 48); setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
    }
    fun header(c: Context, s: String) = text(c, s, 13f, ACCENT).apply { setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 2)); letterSpacing = 0.08f }
    /** Square action tile with an icon on a round accent badge, title and one-line hint. */
    fun tile(c: Context, icon: Int, title: String, hint: String, f: () -> Unit): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.START; setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14))
        background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(c, 18).toFloat() }
        val badge = android.widget.ImageView(c).apply { setImageResource(icon); setColorFilter(Color.BLACK); setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT) }
            layoutParams = LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)) }
        addView(badge); addView(text(c, title, 16f).apply { setPadding(0, dp(c, 10), 0, 0) }); addView(text(c, hint, 12f, MUTED))
        setOnClickListener { f() }
        layoutParams = (if (narrow(c)) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f)).apply { setMargins(dp(c, 5), dp(c, 5), dp(c, 5), dp(c, 5)) }
    }
}
