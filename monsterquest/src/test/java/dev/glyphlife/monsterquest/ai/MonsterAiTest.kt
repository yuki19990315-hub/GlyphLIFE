package dev.glyphlife.monsterquest.ai

import org.junit.Assert.*
import org.junit.Test

class MonsterAiTest {
    private val pixels = "0".repeat(169)

    @Test fun validMonsterIsAcceptedAndStatsAreNotAIDefined() {
        val raw = """{"name":"アクアポム","element":"水","description":"ぷかぷか浮く水の精霊。",
            "pixels":"$pixels","hp":999999}"""
        val idea = MonsterAi.parse(raw)
        assertEquals("アクアポム", idea.name)
        val wild = idea.asWildMonster(2)
        assertEquals(32, wild.maxHp)
        assertEquals(6, wild.attack)
        assertEquals(169, wild.pixels.length)
    }

    @Test fun malformedPixelsAreRejected() {
        try {
            MonsterAi.parse("""{"name":"テスト","element":"火","description":"説明",
                "pixels":"1010"}""")
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun unknownElementIsRejected() {
        try {
            MonsterAi.parse("""{"name":"テスト","element":"土","description":"説明",
                "pixels":"$pixels"}""")
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
    }
}
