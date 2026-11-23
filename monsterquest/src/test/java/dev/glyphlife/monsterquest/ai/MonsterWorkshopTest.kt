package dev.glyphlife.monsterquest.ai

import dev.glyphlife.monsterquest.pixel.GbaMonsterArtStandard
import dev.glyphlife.monsterquest.pixel.PixelSprite
import org.junit.Assert.*
import org.junit.Test

class MonsterWorkshopTest {
    @Test fun nativeSpriteMustSatisfyPaletteAndMargins() {
        val pixels = IntArray(4096)
        for (y in 10..45) for (x in 14..46) pixels[y*64+x] = 0xff336699.toInt()
        val sprite = PixelSprite.fromArgb(64,64,pixels)
        assertTrue(GbaMonsterArtStandard.audit(sprite).compliant)
    }
    @Test fun opaqueFullCanvasFailsTheAudit() {
        val sprite = PixelSprite.fromArgb(64,64,IntArray(4096){ 0xff112233.toInt() })
        assertFalse(GbaMonsterArtStandard.audit(sprite).compliant)
    }
}
