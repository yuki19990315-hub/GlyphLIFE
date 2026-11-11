package dev.glyphlife.app.model

import java.util.UUID

/** The canonical pet state. Never trust LLM output as the source of truth. */
data class PetState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "ピコ",
    val species: String = "スライム",
    val element: String = "水",
    val personality: String = "好奇心旺盛",
    val level: Int = 1,
    val experience: Int = 0,
    val happiness: Int = 60,
    val energy: Int = 80,
) {
    fun play(): PetState = gainExperience(10).copy(
        happiness = (happiness + 8).coerceIn(0, 100),
        energy = (energy - 6).coerceIn(0, 100),
    )

    fun feed(): PetState = copy(
        happiness = (happiness + 3).coerceIn(0, 100),
        energy = (energy + 12).coerceIn(0, 100),
    )

    fun rest(): PetState = copy(energy = (energy + 25).coerceIn(0, 100))

    private fun gainExperience(points: Int): PetState {
        val total = experience + points
        return copy(level = level + total / 100, experience = total % 100)
    }
}

object PixelArt {
    const val WIDTH = 13
    const val HEIGHT = 13
    const val COUNT = WIDTH * HEIGHT
    fun empty(): List<Boolean> = List(COUNT) { false }
    fun defaultPet(): List<Boolean> {
        val rows = listOf(
            "0000000000000",
            "0000000000000",
            "0000110011000",
            "0001101101100",
            "0011111111100",
            "0011111111100",
            "0011011101100",
            "0011111111100",
            "0011111111100",
            "0011100011100",
            "0011111111100",
            "0001111111000",
            "0000000000000",
        )
        require(rows.size == HEIGHT && rows.all { it.length == WIDTH })
        return rows.flatMap { row -> row.map { it == '1' } }
    }
}
