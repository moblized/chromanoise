package app.chromanoise.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateIconColorsTest {
    @Test fun everyDateIconIsReadable() {
        for (p in AccentPalette.entries) {
            for ((name, c) in listOf("light" to DateIconColors.light(p), "dark" to DateIconColors.dark(p))) {
                for ((part, fg) in listOf("accent" to c.accent, "text" to c.text, "muted" to c.muted)) {
                    val ratio = Palettes.contrast(fg, c.background)
                    assertTrue("${p.label} $name $part contrast $ratio", ratio >= 4.5)
                }
            }
        }
    }

    @Test fun darkModeIsBlackLikeTheAppIcon() {
        for (p in AccentPalette.entries) assertEquals(Palettes.BLACK, DateIconColors.dark(p).background)
    }

    @Test fun lightModeIsALightTile() {
        for (p in AccentPalette.entries) assertTrue(Palettes.luminance(DateIconColors.light(p).background) > 0.7)
    }
}
