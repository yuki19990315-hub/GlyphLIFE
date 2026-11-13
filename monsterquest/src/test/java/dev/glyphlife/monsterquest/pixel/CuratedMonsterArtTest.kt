package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class CuratedMonsterArtTest {
    @Test fun allThirtyAuthoredSpritesAreRealGba64pxAndStayInsideBounds() {
        MonsterBodyType.entries.forEach { type ->
            (1..3).forEach { stage ->
                val monster=WildMonster(id="test-${type.name}-$stage",
                    species="デザイン試験",element="雷",
                    bodyType=type.name,stage=stage,maxHp=40,attack=10)
                val sprite=CuratedMonsterArt.find(monster)
                val audit=GbaMonsterArtStandard.audit(sprite)
                assertTrue("$type/$stage => $audit",audit.compliant)
                assertTrue(sprite.opaqueCount()>80)
                assertTrue(audit.totalColors<=16)
                assertEquals(64,sprite.width)
                assertEquals(64,sprite.height)
                assertTrue(FullBodyGlyphProjector.project(sprite).any { it>0 })
            }
        }
    }

    @Test fun silhouettesChangeAcrossAllThreeStagesAndTypes() {
        fun mask(m:WildMonster):String {
            return CuratedMonsterArt.find(m).pixels().joinToString("") {
                if(it ushr 24==0)"." else "#"
            }
        }
        val signatures=MonsterBodyType.entries.map { type ->
            (1..3).map { s ->
                mask(WildMonster(id="v-$s",species="姿",element="闇",
                    maxHp=20,attack=6,bodyType=type.name,stage=s))
            }
        }
        signatures.forEach { assertEquals(3,it.distinct().size) }
        assertTrue(signatures.map { it[0] }.distinct().size>=7)
    }

    @Test fun gameAlwaysUsesAuthoredAssetsRatherThanOldEllipseFallback() {
        val base=WildMonster(id="monster",species="フレア",element="火",
            maxHp=50,attack=8,bodyType="DRAGON",stage=2)
        assertTrue(MonsterDesign.sprite(base).pixels().contentEquals(
            CuratedMonsterArt.find(base).pixels()))
        val eye=base.copy(species="深淵の魔眼",bodyType="SPIRIT")
        val spirit=base.copy(species="標準精霊",bodyType="SPIRIT")
        assertFalse(MonsterDesign.sprite(eye).pixels().contentEquals(
            MonsterDesign.sprite(spirit).pixels()))
    }
}
