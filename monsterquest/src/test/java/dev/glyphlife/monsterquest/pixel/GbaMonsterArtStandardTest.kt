package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class GbaMonsterArtStandardTest {
    private fun monster(family: String, stage: Int): WildMonster = WildMonster(
        id="00000000-0000-4000-8000-000000000777",
        species=if(family==MonsterArtFamilies.LUNA) "精霊" else "ライゼル",
        element=if(family==MonsterArtFamilies.LUNA) "光" else "雷",
        stage=stage,maxHp=30,attack=8,artFamily=family
    )

    @Test fun featuredFamiliesMeet64pxPaletteAndMarginContract() {
        for(family in listOf(MonsterArtFamilies.RAIZEL, MonsterArtFamilies.LUNA)) {
            for(stage in 1..3) {
                val sprite=MonsterDesign.sprite(monster(family,stage))
                val audit=GbaMonsterArtStandard.audit(sprite)
                assertTrue("$family stage$stage: $audit", audit.compliant)
                assertEquals(64,sprite.width)
                assertEquals(64,sprite.height)
                assertTrue(sprite.opaqueCount()>100)
                assertTrue(audit.opaqueColors in 2..15)
                assertEquals(0,sprite.pixel(0,0))
                assertTrue(FullBodyGlyphProjector.project(sprite).any { it > 0 })
                assertTrue(MonsterAnimation.idle(sprite,monster(family,stage)).all {
                    GbaMonsterArtStandard.audit(it.sprite).totalColors <= 16
                })
                assertTrue(MonsterAnimation.attack(sprite,monster(family,stage)).all {
                    GbaMonsterArtStandard.audit(it.sprite).totalColors <= 16
                })
            }
        }
    }

    @Test fun quantizationLeavesOriginalIntactAndReservesTransparentSlot() {
        val colors=IntArray(4096) { index ->
            val x=index%64; val y=index/64
            if(x in 4..59 && y in 8..55)
                0xff000000.toInt() or (((index*97) and 255) shl 16) or
                    (((index*23) and 255) shl 8) or ((index*71) and 255)
            else 0
        }
        val original=PixelSprite.fromArgb(64,64,colors)
        assertTrue(GbaMonsterArtStandard.audit(original).totalColors>16)
        val clean=GbaMonsterArtStandard.canonical(original)
        assertTrue(GbaMonsterArtStandard.audit(clean).compliant)
        assertEquals(0,clean.pixel(0,0))
        assertEquals(colors[9*64+9],original.pixel(9,9))
        assertFalse(original.pixels().contentEquals(clean.pixels()))
    }

    @Test fun alreadyCompliantPixelGridIsNotRedrawn() {
        val art=MonsterDesign.sprite(monster(MonsterArtFamilies.RAIZEL,1))
        val clean=GbaMonsterArtStandard.canonical(art)
        assertTrue(art.pixels().contentEquals(clean.pixels()))
    }

    @Test fun auditReportsOpaqueBackgroundRatherThanPretendingItIsTransparent() {
        val full=PixelSprite.fromArgb(64,64,IntArray(4096) { 0xffaa7733.toInt() })
        val report=GbaMonsterArtStandard.audit(full)
        assertFalse(report.compliant)
        assertFalse(report.hasTransparency)
        assertTrue(report.croppedAtEdge)
    }
}
