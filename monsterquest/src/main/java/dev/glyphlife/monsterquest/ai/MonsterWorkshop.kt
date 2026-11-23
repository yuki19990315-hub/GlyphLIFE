package dev.glyphlife.monsterquest.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.GbaMonsterArtStandard
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline
import dev.glyphlife.monsterquest.pixel.PixelSprite
import java.io.ByteArrayOutputStream

/** The generated asset is only a review candidate until the user approves. */
object MonsterWorkshop {
    data class Art(
        val sprite: PixelSprite,
        val png: ByteArray,
        val audit: GbaMonsterArtStandard.Audit
    )

    fun prepare(bytes: ByteArray): Art {
        require(bytes.size in 64..12_000_000) { "画像容量が不正です" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 64..8192 && bounds.outHeight in 64..8192) {
            "画像の縦横サイズが不正です"
        }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1100)
            sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("生成画像を読み込めません")
        try {
            val sprite = GbaMonsterArtStandard.canonical(PixelAssetPipeline.fromBitmap(bitmap))
            val audit = GbaMonsterArtStandard.audit(sprite)
            val output = ByteArrayOutputStream()
            val native = PixelAssetPipeline.toBitmap(sprite)
            try {
                check(native.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "PNG画像を書き出せません"
                }
            } finally { native.recycle() }
            return Art(sprite, output.toByteArray(), audit)
        } finally { bitmap.recycle() }
    }
}

/** Ephemeral, cancel-safe proposal; no side effects on game state. */
/**
 * A Pixel-Lab-like selection session: retain up to four images in RAM,
 * choose a previous alternative without another paid API call, then commit.
 * A cancelled session makes NO changes to player progression.
 */
data class MonsterWorkshopDraft(
    val idea: MonsterIdea,
    val monster: WildMonster,
    val variants: List<MonsterWorkshop.Art> = emptyList(),
    val selectedIndex: Int = 0
) {
    init {
        require(variants.size <= 4)
        require(selectedIndex in 0..maxOf(0, variants.lastIndex))
    }

    val art: MonsterWorkshop.Art? get() = variants.getOrNull(selectedIndex)
    val readyForApproval: Boolean get() = art?.audit?.compliant ?: true
    val canGenerateMore: Boolean get() = variants.size < 4

    fun choose(index: Int): MonsterWorkshopDraft {
        require(index in variants.indices)
        return copy(selectedIndex = index)
    }

    fun addCandidate(candidate: MonsterWorkshop.Art): MonsterWorkshopDraft {
        require(canGenerateMore) { "候補は最大4枚です" }
        return copy(variants = variants + candidate, selectedIndex = variants.size)
    }
}
