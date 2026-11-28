package app.chromanoise.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightWindowTest {
    private fun t(h: Int, m: Int = 0) = h * 60 + m

    @Test fun overnightWindowWrapsPastMidnight() {
        val from = t(20)
        val to = t(8)
        assertTrue(NightWindow.contains(t(20), from, to)) // starts at 8:00 PM
        assertTrue(NightWindow.contains(t(23, 30), from, to))
        assertTrue(NightWindow.contains(t(0), from, to))
        assertTrue(NightWindow.contains(t(7, 59), from, to))
        assertFalse(NightWindow.contains(t(8), from, to)) // ends at 8:00 AM
        assertFalse(NightWindow.contains(t(13), from, to))
        assertFalse(NightWindow.contains(t(19, 59), from, to))
    }

    @Test fun sameDayWindow() {
        assertTrue(NightWindow.contains(t(14), t(13), t(15)))
        assertFalse(NightWindow.contains(t(15), t(13), t(15)))
        assertFalse(NightWindow.contains(t(12, 59), t(13), t(15)))
    }

    @Test fun equalEndsMeansAllDay() {
        assertTrue(NightWindow.contains(t(3), t(22), t(22)))
        assertTrue(NightWindow.contains(t(22), t(22), t(22)))
    }
}
