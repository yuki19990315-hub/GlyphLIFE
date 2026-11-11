package dev.glyphlife.app.model

import org.junit.Assert.*
import org.junit.Test

class GlyphAnimationTest {
    @Test fun framesAre169PixelsAndHaveEditableDuration() {
        val a = GlyphAnimation()
        assertEquals(169, a.current.pixels.size)
        assertEquals(120, a.setDuration(120).current.durationMs)
        assertEquals(2000, a.setDuration(5000).current.durationMs)
    }

    @Test fun animationDuplicateDeleteAndLimit() {
        val start = GlyphAnimation()
        val two = start.addFrame(duplicate = true)
        assertEquals(2, two.frames.size)
        assertEquals(1, two.selected)
        assertEquals(start.current.pixels, two.current.pixels)
        assertEquals(start, two.deleteFrame())
        var a = start
        repeat(70) { a = a.addFrame() }
        assertEquals(64, a.frames.size)
    }

    @Test fun rotateFourTimesIsIdentityAndMirrorTwiceIsIdentity() {
        val p = PixelArt.defaultPet()
        var r = p
        repeat(4) { r = GlyphOps.rotateRight(r) }
        assertEquals(p, r)
        assertEquals(p, GlyphOps.mirrorHorizontal(GlyphOps.mirrorHorizontal(p)))
    }

    @Test fun fillRespectsConnectedRegion() {
        val p = List(169) { index -> index % 13 == 6 }
        val f = GlyphOps.floodFill(p, 0, true)
        assertTrue(f[0])
        assertTrue(f[6])
        assertFalse(f[7])
    }

    @Test fun encodeDecodeRoundTrip() {
        val p = PixelArt.defaultPet()
        assertEquals(p, GlyphOps.decode(GlyphOps.encode(p)))
        try {
            GlyphOps.decode("bad")
            fail("Should reject invalid input")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun shiftDropsPixelsAtBoundary() {
        val p = GlyphOps.set(PixelArt.empty(), 0, true)
        val shifted = GlyphOps.shift(p, -1, 0)
        assertEquals(0, shifted.count { it })
    }
}
