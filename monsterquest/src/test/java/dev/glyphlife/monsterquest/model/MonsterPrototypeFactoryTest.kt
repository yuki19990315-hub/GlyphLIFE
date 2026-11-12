package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterBodyType
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import org.junit.Assert.*
import org.junit.Test

class MonsterPrototypeFactoryTest {
    @Test fun everyBodyAndElementCanBePreviewedOffline() {
        for(type in MonsterBodyType.entries)for(element in MonsterPrototypeFactory.elements) {
            val monster=MonsterPrototypeFactory.preview("原種",type,element,1)
            assertEquals(type.name,monster.bodyType)
            assertEquals(element,monster.element)
            assertEquals(64,MonsterDesign.sprite(monster).width)
            assertEquals(169,monster.pixels.length)
            assertTrue(monster.pixels.any { it=='1' })
        }
    }

    @Test fun stagesDifferWithoutChangingNameOrType() {
        val type=MonsterBodyType.DRAGON
        val stage1=MonsterPrototypeFactory.preview("ルナザード",type,"雷",1)
        val stage3=MonsterPrototypeFactory.preview("ルナザード",type,"雷",3)
        assertEquals(stage1.species,stage3.species)
        assertEquals(stage1.bodyType,stage3.bodyType)
        assertNotEquals(
            MonsterDesign.sprite(stage1).pixels().toList(),
            MonsterDesign.sprite(stage3).pixels().toList()
        )
    }

    @Test fun summonStartsStageOneForFairGameplay() {
        val prototype=MonsterPrototypeFactory.preview("試作型",MonsterBodyType.MECHA,"闇",3)
        val actual=MonsterPrototypeFactory.summon(prototype,2)
        assertNotEquals(prototype.id,actual.id)
        assertEquals(1,actual.stage)
        assertEquals(prototype.bodyType,actual.bodyType)
        assertEquals(32,actual.maxHp)
        assertEquals(6,actual.attack)
        assertEquals(GlyphProjector.project(MonsterDesign.sprite(actual)),actual.pixels)
    }
    @Test fun chosenPreviewMatchesSummonedMonsterExactlyAtStageOne() {
        for(type in MonsterBodyType.entries) {
            for(variant in 0..31 step 3) {
                val preview = MonsterPrototypeFactory.preview(
                    "彗晶ヴァルゼン", type, "雷", 1, variant
                )
                val actual = MonsterPrototypeFactory.summon(preview, 3)
                assertNotEquals(preview.id, actual.id)
                assertEquals(preview.designSeed, actual.designSeed)
                assertTrue(
                    "Shape changed between preview and summon: $type / $variant",
                    MonsterDesign.sprite(preview).pixels()
                        .contentEquals(MonsterDesign.sprite(actual).pixels())
                )
                assertEquals(preview.pixels, actual.pixels)
            }
        }
    }

    @Test fun variantChoicesHaveDistinctOutlinesAndDeterministicPixels() {
        for(type in MonsterBodyType.entries) {
            val silhouettes = (0..31).map { variant ->
                val monster = MonsterPrototypeFactory.preview("星鎧", type, "光", 1, variant)
                val pixels = MonsterDesign.sprite(monster).pixels()
                pixels.joinToString("") { if(it ushr 24 > 0) "1" else "0" }
            }
            assertTrue("$type does not vary in silhouette", silhouettes.distinct().size >= 6)
            val sameA = MonsterPrototypeFactory.preview("星鎧", type, "光", 1, 4)
            val sameB = MonsterPrototypeFactory.preview("星鎧", type, "光", 1, 4)
            assertEquals(sameA, sameB)
        }
    }

    @Test fun designIdentitySurvivesAnIdChangeButLegacyIdentityStillUsesUuid() {
        val preview=MonsterPrototypeFactory.preview("ヴァル",MonsterBodyType.BEAST,"火",1,12)
        val transferred=preview.copy(id="new-instance-id")
        assertTrue(MonsterDesign.sprite(preview).pixels()
            .contentEquals(MonsterDesign.sprite(transferred).pixels()))
        val legacy=preview.copy(designSeed="")
        val secondLegacy=legacy.copy(id="other-instance")
        assertFalse(MonsterDesign.sprite(legacy).pixels()
            .contentEquals(MonsterDesign.sprite(secondLegacy).pixels()))
    }

    @Test fun invalidVariantIsRejected() {
        try {
            MonsterPrototypeFactory.preview("test",MonsterBodyType.BEAST,"火",1,64)
            fail("variant must be in 0..63")
        } catch (_: IllegalArgumentException) {}
    }

}
