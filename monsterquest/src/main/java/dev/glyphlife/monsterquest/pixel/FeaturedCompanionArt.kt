package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import dev.glyphlife.monsterquest.model.WildMonster
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/**
 * Luna is no longer Raizel with different colors.
 *
 * Real three-stage original SPIRIT artwork:
 * 1. Moon wisp with antennae and two-color face.
 * 2. Floating multi-armed fairy spirit.
 * 3. Celestial guardian with feathered aura and lunar halo.
 *
 * The demo companion's species/element gate prevents imported custom pets
 * from being overwritten. Native 64px indexed sprites are stored offline.
 */
object FeaturedCompanionArt {
    private val palettes = arrayOf(
        intArrayOf(0,0xFF000000L.toInt(),0xFF000024L.toInt(),0xFF1A1122L.toInt(),0xFF362F43L.toInt(),0xFF525C7CL.toInt(),0xFF80D6E9L.toInt(),0xFF896FA0L.toInt(),0xFF9BACD4L.toInt(),0xFFA990C9L.toInt(),0xFFBDD8F0L.toInt(),0xFFC4B4E4L.toInt(),0xFFDAC0E3L.toInt(),0xFFE0D9F0L.toInt(),0xFFECEAF5L.toInt(),0xFFF9FAF8L.toInt()),
        intArrayOf(0,0xFF000000L.toInt(),0xFF000028L.toInt(),0xFF06020EL.toInt(),0xFF15172EL.toInt(),0xFF301740L.toInt(),0xFF393051L.toInt(),0xFF504E79L.toInt(),0xFF637CA8L.toInt(),0xFF81CCE4L.toInt(),0xFF9479B6L.toInt(),0xFFABA1CCL.toInt(),0xFFC2D6EAL.toInt(),0xFFE4D4EDL.toInt(),0xFFF1F8F3L.toInt()),
        intArrayOf(0,0xFF000000L.toInt(),0xFF00001BL.toInt(),0xFF040000L.toInt(),0xFF04070BL.toInt(),0xFF201F25L.toInt(),0xFF4B3333L.toInt(),0xFF605657L.toInt(),0xFF7C7A86L.toInt(),0xFF83A3BCL.toInt(),0xFFB9A395L.toInt(),0xFFBBD7E6L.toInt(),0xFFC8BFBFL.toInt(),0xFFDEDDE3L.toInt(),0xFFEEE3D0L.toInt(),0xFFF9F8E5L.toInt())
    )
    private val compressed = arrayOf(
        "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"
    )
    private val sprites: List<PixelSprite> by lazy {
        compressed.indices.map { index ->
            val bytes = InflaterInputStream(ByteArrayInputStream(
                Base64.getDecoder().decode(compressed[index])
            )).use { it.readBytes() }
            check(bytes.size == 2048)
            val palette = palettes[index]
            PixelSprite.fromArgb(64,64,IntArray(4096) { i ->
                val packed = bytes[i/2].toInt() and 255
                val color = if (i%2==0) packed ushr 4 else packed and 15
                check(color < palette.size)
                palette[color]
            })
        }
    }
    fun find(monster: WildMonster): PixelSprite? {
        if (monster.artFamily != MonsterArtFamilies.LUNA ||
            monster.species != "精霊" || monster.element != "光") return null
        return sprites[monster.stage.coerceIn(1,3)-1]
    }
}
