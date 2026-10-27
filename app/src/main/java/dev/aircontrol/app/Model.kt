package dev.aircontrol.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class Mode { REELS, CAMERA }

enum class Act(val label: String, val mode: Mode) {
    NONE("Do nothing", Mode.REELS),
    NEXT("Next reel / short (swipe up)", Mode.REELS),
    PREV("Previous reel / short (swipe down)", Mode.REELS),
    PLAY_PAUSE("Pause / play (tap center)", Mode.REELS),
    BACK("Back", Mode.REELS),
    HOME("Home", Mode.REELS),
    NOTIFICATIONS("Open notifications", Mode.REELS),
    SCREENSHOT("Screenshot", Mode.REELS),
    POINTER_TOGGLE("Pointer on / off (finger moves cursor, pinch = tap)", Mode.REELS),
    CALL_FAVORITE("Call my favourite contact (asks for confirm gesture)", Mode.REELS),
    C_NONE("Do nothing", Mode.CAMERA),
    PHOTO("Take photo", Mode.CAMERA),
    VIDEO_TOGGLE("Start / stop video", Mode.CAMERA),
    SWITCH_CAMERA("Switch front / back camera", Mode.CAMERA)
}

object Builtin {
    const val SWIPE_UP = "SWIPE_UP"
    const val SWIPE_DOWN = "SWIPE_DOWN"
    val poses = listOf("Open_Palm", "Closed_Fist", "Victory", "Thumb_Up", "Thumb_Down", "Pointing_Up", "ILoveYou")
    val swipes = listOf(SWIPE_UP, SWIPE_DOWN)
    fun pretty(id: String) = when (id) {
        SWIPE_UP -> "Index finger swipe UP (motion)"
        SWIPE_DOWN -> "Index finger swipe DOWN (motion)"
        else -> if (id.startsWith("custom:")) "My gesture: " + id.removePrefix("custom:") else id.replace('_', ' ')
    }
}

class Store(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("air", Context.MODE_PRIVATE)

    var holdMs: Int
        get() = p.getInt("hold", 700)
        set(v) = p.edit().putInt("hold", v).apply()
    var cooldownMs: Int
        get() = p.getInt("cool", 1200)
        set(v) = p.edit().putInt("cool", v).apply()
    var favName: String
        get() = p.getString("favName", "") ?: ""
        set(v) = p.edit().putString("favName", v).apply()
    var favNumber: String
        get() = p.getString("favNum", "") ?: ""
        set(v) = p.edit().putString("favNum", v).apply()
    /** Emergency numbers are never allowed as the favourite (no hands-free emergency dialing from a gesture). */
    fun isEmergency(n: String): Boolean {
        val d = n.filter { it.isDigit() }
        return d in setOf("112", "100", "101", "102", "108", "911", "999", "000", "110", "119", "15", "17", "18", "1098") ||
            (android.os.Build.VERSION.SDK_INT >= 29 && android.telephony.PhoneNumberUtils.isEmergencyNumber(n))
    }
    var armRequired: Boolean
        get() = p.getBoolean("arm", true)
        set(v) = p.edit().putBoolean("arm", v).apply()
    var armPose: String
        get() = p.getString("armPose", "ILoveYou") ?: "ILoveYou"
        set(v) = p.edit().putString("armPose", v).apply()
    var matchThreshold: Float
        get() = p.getFloat("thr", 0.35f)
        set(v) = p.edit().putFloat("thr", v).apply()

    fun defaultFor(mode: Mode, id: String): Act = when (mode) {
        Mode.REELS -> when (id) {
            "Open_Palm" -> Act.PLAY_PAUSE
            "Victory" -> Act.POINTER_TOGGLE
            "ILoveYou" -> Act.NONE
            "Thumb_Down" -> Act.BACK
            "Closed_Fist" -> Act.CALL_FAVORITE
            Builtin.SWIPE_UP -> Act.NEXT
            Builtin.SWIPE_DOWN -> Act.PREV
            else -> Act.NONE
        }
        Mode.CAMERA -> when (id) {
            "Victory" -> Act.PHOTO
            "Closed_Fist" -> Act.VIDEO_TOGGLE
            else -> Act.C_NONE
        }
    }

    fun action(mode: Mode, id: String): Act {
        val s = p.getString("map_${mode.name}_$id", null)
        return s?.let { runCatching { Act.valueOf(it) }.getOrNull() } ?: defaultFor(mode, id)
    }

    fun setAction(mode: Mode, id: String, a: Act) = p.edit().putString("map_${mode.name}_$id", a.name).apply()

    // custom gestures: name -> list of 63-float template vectors (landmarks only, never images)
    fun customNames(): List<String> = loadCustom().keys.toList()
    fun loadCustom(): Map<String, List<FloatArray>> {
        val out = LinkedHashMap<String, List<FloatArray>>()
        val raw = p.getString("custom", "{}") ?: "{}"
        val o = JSONObject(raw)
        for (k in o.keys()) {
            val arr = o.getJSONArray(k)
            val list = ArrayList<FloatArray>()
            for (i in 0 until arr.length()) {
                val v = arr.getJSONArray(i)
                list.add(FloatArray(v.length()) { j -> v.getDouble(j).toFloat() })
            }
            out[k] = list
        }
        return out
    }
    fun saveCustom(name: String, samples: List<FloatArray>) {
        val m = loadCustom().toMutableMap()
        m[name] = samples
        val o = JSONObject()
        for ((k, list) in m) {
            val arr = JSONArray()
            for (f in list) { val v = JSONArray(); f.forEach { x -> v.put(x.toDouble()) }; arr.put(v) }
            o.put(k, arr)
        }
        p.edit().putString("custom", o.toString()).apply()
    }
    fun deleteCustom(name: String) {
        val m = loadCustom().toMutableMap(); m.remove(name)
        val o = JSONObject()
        for ((k, list) in m) {
            val arr = JSONArray()
            for (f in list) { val v = JSONArray(); f.forEach { x -> v.put(x.toDouble()) }; arr.put(v) }
            o.put(k, arr)
        }
        p.edit().putString("custom", o.toString()).apply()
    }

    fun allGestureIds(): List<String> =
        Builtin.poses + Builtin.swipes + customNames().map { "custom:$it" }
}
