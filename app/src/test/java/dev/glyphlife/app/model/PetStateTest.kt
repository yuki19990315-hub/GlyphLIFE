package dev.glyphlife.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetStateTest {
    @Test fun `play increases XP and costs energy`() {
        val start = PetState(energy = 50, happiness = 50, experience = 95)
        val result = start.play()
        assertEquals(2, result.level)
        assertEquals(5, result.experience)
        assertEquals(44, result.energy)
        assertEquals(58, result.happiness)
    }

    @Test fun `all stats are bounded`() {
        assertEquals(100, PetState(happiness = 100, energy = 100).feed().energy)
        assertEquals(100, PetState(happiness = 100, energy = 100).feed().happiness)
        assertEquals(0, PetState(energy = 0).play().energy)
    }

    @Test fun `sprite has 169 pixels`() {
        assertEquals(169, PixelArt.defaultPet().size)
        assertTrue(PixelArt.defaultPet().any { it })
    }
}
