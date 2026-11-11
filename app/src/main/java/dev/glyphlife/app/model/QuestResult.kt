package dev.glyphlife.app.model

import org.json.JSONObject
import java.util.UUID

/**
 * Single-player/offline result exchange. Authenticity is NOT guaranteed;
 * add signing / trust checks before competitive or online features.
 */
data class QuestResult(
    val eventId: String,
    val petId: String,
    val experience: Int,
    val happiness: Int
) {
    companion object {
        fun parse(json: String): QuestResult {
            require(json.length <= 65_536) { "Result is too large" }
            val obj = JSONObject(json)
            require(obj.getInt("schemaVersion") == 1)
            require(obj.getString("source") == "monster-quest")
            val result = QuestResult(
                obj.getString("eventId"),
                obj.getString("petId"),
                obj.getInt("experience"),
                obj.getInt("happiness")
            )
            UUID.fromString(result.eventId)
            UUID.fromString(result.petId)
            require(result.experience in 0..100)
            require(result.happiness in -20..20)
            return result
        }
    }

    fun apply(pet: PetState): PetState {
        require(pet.id == petId) { "This result belongs to a different pet" }
        val total = pet.experience + experience
        return pet.copy(
            experience = total % 100,
            level = pet.level + total / 100,
            happiness = (pet.happiness + happiness).coerceIn(0, 100)
        )
    }
}
