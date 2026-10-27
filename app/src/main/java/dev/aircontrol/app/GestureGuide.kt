package dev.aircontrol.app

/** Plain-English name, how-to and demo for every built-in gesture. Custom gestures get a generic card. */
object GestureGuide {
    class G(val title: String, val how: String, val demo: HandIconView.Demo)

    fun info(id: String): G = when (id) {
        "Open_Palm" -> G("Open palm", "All five fingers open, palm toward the camera. Hold for a moment.", HandIconView.Demo.OPEN_PALM)
        "Closed_Fist" -> G("Fist", "Close your hand into a fist. Hold for a moment.", HandIconView.Demo.FIST)
        "Victory" -> G("Peace sign", "Raise your index and middle finger. Hold for a moment.", HandIconView.Demo.VICTORY)
        "Thumb_Up" -> G("Thumbs up", "Thumb pointing up, other fingers closed.", HandIconView.Demo.THUMB_UP)
        "Thumb_Down" -> G("Thumbs down", "Thumb pointing down, other fingers closed.", HandIconView.Demo.THUMB_DOWN)
        "Pointing_Up" -> G("Point up", "Only the index finger up. Keep it still.", HandIconView.Demo.POINT_UP)
        "ILoveYou" -> G("I-love-you hand", "Thumb, index and little finger out; middle and ring finger folded.", HandIconView.Demo.LOVE)
        Builtin.SWIPE_UP -> G("Swipe up", "Only the index finger up, then move it up quickly.", HandIconView.Demo.SWIPE_UP)
        Builtin.SWIPE_DOWN -> G("Swipe down", "Only the index finger up, then move it down quickly.", HandIconView.Demo.SWIPE_DOWN)
        else -> if (id.startsWith("custom:")) G(id.removePrefix("custom:"), "Your own pose. Show it like you did when you recorded it.", HandIconView.Demo.CUSTOM)
        else G(id.replace('_', ' '), "", HandIconView.Demo.OPEN_PALM)
    }
}
