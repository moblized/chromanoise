package app.chromanoise.clock

import app.chromanoise.alarm.FlipDetector
import app.chromanoise.data.AccentPalette
import app.chromanoise.data.Palettes
import app.chromanoise.data.SchemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTest {
    // ---- Timer -------------------------------------------------------------------------

    @Test fun timerRunsPausesAndResumes() {
        var t = TimerState.start(5 * 60_000L, now = 1_000)
        assertEquals(5 * 60_000L, t.remaining(1_000))
        assertEquals(4 * 60_000L, t.remaining(61_000))
        t = t.pause(61_000)
        assertTrue(t.paused)
        // Time passing while paused doesn't count.
        assertEquals(4 * 60_000L, t.remaining(999_999))
        t = t.resume(100_000)
        assertEquals(100_000L + 4 * 60_000L, t.endsAt)
        assertEquals(0L, t.remaining(10_000_000)) // never negative
    }

    @Test fun addingAMinute() {
        val running = TimerState.start(60_000, now = 0).add(60_000, now = 30_000)
        assertEquals(120_000L, running.durationMs)
        assertEquals(90_000L, running.remaining(30_000))
        val paused = TimerState.start(60_000, now = 0).pause(10_000).add(60_000, now = 10_000)
        assertEquals(110_000L, paused.remainingMs)
        // On an idle timer it just starts a one-minute timer.
        assertEquals(60_000L, TimerState().add(60_000, now = 5).remaining(5))
    }

    @Test fun timerSurvivesAShiftedClock() {
        // After a reboot the monotonic clock restarts; shifting keeps the same time left.
        val before = TimerState.start(600_000, now = 5_000_000)
        val after = before.shifted(-4_999_000)
        assertEquals(before.remaining(5_100_000), after.remaining(101_000))
    }

    @Test fun progressFraction() {
        val t = TimerState.start(100_000, now = 0)
        assertEquals(1f, t.fractionLeft(0))
        assertEquals(0.25f, t.fractionLeft(75_000))
        assertEquals(0f, t.fractionLeft(200_000))
    }

    @Test fun keypadEntry() {
        var d = ""
        for (k in listOf("1", "3", "0")) d = TimerInput.type(d, k)
        assertEquals("130", d)
        assertEquals(90_000L, TimerInput.toMillis(d)) // 1 min 30 s
        assertEquals(90_000L, TimerInput.toMillis("90")) // 90 s
        assertEquals("", TimerInput.type("", "00")) // leading zeros ignored
        assertEquals(Triple(0, 13, 0), TimerInput.parts(TimerInput.type("13", "00"))) // 13:00
        assertEquals(Triple(1, 30, 0), TimerInput.parts(TimerInput.type("130", "00"))) // 1:30:00
        assertEquals("123456", TimerInput.type("123456", "7")) // full: extra digit ignored
        assertEquals("12345", TimerInput.backspace("123456"))
        assertEquals(((12 * 3600L) + (34 * 60L) + 56) * 1000, TimerInput.toMillis("123456"))
    }

    @Test fun textFormats() {
        assertEquals("5:00", ClockText.timer(300_000))
        assertEquals("5:00", ClockText.timer(299_001)) // rounds up until a whole second has gone
        assertEquals("4:59", ClockText.timer(299_000))
        assertEquals("0:01", ClockText.timer(1))
        assertEquals("0:00", ClockText.timer(0))
        assertEquals("1:00:00", ClockText.timer(3_600_000))
        assertEquals("00:00.00", ClockText.stopwatch(0))
        assertEquals("02:03.45", ClockText.stopwatch(123_450))
        assertEquals("1:02:03.45", ClockText.stopwatch(3_723_450))
        // Overtime / paused chip: whole seconds rounded down.
        assertEquals("0:05", ClockText.clock(5_999))
        assertEquals("12:34", ClockText.clock(754_000))
        assertEquals("1:02:03", ClockText.clock(3_723_999))
        assertEquals("0:00", ClockText.clock(-50))
        assertEquals("5 min", ClockText.shortDuration(300_000))
        assertEquals("1 h 30 min", ClockText.shortDuration(5_400_000))
        assertEquals("45 s", ClockText.shortDuration(45_000))
    }

    // ---- Stopwatch ---------------------------------------------------------------------

    @Test fun stopwatchLapsAndPauses() {
        var s = StopwatchState().start(1_000)
        s = s.lap(11_000) // lap 1: 10 s
        s = s.lap(26_000) // lap 2: 15 s
        s = s.pause(30_000)
        assertEquals(29_000L, s.elapsed(99_999)) // frozen while paused
        assertTrue(!s.idle)
        s = s.lap(40_000) // can't lap while paused
        assertEquals(2, s.lapMarks.size)
        s = s.start(50_000)
        s = s.lap(56_000) // lap 3: 4 s + 6 s = 10 s
        assertEquals(listOf(10_000L, 15_000L, 10_000L), s.lapTimes())
        assertEquals(35_000L, s.elapsed(56_000))
        assertTrue(StopwatchState().idle)
    }

    // ---- Flip to snooze ----------------------------------------------------------------

    @Test fun flipFaceDownSnoozes() {
        val f = FlipDetector(holdMs = 500)
        assertFalse(f.onGravityZ(0f, 0)) // held upright: arms
        assertFalse(f.onGravityZ(-9.6f, 100)) // face down…
        assertFalse(f.onGravityZ(-9.7f, 500)) // …not long enough yet
        assertTrue(f.onGravityZ(-9.7f, 600)) // held 500 ms: snooze
        assertFalse(f.onGravityZ(-9.7f, 2_000)) // only once
    }

    @Test fun alreadyFaceDownDoesNotSnooze() {
        val f = FlipDetector(holdMs = 500)
        // Lying face down on the nightstand when the alarm starts.
        for (t in 0L..5_000L step 100) assertFalse(f.onGravityZ(-9.8f, t))
        // Picked up, then put back face down: now it counts.
        assertFalse(f.onGravityZ(4f, 5_100))
        assertFalse(f.onGravityZ(-9.8f, 5_200))
        assertTrue(f.onGravityZ(-9.8f, 5_800))
    }

    @Test fun aWobbleDoesNotSnooze() {
        val f = FlipDetector(holdMs = 500)
        f.onGravityZ(9.8f, 0)
        assertFalse(f.onGravityZ(-9f, 100))
        assertFalse(f.onGravityZ(-5f, 300)) // tilted back up a bit: timer resets
        assertFalse(f.onGravityZ(-9f, 400))
        assertFalse(f.onGravityZ(-9f, 800))
        assertTrue(f.onGravityZ(-9f, 900))
    }

    // ---- Colour themes -----------------------------------------------------------------

    @Test fun everyThemeIsReadable() {
        for (p in AccentPalette.entries) {
            for ((name, c) in listOf(
                "dark" to Palettes.dark(p, pureBlack = false),
                "black" to Palettes.dark(p, pureBlack = true),
                "light" to Palettes.light(p),
            )) {
                val where = "${p.label} $name"
                checkText(where, "text", c.onBackground, c.background, 7.0)
                checkText(where, "text on cards", c.onBackground, c.surfaceContainerHighest, 7.0)
                checkText(where, "secondary text", c.onSurfaceVariant, c.surfaceContainerHighest, 4.5)
                checkText(where, "secondary text on bg", c.onSurfaceVariant, c.background, 4.5)
                checkText(where, "button text", c.onPrimary, c.primary, 4.5)
                checkText(where, "accent as text", c.primary, c.surfaceContainerHigh, 4.5)
                checkText(where, "chip text", c.onSecondaryContainer, c.secondaryContainer, 4.5)
                checkText(where, "container text", c.onPrimaryContainer, c.primaryContainer, 4.5)
            }
        }
    }

    @Test fun violetMatchesTheOriginalLook() {
        val c: SchemeColors = Palettes.dark(AccentPalette.VIOLET, pureBlack = false)
        assertClose(0x0A0C16, c.background)
        assertClose(0x13172A, c.surfaceContainer)
        assertClose(0x1A1E36, c.surfaceContainerHigh)
        assertClose(0x22263F, c.surfaceContainerHighest)
    }

    /** Within one shade step per channel of the hand-picked original. */
    private fun assertClose(expected: Int, actual: Int) {
        val ok = (0..2).all { i ->
            val sh = i * 8
            kotlin.math.abs((expected shr sh and 0xFF) - (actual shr sh and 0xFF)) <= 1
        }
        assertTrue("expected ${hex(expected)} got ${hex(actual)}", ok)
    }

    private fun checkText(where: String, what: String, fg: Int, bg: Int, min: Double) {
        val ratio = Palettes.contrast(fg, bg)
        assertTrue("$where $what ${"%.1f".format(ratio)}:1 (need $min)", ratio >= min)
    }

    private fun hex(c: Int) = "%06X".format(c and 0xFFFFFF)
}
