package dev.aircontrol.app

import android.os.Bundle
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/** Debug-only: every demo at fixed frames, large, so poses and motion can be inspected. */
class GalleryActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val frames = intent.getFloatArrayExtra("frames") ?: floatArrayOf(0.55f)
        val grid = GridLayout(this).apply { columnCount = 3; setBackgroundColor(0xFF0A0F1E.toInt()); setPadding(8, 8, 8, 8) }
        val sets = if (intent.getStringExtra("set") == "motion")
            listOf(HandIconView.Demo.SWIPE_UP to listOf(0.1f, 0.25f, 0.45f), HandIconView.Demo.SWIPE_DOWN to listOf(0.1f, 0.25f, 0.45f), HandIconView.Demo.POINTER to listOf(0.1f, 0.45f, 0.8f), HandIconView.Demo.POINTER to listOf(0.62f, 0.7f, 0.78f))
        else listOf(HandIconView.Demo.OPEN_PALM, HandIconView.Demo.FIST, HandIconView.Demo.VICTORY, HandIconView.Demo.THUMB_UP, HandIconView.Demo.THUMB_DOWN, HandIconView.Demo.POINT_UP, HandIconView.Demo.LOVE, HandIconView.Demo.ARM, HandIconView.Demo.CUSTOM).map { it to listOf(0.55f) }
        val sz = resources.displayMetrics.widthPixels / 3 - 16
        for ((d, fs) in sets) for (f in fs) grid.addView(HandIconView(this).apply { demo = d; setFrame(f); layoutParams = GridLayout.LayoutParams().apply { width = sz; height = sz; setMargins(4, 4, 4, 4) } })
        setContentView(ScrollView(this).apply { addView(grid) })
    }
}
