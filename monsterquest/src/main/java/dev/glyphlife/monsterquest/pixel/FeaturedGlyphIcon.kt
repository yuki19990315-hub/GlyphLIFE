package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import dev.glyphlife.monsterquest.model.WildMonster

/**
 * The phone's 64x64 art remains the authoritative full-body design.
 *
 * A 13x13 (137 physical LED) panel cannot display the whole animal with
 * recognizable armor, face and legs. For authored families, use an intentionally
 * simplified, bespoke face insignia instead of cropping the full-body silhouette.
 * No other monster or user supplied art is altered.
 */
object FeaturedGlyphIcon {
    private val beast = listOf(
        "0000000000000",
        "0010000000100",
        "0011000001100",
        "0111100011110",
        "1111110111111",
        "0111111111110",
        "0011011101100",
        "0011111111100",
        "0001111111000",
        "0000111110000",
        "0000011100000",
        "0000001000000",
        "0000000000000"
    )
    private val moon = listOf(
        "0000111110000",
        "0001000001000",
        "0010000000100",
        "0110000000110",
        "0000111110000",
        "0001111111000",
        "0011100011100",
        "0011111111100",
        "0001111111000",
        "0000111110000",
        "0000011100000",
        "0000001000000",
        "0000000000000"
    )

    fun find(monster: WildMonster, pose: Int = 0): String? {
        // Armor/imported parts deserve a fresh raster projection instead.
        if (monster.armorParts.isNotEmpty() || monster.customArmorParts.isNotEmpty())
            return null
        val isLuna = monster.artFamily == MonsterArtFamilies.LUNA &&
            monster.species == "精霊" && monster.element == "光"
        val isRaizel = (monster.artFamily == MonsterArtFamilies.RAIZEL ||
            (monster.artFamily.isBlank() && monster.species == "ライゼル")) &&
            monster.species == "ライゼル" && monster.element == "雷" &&
            (monster.bodyType.isBlank() || monster.bodyType == MonsterBodyType.BEAST.name)
        if (!isLuna && !isRaizel) return null

        val pixels = (if (isLuna) moon else beast).joinToString("").toCharArray()
        // Evolution adds a crown ridge/crest, preserving the recognizable head.
        if (monster.stage >= 2) pixels[0 * 13 + 6] = '1'
        if (monster.stage >= 3) {
            pixels[1 * 13 + 5] = '1'
            pixels[1 * 13 + 7] = '1'
        }
        // Idle pose 2: blink by closing the two eyes for a single frame.
        if (pose == 2) {
            if (isLuna) {
                pixels[6 * 13 + 5] = '1'
                pixels[6 * 13 + 7] = '1'
            } else {
                pixels[6 * 13 + 4] = '1'
                pixels[6 * 13 + 8] = '1'
            }
        }
        for (i in pixels.indices) {
            if (!GlyphProjector.isPhysical(i)) pixels[i] = '0'
        }
        return String(pixels)
    }
}
