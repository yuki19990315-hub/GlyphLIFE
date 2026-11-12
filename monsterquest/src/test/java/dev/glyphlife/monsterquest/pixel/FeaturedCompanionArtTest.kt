package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.Companion
import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.model.gameArtFamily
import org.junit.Assert.*
import org.junit.Test

class FeaturedCompanionArtTest {
    private fun luna(stage: Int = 1): WildMonster = WildMonster(
        id = "00000000-0000-4000-8000-000000000111",
        species = "精霊", element = "光", maxHp = 100, attack = 10,
        stage = stage, artFamily = MonsterArtFamilies.LUNA
    )

    @Test fun onlyUnlinkedBuiltinLunaGetsFeaturedArt() {
        assertEquals(MonsterArtFamilies.LUNA, Companion().gameArtFamily())
        assertEquals("", Companion(importedFromGlyphLife = true).gameArtFamily())
        assertEquals("", Companion(name = "自作ルナ").gameArtFamily())
        assertEquals("", Companion(element = "水").gameArtFamily())
        assertNull(FeaturedCompanionArt.find(luna().copy(artFamily = "")))
        assertNull(FeaturedCompanionArt.find(luna().copy(element = "雷")))
        assertNull(FeaturedCompanionArt.find(luna().copy(species = "別種")))
    }

    @Test fun nativeFeaturedSpriteIsStableAtAllStages() {
        val stageImages = (1..3).map { stage ->
            val original = MonsterDesign.sprite(luna(stage))
            val anotherId = MonsterDesign.sprite(luna(stage).copy(id = "other-instance"))
            assertTrue(original.pixels().contentEquals(anotherId.pixels()))
            assertEquals(64, original.width)
            assertEquals(64, original.height)
            assertTrue(original.opaqueCount() in 250..3500)
            assertTrue(original.colors().size in 6..16)
            assertEquals(0, original.pixel(0, 0))
            val glyph = GlyphProjector.project(original)
            assertEquals(169, glyph.length)
            assertTrue(glyph.withIndex().all { (i, bit) ->
                bit != '1' || GlyphProjector.isPhysical(i)
            })
            original.pixels().toList()
        }
        assertEquals(3, stageImages.distinct().size)
    }

    @Test fun moonlitStyleSharesQualitySilhouetteNotExactRaizelColors() {
        val star = MonsterDesign.sprite(luna())
        val thunder = MonsterDesign.sprite(WildMonster(
            id = "another", species = "ライゼル", element = "雷",
            maxHp = 100, attack = 10, stage = 1,
            artFamily = MonsterArtFamilies.RAIZEL
        ))
        assertEquals(star.opaqueCount(), thunder.opaqueCount())
        assertNotEquals(star.pixels().toList(), thunder.pixels().toList())
    }
}
