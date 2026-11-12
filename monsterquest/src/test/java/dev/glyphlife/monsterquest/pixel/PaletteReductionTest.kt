package dev.glyphlife.monsterquest.pixel

import org.junit.Assert.*
import org.junit.Test

class PaletteReductionTest {
    @Test fun arbitraryManyColorsAreReducedTo16OrFewer() {
        val colors = IntArray(64*64) { i ->
            val r=(i*53) and 255
            val g=(i*127) and 255
            val b=(i*17) and 255
            0xff000000.toInt() or (r shl 16) or (g shl 8) or b
        }
        val quantized=PixelAssetPipeline.paletteReduce(colors)
        assertEquals(4096,quantized.size)
        assertTrue(quantized.toSet().size in 1..16)
    }

    @Test fun alphaTransparencyIsNotPaintedOver() {
        val pixels=IntArray(4096) { i ->
            when {
                i%4==0 -> 0
                i%4==1 -> 0x1000ff00 // mostly transparent
                else -> 0xff112233.toInt()
            }
        }
        val result=PixelAssetPipeline.paletteReduce(pixels)
        assertEquals(0,result[0])
        assertEquals(0,result[1])
        assertNotEquals(0,result[2])
    }

    @Test fun nativeSourceIsPixelExactIndependentOfOutputAlias() {
        val pixels=IntArray(4096)
        pixels[10]=0xffff0000.toInt()
        val sprite=PixelSprite.fromArgb(64,64,pixels)
        pixels[10]=0
        assertEquals(0xffff0000.toInt(),sprite.pixel(10,0))
    }
}
