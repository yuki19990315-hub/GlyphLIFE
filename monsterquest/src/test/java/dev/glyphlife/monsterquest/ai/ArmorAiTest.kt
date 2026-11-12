package dev.glyphlife.monsterquest.ai

import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.MonsterArmor
import org.junit.Assert.*
import org.junit.Test

class ArmorAiTest {
    private val wild=WildMonster(
        id="00000000-0000-4000-8000-000000000001",
        species="原種",element="雷",maxHp=35,attack=7,bodyType="BEAST")

    @Test fun acceptsOnlySixSafeParts() {
        val json="""{"HEAD":"SPIKE","CORE":"CRYSTAL","TAIL":"PLATE",
            "SHOULDER":"PLATE","BACK":"RUNE","WING":"CRYSTAL"}"""
        val result=ArmorAi.parse(json)
        assertEquals(6,result.size)
        assertTrue(MonsterArmor.isValid(result))
    }

    @Test fun rejectsUnknownStyleAndExtraKeys() {
        val examples=listOf(
            """{"HEAD":"PISTOL","CORE":"CRYSTAL","TAIL":"PLATE",
                "SHOULDER":"PLATE","BACK":"RUNE","WING":"CRYSTAL"}""",
            """{"HEAD":"SPIKE","CORE":"CRYSTAL","TAIL":"PLATE",
                "SHOULDER":"PLATE","BACK":"RUNE","WING":"CRYSTAL",
                "attack":"99999"}"""
        )
        examples.forEach { bad ->
            try { ArmorAi.parse(bad); fail("invalid AI fields") }
            catch (_:IllegalArgumentException) {}
        }
    }

    @Test fun promptDoesNotAskForAiGameStatsOrCopyOfOtherCreatures() {
        val prompt=ArmorAi.prompt(wild)
        assertTrue(prompt.contains("HEAD"))
        assertTrue(prompt.contains("SPIKE"))
        assertTrue(prompt.contains("64 by 64"))
        assertTrue(prompt.contains("fixed attachment slots"))
        assertTrue(prompt.contains("Do not output stats"))
    }
}
