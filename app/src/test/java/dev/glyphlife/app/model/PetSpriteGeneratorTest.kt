package dev.glyphlife.app.model

import org.junit.Assert.*
import org.junit.Test

class PetSpriteGeneratorTest {
    @Test fun stageThresholdsAreDeterministic() {
        assertEquals("幼体", PetSpriteGenerator.stageName(1))
        assertEquals("幼体", PetSpriteGenerator.stageName(4))
        assertEquals("成長体", PetSpriteGenerator.stageName(5))
        assertEquals("進化体", PetSpriteGenerator.stageName(12))
    }

    @Test fun allSpeciesProduceThreeMaskedFrames() {
        val variants = listOf("スライム", "ドラゴン", "キツネ", "ロボット", "精霊")
        val firstFrames = variants.map { species ->
            val animation = PetSpriteGenerator.create(PetState(species = species))
            assertEquals(3, animation.frames.size)
            animation.frames.forEach { frame ->
                assertEquals(169, frame.pixels.size)
                assertTrue(frame.pixels.any { it })
                assertTrue(frame.pixels.withIndex().all { (i, on) -> !on || GlyphMask.isPhysical(i) })
            }
            animation.frames.first().pixels
        }
        assertEquals(variants.size, firstFrames.distinct().size)
    }

    @Test fun evolvedPetHasDifferentSpriteFromYoungPet() {
        val pet = PetState(species = "スライム")
        assertNotEquals(
            PetSpriteGenerator.create(pet).frames[0].pixels,
            PetSpriteGenerator.create(pet.copy(level = 12)).frames[0].pixels
        )
    }

    @Test fun blinkAndHopAreDifferent() {
        val a = PetSpriteGenerator.create(PetState()).frames
        assertNotEquals(a[0].pixels, a[1].pixels)
        assertNotEquals(a[1].pixels, a[2].pixels)
    }
}
