package app.chromanoise.alarm

/**
 * Decides when a ringing phone has been turned face down, from the gravity reading on the
 * axis that comes out of the screen (+9.8 m/s² lying face up, -9.8 face down).
 *
 * Two safeguards against snoozing by accident:
 *  - it only arms once the phone has been seen *not* face down, so a phone that was already
 *    lying face down when the alarm started doesn't snooze itself instantly;
 *  - it must stay face down for [holdMs] (a quick wobble doesn't count).
 *
 * Pure logic so it's unit-tested; [FlipToSnooze] feeds it real sensor data.
 */
class FlipDetector(private val holdMs: Long = 500) {
    private var armed = false
    private var downSince: Long? = null

    /** Returns true exactly once, when a flip is confirmed. */
    fun onGravityZ(z: Float, nowMs: Long): Boolean {
        if (z > ARM_Z) {
            armed = true
            downSince = null
            return false
        }
        if (z >= FACE_DOWN_Z || !armed) {
            downSince = null
            return false
        }
        val since = downSince ?: nowMs.also { downSince = it }
        if (nowMs - since >= holdMs) {
            armed = false
            downSince = null
            return true
        }
        return false
    }

    private companion object {
        /** Anything less face-down than about 110° from flat arms the detector. */
        const val ARM_Z = -3f

        /** Within roughly 35° of lying flat on its face. */
        const val FACE_DOWN_Z = -8f
    }
}
