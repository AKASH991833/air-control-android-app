package dev.aircontrol.app

import kotlin.math.abs

/**
 * Turns per-frame hand results into discrete gesture events.
 * Static poses: must be held holdMs, fire once, then need release + cooldown.
 * Swipes: temporal detector on index-fingertip vertical travel, separate from pose classification.
 * Arm pose: when enabled, no action fires until the arm pose has been held; stays armed for ARM_WINDOW_MS.
 */
class GestureEngine(private val store: Store, private val onGesture: (String) -> Unit, private val onState: (String) -> Unit = {}) {
    private var curId: String? = null
    private var since = 0L
    private var firedCurrent = false
    private var lastFiredId: String? = null
    private var releaseSince = 0L
    private var lastFire = 0L
    private var armedUntil = 0L
    @Volatile var suspendSwipes = false
    private val swipeBuf = ArrayDeque<Pair<Long, Float>>()
    private val custom = store.loadCustom().mapValues { e -> e.value }

    fun reload() {}
    fun armedLeftMs(now: Long): Long = if (!store.armRequired) -1L else maxOf(0L, armedUntil - now)

    private fun classify(r: HandResult): String {
        var best: String? = null; var bestD = Float.MAX_VALUE
        val thr = store.matchThreshold
        val t = HandAnalyzer.template(r.pts)
        for ((name, samples) in custom) {
            for (s in samples) {
                val d = HandAnalyzer.distance(t, s)
                if (d < bestD) { bestD = d; best = name }
            }
        }
        if (best != null && bestD < thr) return "custom:$best"
        return if (r.cannedScore >= 0.6f && r.canned != "None") r.canned else "None"
    }

    fun onFrame(r: HandResult?, now: Long) {
        if (r == null) { curId = null; swipeBuf.clear(); if (releaseSince == 0L) releaseSince = now; if (now - releaseSince >= RELEASE_MS) lastFiredId = null; return }
        val id = classify(r)
        val armedNow = !store.armRequired || now < armedUntil

        // temporal swipe detector
        if (!suspendSwipes && HandAnalyzer.indexOnly(r.pts)) {
            swipeBuf.addLast(now to r.pts[8 * 3 + 1])
            while (swipeBuf.isNotEmpty() && now - swipeBuf.first().first > 450) swipeBuf.removeFirst()
            if (swipeBuf.size >= 4 && armedNow && now - lastFire > store.cooldownMs) {
                val dy = swipeBuf.last().second - swipeBuf.first().second
                if (abs(dy) > 0.17f) {
                    fire(if (dy < 0) Builtin.SWIPE_UP else Builtin.SWIPE_DOWN, now)
                    swipeBuf.clear()
                    return
                }
            }
        } else swipeBuf.clear()

        if (id != curId) { curId = id; since = now; firedCurrent = false }
        // a pose that just fired must be released (different pose or no hand) for RELEASE_MS before it can fire again
        if (id != lastFiredId) { if (releaseSince == 0L) releaseSince = now; if (now - releaseSince >= RELEASE_MS) lastFiredId = null } else releaseSince = 0L
        if (id == lastFiredId) return
        if (id == "None" || firedCurrent) return
        if (now - since < store.holdMs) return
        if (store.armRequired && id == store.armPose) {
            armedUntil = now + ARM_WINDOW_MS; firedCurrent = true
            onState("Armed for ${ARM_WINDOW_MS / 1000}s"); return
        }
        if (!armedNow) return
        if (now - lastFire < store.cooldownMs) return
        firedCurrent = true
        fire(id, now)
    }

    private fun fire(id: String, now: Long) {
        lastFire = now; lastFiredId = id; releaseSince = 0L
        if (store.armRequired) armedUntil = now + ARM_WINDOW_MS
        onGesture(id)
    }

    companion object { const val ARM_WINDOW_MS = 10000L; const val RELEASE_MS = 700L }
}
