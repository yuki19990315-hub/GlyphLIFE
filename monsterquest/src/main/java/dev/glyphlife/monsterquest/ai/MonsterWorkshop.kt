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

