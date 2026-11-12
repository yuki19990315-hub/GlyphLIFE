package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.FusionRules
import dev.glyphlife.monsterquest.model.MonsterPrototypeFactory
import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class MonsterArmorTest {
    private fun wild(type:MonsterBodyType=MonsterBodyType.BEAST,stage:Int=3) =
        MonsterPrototypeFactory.preview("装甲獣",type,"雷",stage,5)

    @Test fun allSixSlotsAndFourStylesHaveSafeNativeSprites() {
        for (body in MonsterBodyType.entries) for (slot in ArmorSlot.entries) {
            for (style in ArmorStyle.entries) {
                val creature=wild(body).copy(armorParts=mapOf(slot.name to style.name))
                val art=MonsterDesign.sprite(creature)
                assertEquals(64,art.width)
                assertEquals(64,art.height)
                assertTrue(art.opaqueCount()>100)
                assertTrue("${body}/${slot}/${style} too many colors",art.colors().size<=16)
                val glyph=GlyphProjector.project(art)
                assertEquals(169,glyph.length)
                assertTrue(glyph.withIndex().all { (i,c)->
                    c=='0' || (c=='1' && GlyphProjector.isPhysical(i))
                })
            }
        }
    }

    @Test fun armorNeverChangesLegacySprites() {
        for (stage in 1..3) {
            val creature=wild(stage=stage)
            val unarmored=MonsterDesign.sprite(creature)
            assertTrue(MonsterArmor.compose(unarmored,creature).pixels()
                .contentEquals(unarmored.pixels()))
            val empty=creature.copy(armorParts=emptyMap())
            assertTrue(MonsterDesign.sprite(empty).pixels()
                .contentEquals(unarmored.pixels()))
        }
    }

    @Test fun armorAddsRealPixelsAndSurvivesSummon() {
        val plan=MonsterArmor.suggestion("雷",MonsterBodyType.BEAST)
        val model=wild(stage=1).copy(armorParts=plan)
        val plain=wild(stage=1)
        assertFalse(MonsterDesign.sprite(model).pixels()
            .contentEquals(MonsterDesign.sprite(plain).pixels()))
        val preview=MonsterPrototypeFactory.preview(
            "試験装備",MonsterBodyType.BEAST,"雷",1,7,plan)
        val summon=MonsterPrototypeFactory.summon(preview,2)
        assertEquals(preview.armorParts,summon.armorParts)
        assertEquals(preview.pixels,summon.pixels)
        assertTrue(MonsterDesign.sprite(preview).pixels()
            .contentEquals(MonsterDesign.sprite(summon).pixels()))
    }

    @Test fun lockedStageItemsWaitForEvolution() {
        val base=wild(stage=1)
        val all=MonsterArmor.suggestion("火",MonsterBodyType.BEAST)
        val early=all.filterKeys { ArmorSlot.valueOf(it).minStage==1 }
        assertTrue(MonsterDesign.sprite(base.copy(armorParts=all)).pixels()
            .contentEquals(MonsterDesign.sprite(base.copy(armorParts=early)).pixels()))
        val late=base.copy(stage=3)
        assertFalse(MonsterDesign.sprite(late.copy(armorParts=all)).pixels()
            .contentEquals(MonsterDesign.sprite(late.copy(armorParts=early)).pixels()))
    }

    @Test fun fusionInheritsSlotSelectionWithoutModifyingParents() {
        val first=wild().copy(
            id="00000000-0000-4000-8000-000000000111",
            armorParts=mapOf("HEAD" to "SPIKE","CORE" to "CRYSTAL",
                "SHOULDER" to "PLATE","WING" to "RUNE"))
        val second=wild(MonsterBodyType.DRAGON).copy(
            id="00000000-0000-4000-8000-000000000222",
            armorParts=mapOf("TAIL" to "SPIKE","BACK" to "RUNE",
                "WING" to "CRYSTAL","HEAD" to "RUNE"))
        val fused=FusionRules.preview(first,second)
        assertEquals("SPIKE",fused.armorParts["HEAD"])
        assertEquals("CRYSTAL",fused.armorParts["CORE"])
        assertEquals("PLATE",fused.armorParts["SHOULDER"])
        assertEquals("SPIKE",fused.armorParts["TAIL"])
        assertEquals("RUNE",fused.armorParts["BACK"])
        assertEquals("CRYSTAL",fused.armorParts["WING"])
        assertTrue(fused.armorParts.size==6)
        assertEquals("RUNE",second.armorParts["HEAD"])
        val result=FusionRules.fuse(dev.glyphlife.monsterquest.model.QuestState(
            captures=listOf(first,second)), first.id,second.id).captures.single()
        assertEquals(fused.armorParts,result.armorParts)
        assertEquals(fused.pixels,result.pixels)
        assertTrue(MonsterDesign.sprite(fused).pixels()
            .contentEquals(MonsterDesign.sprite(result).pixels()))
    }

    @Test fun unknownSlotsAndStylesAreRejected() {
        for(plan in listOf(mapOf("BAD" to "PLATE"),
            mapOf("HEAD" to "BOMB"),mapOf("HEAD" to "SPIKE","BAD" to "PLATE"))) {
            try {
                MonsterArmor.validated(plan)
                fail("expected rejection")
            } catch (_:IllegalArgumentException) {}
        }
    }

    @Test fun featured15ColorArtStaysWithinPaletteBudget() {
        val featured=WildMonster(
            id="00000000-0000-4000-8000-000000000111",
            species="ライゼル",element="雷",maxHp=40,attack=8,stage=3)
        val base=MonsterDesign.sprite(featured)
        val armored=MonsterDesign.sprite(featured.copy(
            armorParts=MonsterArmor.suggestion("雷",MonsterBodyType.BEAST)))
        assertTrue(base.colors().size<=15)
        assertTrue(armored.colors().size<=16)
        assertFalse(base.pixels().contentEquals(armored.pixels()))
    }
}
