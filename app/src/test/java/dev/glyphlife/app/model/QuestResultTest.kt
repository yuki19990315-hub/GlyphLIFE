package dev.glyphlife.app.model

import org.junit.Assert.*
import org.junit.Test

class QuestResultTest {
    private val pet = PetState(id = "467437c1-7e85-4755-aeca-b23b09f6fd12",
        level = 2, experience = 90, happiness = 95)
    private val eventId = "110283ab-452c-43e0-8100-8a8160f5f30d"

    @Test fun questResultAddsExperienceAndClampsHappiness() {
        val result = QuestResult(eventId, pet.id, 20, 10)
        val updated = result.apply(pet)
        assertEquals(3, updated.level)
        assertEquals(10, updated.experience)
        assertEquals(100, updated.happiness)
    }

    @Test fun resultCannotApplyToAnotherPet() {
        try {
            QuestResult(eventId, "be4adaea-2ed3-4d4d-a54d-ec2e495d3ce7", 20, 2).apply(pet)
            fail("Should reject mismatched pet ID")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun invalidRewardIsRejected() {
        val raw = """{"schemaVersion":1,"source":"monster-quest",
            "eventId":"$eventId","petId":"${pet.id}",
            "experience":9999,"happiness":0}"""
        try {
            QuestResult.parse(raw)
            fail("Should reject invalid reward")
        } catch (_: IllegalArgumentException) {}
    }
}
