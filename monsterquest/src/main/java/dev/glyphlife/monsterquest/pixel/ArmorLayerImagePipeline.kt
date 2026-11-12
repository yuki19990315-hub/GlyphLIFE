package dev.glyphlife.monsterquest.pixel

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Extracts a 64x64 coordinate-aligned overlay from a transparency-ready
 * imported or AI image. An opaque background is rejected rather than painted
 * on top of the monster. The user must approve the resulting preview.
 */
object ArmorLayerImagePipeline {
    private const val SIZE = 64

    fun fromBitmap(source: Bitmap): PixelSprite {
        require(source.width in 64..8192 && source.height in 64..8192)
        val resized = Bitmap.createScaledBitmap(source, SIZE, SIZE, false)
        try {
            val input = IntArray(SIZE * SIZE)
            resized.getPixels(input, 0, SIZE, 0, 0, SIZE, SIZE)
            val masked = input.map { c ->
                val alpha = c ushr 24
                val r = (c ushr 16) and 255
                val g = (c ushr 8) and 255
                val b = c and 255
                // Solid fuchsia background (requested from the model) or
                // real PNG alpha. Do not guess the background from a body color.
                if (alpha < 96 ||
                    (r >= 210 && b >= 210 && g <= 100 && abs(r - b) < 60)) 0
                else c or (0xff shl 24)
            }.toIntArray()
            val corner = intArrayOf(0, SIZE - 1, SIZE * (SIZE - 1), SIZE * SIZE - 1)
            require(corner.count { masked[it] == 0 } >= 3) {
                "背景が透過されていません。透明PNGかマゼンタ背景の画像を選択してください"
            }
            val solid = masked.count { it != 0 }
            require(solid in 8..2600) {
                "装甲部分だけの画像を指定してください（大きすぎる画像や空画像は不可）"
            }
            return PixelSprite.fromArgb(SIZE, SIZE,
                PixelAssetPipeline.paletteReduce(masked))
        } finally {
            if (resized !== source) resized.recycle()
        }
    }
}

/** Overlay immutable 64px parts by slot, respecting evolution unlocks. */
object CustomArmorLayers {
    fun apply(base: PixelSprite, layer: PixelSprite): PixelSprite {
        require(base.width == 64 && base.height == 64)
        require(layer.width == 64 && layer.height == 64)
        val result = base.pixels()
        val p = layer.pixels()
        for (i in p.indices) {
            if (p[i] ushr 24 >= 96) result[i] = p[i]
        }
        // Total sprite (base + part) stays inside the 16-color game budget.
        return PixelSprite.fromArgb(64, 64, PixelAssetPipeline.paletteReduce(result))
    }

    fun compose(
        base: PixelSprite,
        wild: dev.glyphlife.monsterquest.model.WildMonster,
        read: (String) -> PixelSprite?
    ): PixelSprite {
        val refs = MonsterArmor.validatedLayerRefs(wild.armorLayers)
        var current = base
        ArmorSlot.entries.forEach { slot ->
            if (wild.stage.coerceIn(1, 3) >= slot.minStage) {
                val ref = refs[slot.name] ?: return@forEach
                read(ref)?.let { layer ->
                    if (layer.width == 64 && layer.height == 64)
                        current = apply(current, layer)
                }
            }
        }
        return current
    }
}
