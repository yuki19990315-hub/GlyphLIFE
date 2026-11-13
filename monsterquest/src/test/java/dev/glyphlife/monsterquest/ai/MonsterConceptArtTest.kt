package dev.glyphlife.monsterquest.ai

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class MonsterConceptArtTest {
    private val dragon = WildMonster(
        id = "d1", species = "深淵機甲竜", element = "雷",
        maxHp = 40, attack = 8, description = "古代の機械竜"
    )

    @Test fun allStylesPreserveGbaArtworkContract() {
        for (style in ConceptStyle.entries) {
            val prompt = MonsterConceptArt.prompt(dragon, style)
            assertTrue(prompt.contains("full-body"))
            assertTrue(prompt.contains("64x64"))
            assertTrue(prompt.contains("15 opaque"))
            assertTrue(prompt.contains("transparent"))
            assertTrue(prompt.contains("RIGHT-FACING"))
            assertTrue(prompt.contains("Stage 1"))
            assertTrue(prompt.contains("NOT a three-panel"))
            assertTrue(prompt.contains(style.direction))
            assertTrue(prompt.contains("深淵機甲竜"))
        }
    }

    @Test fun parserRequiresAnImagePart() {
        try {
            MonsterConceptArt.parseImagePayload(
                """{"candidates":[{"content":{"parts":[{"text":"No image"}]}}]}"""
            )
            fail("Text-only response must not be treated as image")
        } catch (_: IllegalStateException) {}
    }

    @Test fun parserRejectsUnsupportedMime() {
        try {
            MonsterConceptArt.parseImagePayload(
                """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"application/octet-stream","data":"AAAA"}}]}}]}"""
            )
            fail("Non-image responses must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun imageModelAndPromptAreSeparateFromGlyphBinary() {
        assertEquals("gemini-3.1-flash-image", MonsterConceptArt.MODEL)
        assertFalse(MonsterConceptArt.prompt(dragon, ConceptStyle.MECHA).contains("169 binary"))
    }
}
