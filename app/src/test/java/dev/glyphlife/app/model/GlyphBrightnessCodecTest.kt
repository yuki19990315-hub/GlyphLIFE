package dev.glyphlife.app.model

import org.junit.Assert.*
import org.junit.Test

class GlyphBrightnessCodecTest {
    @Test fun grayscaleRoundTripsWithoutLosingLevels() {
        val levels=List(169) { i ->
            if(GlyphMask.isPhysical(i)) ((i*11+23)%256) else 0
        }
        val hex=GlyphBrightnessCodec.encode(levels)
        assertEquals(338,hex.length)
        assertEquals(levels,GlyphBrightnessCodec.decode(hex))
        assertTrue(levels.any { it in 1..254 })
        assertTrue(levels.any { it == 0 })
    }

    @Test fun legacyFrameCanStillBeEditedAndBrightnessIsCleared() {
        val levels=List(169) { i -> if(GlyphMask.isPhysical(i)) 128 else 0 }
        val frame=GlyphFrame(PixelArt.empty(),350,levels)
        val original=GlyphAnimation(listOf(frame))
        assertEquals(levels,original.current.levels)
        val edited=original.replace(GlyphOps.set(original.current.pixels,84,true))
        assertNull(edited.current.levels)
        assertTrue(edited.current.pixels[84])
    }

    @Test fun invalidValuesAndOffMaskPixelsAreRejected() {
        val bad=MutableList(169) { 0 }.apply { this[0]=255 }
        try { GlyphBrightnessCodec.encode(bad); fail("Outside mask") }
        catch (_:IllegalArgumentException) {}
        try { GlyphBrightnessCodec.decode("ff"); fail("Invalid length") }
        catch (_:IllegalArgumentException) {}
        try { GlyphBrightnessCodec.decode("gg".repeat(169)); fail("Invalid hex") }
        catch (_:IllegalArgumentException) {}
    }

    @Test fun exportUsesGrayLevelsRatherThanDiscardingThem() {
        val opaque=List(169) { GlyphMask.isPhysical(it) }
        val levels=List(169) { i -> if(GlyphMask.isPhysical(i)) 64 else 0 }
        val grayscale=GlyphFrame(opaque,levels=levels)
        val binary=GlyphFrame(opaque)
        assertEquals(64,GlyphBrightnessCodec.intensity(grayscale,84))
        assertEquals(255,GlyphBrightnessCodec.intensity(binary,84))
        assertEquals(0,GlyphBrightnessCodec.intensity(grayscale,0))
    }
}
