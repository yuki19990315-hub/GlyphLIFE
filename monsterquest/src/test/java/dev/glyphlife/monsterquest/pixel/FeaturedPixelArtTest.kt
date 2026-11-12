package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class FeaturedPixelArtTest {
    private fun wild(stage: Int) = WildMonster(
        id = "00000000-0000-4000-8000-000000000111",
        species = "ライゼル",
        element = "雷",
        maxHp = 40,
        attack = 8,
        stage = stage
    )

    @Test fun threeOriginalStagesAreNative64PaletteArt() {
        val stages = (1..3).map { stage ->
            val sprite = MonsterDesign.sprite(wild(stage))
            assertEquals(64, sprite.width)
            assertEquals(64, sprite.height)
            assertEquals(4096, sprite.pixels().size)
            assertTrue(sprite.opaqueCount() in 250..3500)
            assertTrue(sprite.colors().size in 6..16)
            assertEquals(0, sprite.pixel(0, 0))
            sprite.pixels().toList()
        }
        assertEquals(3, stages.distinct().size)
    }

    @Test fun identityAndGlyphProjectionAreStable() {
        for (stage in 1..3) {
            val original = MonsterDesign.sprite(wild(stage))
            val differentId = wild(stage).copy(
                id = "00000000-0000-4000-8000-000000000555"
            )
            assertTrue(original.pixels().contentEquals(MonsterDesign.sprite(differentId).pixels()))
            val glyph = GlyphProjector.project(original)
            assertEquals(169, glyph.length)
            assertTrue(glyph.count { it == '1' } in 10..137)
            assertTrue(glyph.withIndex().all { (i, bit) ->
                bit != '1' || GlyphProjector.isPhysical(i)
            })
        }
    }

    @Test fun authoredSpriteIsNotUsedForOtherMonstersOrElements() {
        assertNull(FeaturedPixelArt.find(wild(1).copy(species = "別の獣")))
        assertNull(FeaturedPixelArt.find(wild(1).copy(element = "火")))
        assertEquals(MonsterBodyType.BEAST, MonsterDesign.classify("ライゼル"))
    }
}
