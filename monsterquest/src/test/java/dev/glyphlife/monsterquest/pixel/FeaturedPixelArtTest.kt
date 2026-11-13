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

    @Test fun redesignedStagesHaveIndependentFullBodySilhouettes() {
        val silhouetteKeys = mutableSetOf<String>()
        val opaqueCounts = mutableListOf<Int>()
        for (stage in 1..3) {
            val sprite = MonsterDesign.sprite(wild(stage))
            val pixels = sprite.pixels()
            val coords = pixels.indices.filter { index -> (pixels[index] ushr 24) != 0 }
            val xs = coords.map { index -> index % 64 }
            val ys = coords.map { index -> index / 64 }
            // Juvenile and final form intentionally grow in the same 64px canvas.
            val minWidth = when(stage) { 1 -> 35; 2 -> 45; else -> 55 }
            val minHeight = when(stage) { 1 -> 28; 2 -> 35; else -> 44 }
            assertTrue("Stage $stage is too narrow", xs.max() - xs.min() >= minWidth)
            assertTrue("Stage $stage is too flat", ys.max() - ys.min() >= minHeight)
            assertTrue("Stage $stage cropped at edge", xs.min() >= 1 && xs.max() <= 62)
            assertTrue("Stage $stage cropped at edge", ys.min() >= 1 && ys.max() <= 62)
            opaqueCounts.add(coords.size)
            val signature = sprite.pixels().joinToString("") { if (it ushr 24 == 0) "0" else "1" }
            silhouetteKeys.add(signature)
        }
        assertEquals(3, silhouetteKeys.size)
        assertTrue("Stages should vary in body mass", opaqueCounts.distinct().size >= 2)
    }

    @Test fun redesignedArtRemainsStableForLegacyNameAndExplicitIdentity() {
        for (stage in 1..3) {
            val legacy = wild(stage)
            val explicit = legacy.copy(artFamily = dev.glyphlife.monsterquest.model.MonsterArtFamilies.RAIZEL)
            assertTrue(MonsterDesign.sprite(legacy).pixels()
                .contentEquals(MonsterDesign.sprite(explicit).pixels()))
        }
    }

    @Test fun authoredSpriteIsNotUsedForOtherMonstersOrElements() {
        assertNull(FeaturedPixelArt.find(wild(1).copy(species = "別の獣")))
        assertNull(FeaturedPixelArt.find(wild(1).copy(element = "火")))
        assertNull(FeaturedPixelArt.find(wild(1).copy(bodyType = "DRAGON")))
        assertEquals(MonsterBodyType.BEAST, MonsterDesign.classify("ライゼル"))
    }
}
