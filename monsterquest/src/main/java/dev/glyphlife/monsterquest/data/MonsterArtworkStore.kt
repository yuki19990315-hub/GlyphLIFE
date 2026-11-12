package dev.glyphlife.monsterquest.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline
import dev.glyphlife.monsterquest.pixel.PixelSprite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The on-device master asset is native 64x64, <=16-color PNG.
 *
 * Legacy .jpg illustrations are kept untouched. If present, they are lazily
 * pixelized for display so old saved games remain readable. The Glyph sprite
 * is derived only when requested. Never store image bytes in game JSON.
 */
class MonsterArtworkStore(private val context: Context) {
    private val folder = File(context.filesDir, "monster_art_v1")

    private fun pathFor(id: String, extension: String): File {
        UUID.fromString(id)
        return File(folder, "$id.$extension")
    }

    fun hasArt(id: String): Boolean = runCatching {
        pathFor(id,"png").isFile || pathFor(id,"jpg").isFile
    }.getOrDefault(false)

    fun loadNative(id: String): PixelSprite? = runCatching {
        val png=pathFor(id,"png")
        val legacy=pathFor(id,"jpg")
        val file=when {
            png.isFile -> png
            legacy.isFile -> legacy
            else -> return@runCatching null
        }
        val bitmap=BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
        try {
            if(bitmap.width==64 && bitmap.height==64 && png.isFile) {
                val colors=IntArray(4096)
                bitmap.getPixels(colors,0,64,0,0,64,64)
                PixelSprite.fromArgb(64,64,colors)
            } else PixelAssetPipeline.fromBitmap(bitmap)
        } finally { bitmap.recycle() }
    }.getOrNull()

    /** Render the 64-pixel master at 256 pixels without any interpolation. */
    fun load(id: String): Bitmap? = loadNative(id)?.let(PixelAssetPipeline::display256)

    /** Portable 13×13 derivative, produced on demand; never the master. */
    fun glyphFor(monster: WildMonster): String =
        GlyphProjector.project(loadNative(monster.id) ?: MonsterDesign.sprite(monster))

    suspend fun importFromUri(id: String, uri: Uri) = withContext(Dispatchers.IO) {
        val bytes=context.contentResolver.openInputStream(uri)?.use { it.readNBytes(12_000_001) }
            ?: error("画像を読み込めません")
        store(id, bytes)
    }

    suspend fun store(id: String, encoded: ByteArray) = withContext(Dispatchers.IO) {
        UUID.fromString(id)
        require(encoded.size in 64..12_000_000) { "画像の容量が上限を超えています" }
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(encoded,0,encoded.size,bounds)
        require(bounds.outWidth in 64..8192 && bounds.outHeight in 64..8192) {
            "画像の縦横サイズが不正です"
        }
        var sample=1
        while (maxOf(bounds.outWidth/sample,bounds.outHeight/sample)>1100) sample*=2
        val decode=BitmapFactory.Options().apply { inSampleSize=sample }
        val original=BitmapFactory.decodeByteArray(encoded,0,encoded.size,decode)
            ?: error("画像形式を読み込めません")
        try {
            val sprite=PixelAssetPipeline.fromBitmap(original)
            saveNative(id,sprite)
        } finally { original.recycle() }
    }

    private fun saveNative(id: String,sprite:PixelSprite) {
        require(sprite.width==64 && sprite.height==64)
        check(folder.isDirectory || folder.mkdirs()) { "画像保存先を作成できません" }
        val file=pathFor(id,"png")
        val temp=File(folder,"$id.pending")
        val bitmap=PixelAssetPipeline.toBitmap(sprite)
        try {
            try {
                temp.outputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
                }
                require(temp.length() in 100..3_500_000)
                check(temp.renameTo(file)) { "ドット絵の保存に失敗しました" }
            } finally { temp.delete() }
        } finally { bitmap.recycle() }
    }
}
