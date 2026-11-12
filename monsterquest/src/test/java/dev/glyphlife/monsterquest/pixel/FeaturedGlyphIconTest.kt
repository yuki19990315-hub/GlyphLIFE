package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class FeaturedGlyphIconTest {
    private fun monster(family: String,stage: Int = 1) =
        WildMonster(id="demo-$family",species=if (family==MonsterArtFamilies.LUNA) "精霊" else "ライゼル",
            element=if (family==MonsterArtFamilies.LUNA) "光" else "雷",
            stage=stage,maxHp=40,attack=7,artFamily=family)

    @Test fun iconNeverLeavesThe137LedMask() {
        for (family in listOf(MonsterArtFamilies.LUNA, MonsterArtFamilies.RAIZEL)) {
            for (stage in 1..3) for (pose in 0..3) {
                val icon=FeaturedGlyphIcon.find(monster(family,stage),pose)
                assertNotNull(icon)
                assertEquals(169,icon!!.length)
                assertTrue(icon.count { it=='1' } in 16..110)
                icon.forEachIndexed { i,v -> if (v=='1')assertTrue(GlyphProjector.isPhysical(i)) }
            }
        }
    }
    @Test fun featuredSilhouettesAreDistinctAndPreservedAcrossStages() {
        val beast=monster(MonsterArtFamilies.RAIZEL)
        val luna=monster(MonsterArtFamilies.LUNA)
        assertNotEquals(FeaturedGlyphIcon.find(beast),FeaturedGlyphIcon.find(luna))
        assertNotEquals(FeaturedGlyphIcon.find(beast),FeaturedGlyphIcon.find(beast.copy(stage=2)))
        assertNotEquals(FeaturedGlyphIcon.find(luna),FeaturedGlyphIcon.find(luna.copy(stage=3)))
        assertNotEquals(FeaturedGlyphIcon.find(beast,0),FeaturedGlyphIcon.find(beast,2))
        assertEquals(FeaturedGlyphIcon.find(beast),FeaturedGlyphIcon.find(beast.copy(id="new-id")))
    }
    @Test fun customArmorOrOtherMonstersUseNormalProjection() {
        val beast=monster(MonsterArtFamilies.RAIZEL)
        assertNull(FeaturedGlyphIcon.find(beast.copy(armorParts=mapOf("HEAD" to "SPIKE"))))
        assertNull(FeaturedGlyphIcon.find(beast.copy(customArmorParts=mapOf("HEAD" to "test"))))
        assertNull(FeaturedGlyphIcon.find(beast.copy(element="火")))
        assertNull(FeaturedGlyphIcon.find(beast.copy(species="別の獣")))
        val normal=beast.copy(species="別の獣")
        val actual=MonsterDesign.sprite(normal)
        assertEquals(GlyphProjector.project(actual),GlyphProjector.projectFor(normal,actual))
    }
    @Test fun selectedAtelierIconMatchesActualSummon() {
        val proto=dev.glyphlife.monsterquest.model.MonsterPrototypeFactory.preview(
            "ライゼル",MonsterBodyType.BEAST,"雷",stage=1)
        val actual=dev.glyphlife.monsterquest.model.MonsterPrototypeFactory.summon(proto,2)
        assertEquals(proto.pixels,actual.pixels)
        assertEquals(FeaturedGlyphIcon.find(proto),proto.pixels)
    }
}
