package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/**
 * Original AI-assisted pixel-art pilot of one three-stage monster family.
 * Real 64x64, palette-indexed artwork: at most 15 opaque colors plus alpha.
 * Packed 4-bit pixels are zlib compressed; no runtime network or image API.
 * This authored asset is separate from the 13x13 Glyph projection.
 */
object FeaturedPixelArt {
    private val palettes = arrayOf(
        intArrayOf(0,0xFFE6B54BL.toInt(),0xFFAC8E72L.toInt(),0xFF6F6368L.toInt(),0xFF5B5766L.toInt(),0xFFF9E88CL.toInt(),0xFF39354CL.toInt(),0xFFE9D091L.toInt(),0xFF564F60L.toInt(),0xFFEFECF1L.toInt(),0xFF1C192BL.toInt(),0xFF202131L.toInt(),0xFF4A496CL.toInt(),0xFF827E91L.toInt(),0xFFBAADA9L.toInt()),
        intArrayOf(0,0xFFBA934FL.toInt(),0xFFF3C844L.toInt(),0xFFC7AC79L.toInt(),0xFFF4DD84L.toInt(),0xFF6F5D46L.toInt(),0xFFF8EEB5L.toInt(),0xFF7A7075L.toInt(),0xFF574E45L.toInt(),0xFFD7C8B3L.toInt(),0xFF494962L.toInt(),0xFF31303FL.toInt(),0xFF958B90L.toInt(),0xFF5C5865L.toInt(),0xFF1C1A29L.toInt()),
        intArrayOf(0,0xFFBF9D5EL.toInt(),0xFFD6C7A5L.toInt(),0xFFF0EBCEL.toInt(),0xFFC2B3A2L.toInt(),0xFF8A7561L.toInt(),0xFF9894A6L.toInt(),0xFFE4DBBFL.toInt(),0xFF6E5751L.toInt(),0xFF56556CL.toInt(),0xFF6E6E85L.toInt(),0xFF4A4D71L.toInt(),0xFF312D38L.toInt(),0xFF43435FL.toInt(),0xFF141C25L.toInt(),0xFF241B21L.toInt())
    )
    private val compressed = arrayOf(
        "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"
    )

    private val frames: List<PixelSprite> by lazy {
        compressed.indices.map { index ->
            val palette = palettes[index]
            val bytes = InflaterInputStream(ByteArrayInputStream(
                Base64.getDecoder().decode(compressed[index])
            )).use { it.readBytes() }
            check(bytes.size == 2048) { "Invalid featured sprite data" }
            val colors = IntArray(4096) { i ->
                val packed = bytes[i / 2].toInt() and 255
                val slot = if (i % 2 == 0) packed ushr 4 else packed and 15
                palette[slot]
            }
            PixelSprite.fromArgb(64, 64, colors)
        }
    }

    /** Authoritative 64px original sprite for a selected evolution stage. */
    internal fun stageSprite(stage: Int): PixelSprite = frames[stage.coerceIn(1, 3) - 1]

    fun find(monster: WildMonster): PixelSprite? {
        val explicitlySelected = monster.artFamily == MonsterArtFamilies.RAIZEL
        val legacyRaizel = monster.artFamily.isBlank() && monster.species == "ライゼル"
        return if ((explicitlySelected || legacyRaizel) && monster.element == "雷" &&
            (monster.bodyType.isBlank() || monster.bodyType == MonsterBodyType.BEAST.name)) {
            stageSprite(monster.stage)
        } else null
    }
}
