package dev.glyphlife.app.model

import org.junit.Assert.*
import org.junit.Test

class GlyphClockTest {
    @Test fun everyTimeFitsThe137PhysicalPixels() {
        for (h in 0..23) for (m in 0..59 step 3) {
            val pixels = GlyphClock.draw(h, m, 50)
            assertEquals(169, pixels.size)
            assertTrue(pixels.any { it })
            pixels.forEachIndexed { index, lit ->
                if (lit) assertTrue(GlyphMask.isPhysical(index))
            }
        }
    }

    @Test fun clockChangesMinuteAndHour() {
        val a = GlyphClock.draw(10, 5, 80)
        val b = GlyphClock.draw(10, 6, 80)
        val c = GlyphClock.draw(11, 5, 80)
        assertNotEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test fun batteryControlsBottomFiveLeds() {
        val empty = GlyphClock.draw(12, 30, 0)
        val full = GlyphClock.draw(12, 30, 100)
        assertEquals(0, (4..8).count { empty[12 * 13 + it] })
        assertEquals(5, (4..8).count { full[12 * 13 + it] })
        assertEquals(2, (4..8).count { GlyphClock.draw(12,30,50)[12*13+it] })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImpossibleHour() { GlyphClock.draw(24, 0, 50) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImpossibleBattery() { GlyphClock.draw(12, 0, 101) }
}
