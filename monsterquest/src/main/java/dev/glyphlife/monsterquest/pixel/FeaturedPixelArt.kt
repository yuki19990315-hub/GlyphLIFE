package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import dev.glyphlife.monsterquest.model.WildMonster
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/**
 * Raizel artwork v3.3: original three-stage quadruped thunder beast.
 * Based on the newly approved three-stage Raizel design concept.
 * Real 64x64 alpha sprites, at most 15 opaque indexed colors each.
 * Full body (head, four legs, horns and charged tail) fits in each frame.
 * No runtime AI/network. Glyph insignias remain separate 13x13 assets.
 */
object FeaturedPixelArt {
    private val palettes = arrayOf(
        intArrayOf(0,0xFF1B1823L.toInt(),0xFF312E3AL.toInt(),0xFF4C415EL.toInt(),0xFF64567FL.toInt(),0xFF67554EL.toInt(),0xFF82728EL.toInt(),0xFF917661L.toInt(),0xFFAF9581L.toInt(),0xFFB39FB2L.toInt(),0xFFD1A74EL.toInt(),0xFFDBB589L.toInt(),0xFFDBC7BBL.toInt(),0xFFF7E5C4L.toInt(),0xFFFAE175L.toInt(),0xFFFAF5E6L.toInt()),
        intArrayOf(0,0xFF1E1923L.toInt(),0xFF312C39L.toInt(),0xFF4C415FL.toInt(),0xFF53433BL.toInt(),0xFF5F5279L.toInt(),0xFF7E675AL.toInt(),0xFF80708CL.toInt(),0xFFA7979EL.toInt(),0xFFAB8B71L.toInt(),0xFFC9B9B5L.toInt(),0xFFCBAB84L.toInt(),0xFFEED3A3L.toInt(),0xFFF6E3C0L.toInt(),0xFFF7F3E7L.toInt(),0xFFF8DF70L.toInt()),
        intArrayOf(0,0xFF231D2BL.toInt(),0xFF3A3139L.toInt(),0xFF473C56L.toInt(),0xFF5D5074L.toInt(),0xFF665041L.toInt(),0xFF836C69L.toInt(),0xFFA0898AL.toInt(),0xFFB18A5BL.toInt(),0xFFB5A6ADL.toInt(),0xFFCBAA89L.toInt(),0xFFDEC6B0L.toInt(),0xFFF4CE5CL.toInt(),0xFFF5E4C0L.toInt(),0xFFFAF7EEL.toInt(),0xFFFBE374L.toInt())
    )
    private val compressed = arrayOf(
        "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"
    )
    private val frames: List<PixelSprite> by lazy {
        compressed.indices.map { index ->
            val bytes = InflaterInputStream(ByteArrayInputStream(
                Base64.getDecoder().decode(compressed[index])
            )).use { it.readBytes() }
            check(bytes.size == 2048) { "Invalid 64px sprite data" }
            val palette = palettes[index]
            val pixels = IntArray(4096) { i ->
                val b = bytes[i / 2].toInt() and 0xff
                val colorIndex = if (i % 2 == 0) b ushr 4 else b and 15
                check(colorIndex < palette.size) { "Invalid palette index" }
                palette[colorIndex]
            }
            PixelSprite.fromArgb(64, 64, pixels)
        }
    }
    /** Same immutable source for atelier, summons, captured monsters and evolution. */
    internal fun stageSprite(stage: Int): PixelSprite = frames[stage.coerceIn(1, 3) - 1]
    fun find(monster: WildMonster): PixelSprite? {
        val explicit = monster.artFamily == MonsterArtFamilies.RAIZEL
        val legacy = monster.artFamily.isBlank() && monster.species == "ライゼル"
        return if ((explicit || legacy) && monster.species == "ライゼル" &&
            monster.element == "雷" &&
            (monster.bodyType.isBlank() || monster.bodyType == MonsterBodyType.BEAST.name)) {
            stageSprite(monster.stage)
        } else null
    }
}
