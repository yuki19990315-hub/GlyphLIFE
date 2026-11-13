package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class BossRelicPixelsTest {
    private val monster=WildMonster("sprite-id","ライゼル","雷",
        90,14,bodyType="BEAST",stage=2)

    @Test fun sixEquipmentKindsRemain64pxFullBodyAndUseOriginalPalette() {
        val base=GbaMonsterArtStandard.canonical(MonsterDesign.sprite(monster))
        val palette=base.colors()
        val variants=setOf("moonfang","clockgear","starseal","abysscore",
            "lifepearl","spiritclock")
        val fingerprints=variants.map {kind ->
            val out=BossRelicPixels.compose(base,kind)
            assertEquals(64,out.width)
            assertEquals(64,out.height)
            assertEquals(base.opaqueCount()>0,out.opaqueCount()>0)
            assertTrue("palette overflow for "+kind, out.colors().all{it in palette})
            assertTrue("GBA audit failure for "+kind,
                GbaMonsterArtStandard.audit(out).compliant)
            assertFalse("no equipment visible for "+kind,
                out.pixels().contentEquals(base.pixels()))
            out.pixels().contentHashCode()
        }
        assertEquals(6,fingerprints.toSet().size)
    }

    @Test fun invalidOrUnownedItemsNeverAlterNativeArtwork() {
        val base=MonsterDesign.sprite(monster)
        assertSame(base,BossRelicPixels.compose(base,null))
        assertSame(base,BossRelicPixels.compose(base,"unknown"))
    }
}
