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
}
