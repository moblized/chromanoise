package app.chromanoise.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class AlarmTest {
    private val ny = ZoneId.of("America/New_York")

    /** 2026-10-12 is a Monday. */
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, minute, second, 0, ny)

    @Test fun onceLaterToday() {
        val a = Alarm(1, 7, 30)
        assertEquals(at(12, 7, 30), a.nextOccurrence(at(12, 6, 0)))
    }

    @Test fun onceAlreadyPassedGoesToTomorrow() {
        val a = Alarm(1, 7, 30)
        assertEquals(at(13, 7, 30), a.nextOccurrence(at(12, 8, 0)))
    }

    @Test fun exactlyNowMeansNextTime() {
        val a = Alarm(1, 7, 30)
        assertEquals(at(13, 7, 30), a.nextOccurrence(at(12, 7, 30)))
        // …but a second before still counts as today.
        assertEquals(at(12, 7, 30), a.nextOccurrence(at(12, 7, 29, 59)))
    }

    @Test fun weekdaysSkipTheWeekend() {
        val a = Alarm(1, 7, 30, days = Alarm.WEEKDAYS)
        // Friday 16th after 7:30 → Monday 19th.
        assertEquals(at(19, 7, 30), a.nextOccurrence(at(16, 8, 0)))
        // Saturday morning → Monday.
        assertEquals(at(19, 7, 30), a.nextOccurrence(at(17, 6, 0)))
    }

    @Test fun weekendsOnSaturdayMorning() {
        val a = Alarm(1, 9, 0, days = Alarm.WEEKENDS)
        assertEquals(at(17, 9, 0), a.nextOccurrence(at(17, 6, 0)))
        assertEquals(at(18, 9, 0), a.nextOccurrence(at(17, 10, 0)))
    }

    @Test fun singleDayWrapsToNextWeek() {
        val a = Alarm(1, 7, 0).withDay(DayOfWeek.MONDAY, true)
        assertEquals(at(19, 7, 0), a.nextOccurrence(at(12, 7, 1)))
    }

    @Test fun springForwardGapShiftsToThreeThirty() {
        // 2026-03-08: clocks in New York jump from 2:00 to 3:00.
        val a = Alarm(1, 2, 30)
        val next = a.nextOccurrence(ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, ny))
        assertEquals(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, ny), next)
        assertEquals("2026-03-08T07:30:00Z", next.toInstant().toString())
    }

    @Test fun fallBackRingsOnceAtTheFirstOneThirty() {
        // 2026-11-01: 1:00–2:00 happens twice. The alarm should ring at the first 1:30 (EDT).
        val a = Alarm(1, 1, 30)
        val next = a.nextOccurrence(ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, ny))
        assertEquals("2026-11-01T05:30:00Z", next.toInstant().toString())
    }

    @Test fun snoozeWinsAndOffMeansNothing() {
        val now = at(12, 7, 30).toInstant().toEpochMilli()
        val snoozeAt = now + 10 * 60_000L
        val repeating = Alarm(1, 7, 30, days = Alarm.EVERY_DAY, snoozedUntil = snoozeAt)
        assertEquals(snoozeAt, repeating.nextTrigger(now, ny))

        val off = Alarm(1, 7, 30, enabled = false)
        assertNull(off.nextTrigger(now, ny))

        // A one-time alarm switches itself off when it rings, but its snooze still counts.
        val onceSnoozed = Alarm(1, 7, 30).afterFiring().copy(snoozedUntil = snoozeAt)
        assertFalse(onceSnoozed.enabled)
        assertTrue(onceSnoozed.isOn)
        assertEquals(snoozeAt, onceSnoozed.nextTrigger(now, ny))

        // An old snooze from the past is ignored.
        val stale = Alarm(1, 7, 30, days = Alarm.EVERY_DAY, snoozedUntil = now - 1)
        assertEquals(at(13, 7, 30).toInstant().toEpochMilli(), stale.nextTrigger(now, ny))
    }

    @Test fun afterFiringKeepsRepeatingAlarmsOn() {
        val repeating = Alarm(1, 7, 30, days = Alarm.WEEKDAYS, snoozedUntil = 5L, nextAt = 5L).afterFiring()
        assertTrue(repeating.enabled)
        assertNull(repeating.snoozedUntil)
        assertNull(repeating.nextAt)
        assertFalse(Alarm(2, 7, 30).afterFiring().enabled)
    }

    @Test fun dayDescriptions() {
        val us = Locale.US
        assertEquals("Once", AlarmText.describeDays(0, us))
        assertEquals("Every day", AlarmText.describeDays(Alarm.EVERY_DAY, us))
        assertEquals("Weekdays", AlarmText.describeDays(Alarm.WEEKDAYS, us))
        assertEquals("Weekends", AlarmText.describeDays(Alarm.WEEKENDS, us))
        val monWedSun = Alarm(1, 7, 0)
            .withDay(DayOfWeek.MONDAY, true)
            .withDay(DayOfWeek.WEDNESDAY, true)
            .withDay(DayOfWeek.SUNDAY, true)
            .days
        // US weeks start on Sunday; UK weeks on Monday.
        assertEquals("Sun, Mon, Wed", AlarmText.describeDays(monWedSun, us))
        assertEquals("Mon, Wed, Sun", AlarmText.describeDays(monWedSun, Locale.UK))
        assertEquals(DayOfWeek.SUNDAY, AlarmText.weekOrder(us).first())
    }

    @Test fun durations() {
        assertEquals("less than a minute", AlarmText.duration(30_000))
        assertEquals("1 min", AlarmText.duration(60_000))
        assertEquals("25 min", AlarmText.duration(24 * 60_000L + 1))
        assertEquals("9 h 40 min", AlarmText.duration((9 * 60 + 40) * 60_000L))
        assertEquals("1 d 2 h", AlarmText.duration((26 * 60 + 5) * 60_000L))
        assertEquals("8 h", AlarmText.duration(8 * 3_600_000L))
    }

    @Test fun vibrationWaveformsAreValid() {
        for (p in VibrationPattern.entries) {
            for (gentle in listOf(false, true)) {
                val w = p.waveform(gentle)
                assertEquals(w.timings.size, w.amplitudes.size)
                assertTrue("${p.label} amplitudes in range", w.amplitudes.all { it in 0..255 })
                assertTrue("${p.label} timings positive", w.timings.all { it > 0 })
                assertTrue("${p.label} repeat index", w.repeatFrom in w.timings.indices)
                // The repeating part is always full strength.
                val loopPeak = w.amplitudes.drop(w.repeatFrom).max()
                assertTrue("${p.label} loop peak $loopPeak", loopPeak >= 200)
            }
            val preview = p.preview()
            assertEquals(-1, preview.repeatFrom)
            assertEquals(p.cycleMs * 2, preview.timings.sum())
        }
    }

    @Test fun gentleVibrationBuildsUpOverAMinute() {
        val w = VibrationPattern.PULSE.waveform(gentle = true)
        val rampMs = w.timings.take(w.repeatFrom).sum()
        assertTrue("ramp lasts $rampMs ms", rampMs >= Alarm.GENTLE_FADE_MS && rampMs < Alarm.GENTLE_FADE_MS + VibrationPattern.PULSE.cycleMs)
        val buzzes = w.amplitudes.take(w.repeatFrom).filter { it > 0 }
        assertTrue("starts soft (${buzzes.first()})", buzzes.first() in 40..60)
        assertTrue("never gets weaker", buzzes.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("ends near full (${buzzes.last()})", buzzes.last() > 230)
        // Without gentle wake it's straight to the full-strength loop.
        assertEquals(0, VibrationPattern.PULSE.waveform(gentle = false).repeatFrom)
    }

    @Test fun silentAlarmsAlwaysVibrate() {
        val silent = Alarm(1, 7, 0, soundUri = Alarm.SILENT_SOUND, vibrate = false)
        assertTrue(silent.silent)
        assertTrue(silent.vibrates)
        assertFalse(Alarm(1, 7, 0, vibrate = false).vibrates)
        assertEquals(VibrationPattern.PULSE, VibrationPattern.fromName("nonsense"))
        assertEquals(VibrationPattern.HEARTBEAT, VibrationPattern.fromName("HEARTBEAT"))
    }

    @Test fun chimeLoopIsLoudButNotClippedAndSeamless() {
        val loop = ChimeSynth.loop(44_100)
        assertEquals((44_100 * ChimeSynth.LOOP_SECONDS).roundToInt(), loop.size)
        val peak = loop.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak in 22_000..23_500) // normalised to 70% of full scale
        // The jump from the last sample back to the first is tiny compared with the peak.
        val seam = abs(loop.last() - loop.first())
        assertTrue("seam jump $seam", seam < peak * 0.02)
        // And it isn't one long note: there's a quiet stretch before it repeats.
        val tail = loop.copyOfRange(loop.size - 4_410, loop.size).maxOf { abs(it.toInt()) }
        assertTrue("tail $tail", tail < peak * 0.25)
    }
}
