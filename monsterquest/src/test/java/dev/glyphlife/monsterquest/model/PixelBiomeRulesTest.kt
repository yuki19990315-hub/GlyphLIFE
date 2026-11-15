package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test

class PixelBiomeRulesTest {
    @Test fun allAreasBecomeAccessibleAndBossHasOwnZone() {
        assertEquals(PixelBiome.MEADOW,PixelBiomeRules.zone(1,false))
        assertEquals(PixelBiome.FOREST,PixelBiomeRules.zone(3,false))
        assertEquals(PixelBiome.CAVE,PixelBiomeRules.zone(5,false))
        assertEquals(PixelBiome.RUINS,PixelBiomeRules.zone(7,false))
        assertEquals(PixelBiome.VOLCANO,PixelBiomeRules.zone(9,false))
        assertEquals(PixelBiome.ICE,PixelBiomeRules.zone(11,false))
        assertEquals(PixelBiome.COAST,PixelBiomeRules.zone(13,false))
        assertEquals(PixelBiome.WASTELAND,PixelBiomeRules.zone(15,false))
        assertEquals(PixelBiome.DARK,PixelBiomeRules.zone(17,false))
        assertEquals(PixelBiome.MEADOW,PixelBiomeRules.zone(19,false))
        assertEquals(PixelBiome.BOSS,PixelBiomeRules.zone(5,true))
    }

    @Test fun integerParallaxNeverStretchesLogicalWorld() {
        assertEquals(0,PixelBiomeRules.cameraOffset(0f,0))
        assertTrue(PixelBiomeRules.cameraOffset(100f,0)<
            PixelBiomeRules.cameraOffset(100f,1))
        assertTrue(PixelBiomeRules.cameraOffset(100f,1)<
            PixelBiomeRules.cameraOffset(100f,2))
        assertEquals(PixelBiomeRules.cameraOffset(120f,2),
            PixelBiomeRules.cameraOffset(120f+PixelBiomeRules.WORLD_WIDTH,2))
        assertEquals(16,PixelBiomeRules.TILE)
        assertEquals(180,PixelBiomeRules.CAMERA_HEIGHT)
        for((w,h) in listOf(320 to 180,640 to 360,1080 to 610,
            720 to 720,1920 to 1080,1500 to 600)) {
            val scale=PixelBiomeRules.pixelScale(w,h)
            assertTrue(scale*320>=w)
            assertTrue(scale*180>=h)
            assertTrue(scale>=1)
        }
    }
}
