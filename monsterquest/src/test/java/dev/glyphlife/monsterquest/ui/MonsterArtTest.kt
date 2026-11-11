package dev.glyphlife.monsterquest.ui

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class MonsterArtTest {
    @Test fun fallbackAlways169PixelsAndDeterministic() {
        val species = listOf("モコ", "フレア", "ギア", "月蝕の竜", "古代の歯車王")
        for (name in species) {
            val sprite = MonsterArt.generate(name, "火")
            assertEquals(169, sprite.length)
            assertTrue(sprite.all { it == '0' || it == '1' })
            assertTrue(sprite.count { it == '1' } >= 10)
            assertEquals(sprite, MonsterArt.generate(name, "火"))
        }
    }

    @Test fun bossesGetDistinctSilhouette() {
        val ordinary = MonsterArt.generate("月蝕の竜", "闇", false)
        val boss = MonsterArt.generate("月蝕の竜", "闇", true)
        assertNotEquals(ordinary, boss)
    }

    @Test fun importedAiSpriteIsPreserved() {
        val portrait = "1".repeat(169)
        val wild = WildMonster("test","モコ","水",20,4,pixels=portrait)
        assertEquals(portrait, MonsterArt.forWild(wild))
    }

    @Test fun malformedAiSpriteFallsBack() {
        val wild = WildMonster("test","モコ","水",20,4,pixels="bad")
        assertEquals(169, MonsterArt.forWild(wild).length)
        assertNotEquals("bad", MonsterArt.forWild(wild))
    }
}
