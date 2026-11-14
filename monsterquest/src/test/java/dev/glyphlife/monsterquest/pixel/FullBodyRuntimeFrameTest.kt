package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

/** Reproduces original edge-touching art and animated boss-accessory overlays. */
class FullBodyRuntimeFrameTest {
    private fun edgeTouching():PixelSprite {
        val pixels=IntArray(4096)
        // Original alpha silhouette reaches the outer rim on all four sides.
        for(y in 4..60) for(x in 2..61)
            if((x-31)*(x-31)/900+(y-30)*(y-30)/800<=1)
                pixels[y*64+x]=0xFF8CD0EE.toInt()
        for(x in 0..63)pixels[35*64+x]=0xFF17324C.toInt()
        for(y in 0..63)pixels[y*64+29]=0xFFEDE3B0.toInt()
        return PixelSprite.fromArgb(64,64,pixels)
    }

    @Test fun allFullBodyIdleFramesKeepEightPixelMarginIncludingRelics() {
        val wild=WildMonster(id="frame",species="鉱晶ゴーレム",
            element="水",bodyType="GOLEM",maxHp=90,attack=10)
        val master=GbaMonsterArtStandard.fitWithMargin(edgeTouching(),9)
        for(kind in listOf<String?>(null,"moonfang","clockgear",
                "starseal","abysscore","lifepearl","spiritclock")) {
            val frames=MonsterAnimation.idle(master,wild)
            for(frame in frames) {
                val shown=GbaMonsterArtStandard.fitWithMargin(
                    BossRelicPixels.compose(frame.sprite,kind),8)
                val b=GbaMonsterArtStandard.bounds(shown)!!
                assertTrue("left edge cropped in $kind",b.left>=8)
                assertTrue("right edge cropped in $kind",b.right<=55)
                assertTrue("top edge cropped in $kind",b.top>=8)
                assertTrue("bottom edge cropped in $kind",b.bottom<=55)
                assertEquals(64,shown.width)
                assertEquals(64,shown.height)
            }
        }
    }

    @Test fun fullBodyFitDoesNotMutateOriginal64PixelMaster() {
        val source=edgeTouching()
        val original=source.pixels().toList()
        val display=GbaMonsterArtStandard.fitWithMargin(source,9)
        assertEquals(original,source.pixels().toList())
        assertTrue(GbaMonsterArtStandard.bounds(display)!!.left>=9)
        assertTrue(GbaMonsterArtStandard.bounds(display)!!.right<=54)
    }
}
