package dev.aircontrol.app

/** Read-only live state shared from the tracking service to the screen. Holds no images, only short text and numbers. */
object Live {
    @Volatile var frames = 0L
    @Volatile var lastFrameAt = 0L
    @Volatile var hand = false
    @Volatile var pose = "None"
    @Volatile var armLeftMs = -1L
    @Volatile var lastAction = ""
    @Volatile var lastActionAt = 0L
    @Volatile var msg = ""
    @Volatile var idle = false
    @Volatile var hot = false
    fun reset() { frames = 0; lastFrameAt = 0; hand = false; pose = "None"; armLeftMs = -1; lastAction = ""; lastActionAt = 0; msg = ""; idle = false; hot = false }
}
