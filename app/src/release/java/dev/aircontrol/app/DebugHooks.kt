package dev.aircontrol.app

import android.app.Activity

/** Release: intentionally empty. No external auto-start. */
object DebugHooks { @Suppress("UNUSED_PARAMETER") fun install(a: Activity, start: () -> Unit) {} }
