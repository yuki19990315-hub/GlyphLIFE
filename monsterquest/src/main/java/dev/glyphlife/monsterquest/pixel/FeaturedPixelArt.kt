package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import dev.glyphlife.monsterquest.model.WildMonster
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/**
 * Original three-stage Raizel lightning-wolf designs built from an actual
 * image-first pixel-art workflow, not procedural circles and polygons.
 *
 * Each is a hand-reviewed, transparent 64x64 game sprite with <=15 colors.
 * Distinct full-body anatomy is maintained from small pup to guardian.
 * Pixel art is the game's immutable source; the 13x13 rear Glyph Matrix
 * receives a full-body luminance projection elsewhere, never a face icon.
 */
object FeaturedPixelArt {
    private val palettes = arrayOf(
        intArrayOf(0,0xFF000058L.toInt(),0xFF040613L.toInt(),0xFF0D1357L.toInt(),0xFF192874L.toInt(),0xFF333363L.toInt(),0xFF374A91L.toInt(),0xFF577BC1L.toInt(),0xFF5B5789L.toInt(),0xFF8EC9EAL.toInt(),0xFF9796B4L.toInt(),0xFFE0DEE5L.toInt(),0xFFFAF4D8L.toInt(),0xFFFEFDFEL.toInt()),
        intArrayOf(0,0xFF001C82L.toInt(),0xFF0E0D29L.toInt(),0xFF23295FL.toInt(),0xFF342F52L.toInt(),0xFF3F3C5FL.toInt(),0xFF45497BL.toInt(),0xFF4664AAL.toInt(),0xFF6D6780L.toInt(),0xFF888594L.toInt(),0xFF97A3BCL.toInt(),0xFF97CCE7L.toInt(),0xFFE2D6B2L.toInt(),0xFFFCFBE9L.toInt()),
        intArrayOf(0,0xFF000098L.toInt(),0xFF0035BBL.toInt(),0xFF111134L.toInt(),0xFF213EA4L.toInt(),0xFF3A3F7AL.toInt(),0xFF3C94EDL.toInt(),0xFF436DC9L.toInt(),0xFF5A4D6CL.toInt(),0xFF6DCDF9L.toInt(),0xFF8C7D72L.toInt(),0xFFAA9F85L.toInt(),0xFFBECED8L.toInt(),0xFFE4D282L.toInt(),0xFFF7F8CEL.toInt())
    )
    // Four-bit indexed palettes, zlib-compressed and base64 encoded to keep
    // the source lightweight while avoiding runtime networking/AI generation.
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
                val packed = bytes[i/2].toInt() and 255
                val color = if (i%2 == 0) packed ushr 4 else packed and 15
                check(color < palette.size) { "Invalid palette index" }
                palette[color]
            }
            // Source composition faced left; mirror to the design template's
            // right-facing three-quarter battle view.
            val facingRight = IntArray(4096) { i ->
                pixels[(i / 64) * 64 + (63 - i % 64)]
            }
            PixelSprite.fromArgb(64,64,facingRight)
        }
    }
    /** The same full-body source is used in encounters, the studio and the bestiary. */
    internal fun stageSprite(stage: Int): PixelSprite = frames[stage.coerceIn(1,3)-1]

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
