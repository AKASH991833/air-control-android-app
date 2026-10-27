package dev.aircontrol.app

import android.app.Activity

/** Test-variant only (src/debug). Lets the CI emulator start tracking. Does not exist in release builds. */
object DebugHooks {
    fun install(a: Activity, start: () -> Unit) {
        if (a.intent.getBooleanExtra("debug_start", false))
            a.window.decorView.postDelayed({ android.util.Log.i("AirControl", "debug_start hook"); if (!GestureService.running) start() }, 800)
    }
}
