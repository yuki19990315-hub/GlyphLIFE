package dev.glyphlife.monsterquest.data

import android.content.Context
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.pixel.MonsterAnimation
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

    private fun pathFor(id: String, extension: String, stage:Int=1): File {
        UUID.fromString(id)
        require(stage in 1..3)
        return File(folder, if(extension=="png") "$id-s$stage.png" else "$id.$extension")
    }
    private fun originalV2Path(id:String):File {
        UUID.fromString(id)
        return File(folder,"$id.png")
    }

    fun hasArt(id: String,stage:Int=1): Boolean = runCatching {
        pathFor(id,"png",stage).isFile ||
            (stage==1 && (originalV2Path(id).isFile || pathFor(id,"jpg").isFile))
    }.getOrDefault(false)

    fun loadNative(id: String, stage:Int=1): PixelSprite? = runCatching {
        val png=pathFor(id,"png",stage)
        val previous=originalV2Path(id)
        val legacy=pathFor(id,"jpg")
        val file=when {
            png.isFile -> png
            stage==1 && previous.isFile -> previous
            stage==1 && legacy.isFile -> legacy
            else -> return@runCatching null
        }
        val bitmap=BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching null
        try {
            if(bitmap.width==64 && bitmap.height==64 && file.extension=="png") {
                val colors=IntArray(4096)
                bitmap.getPixels(colors,0,64,0,0,64,64)
                PixelSprite.fromArgb(64,64,colors)
            } else PixelAssetPipeline.fromBitmap(bitmap)
        } finally { bitmap.recycle() }
    }.getOrNull()

    /** Render the 64-pixel master at 256 pixels without any interpolation. */
    fun load(id: String, stage:Int=1): Bitmap? =
        loadNative(id,stage)?.let(PixelAssetPipeline::display256)

    /** Portable 13×13 derivative, produced on demand; never the master. */
    fun glyphFor(monster: WildMonster): String =
        GlyphProjector.project(loadNative(monster.id,monster.stage) ?: MonsterDesign.sprite(monster))

    suspend fun importFromUri(id: String, uri: Uri, stage:Int=1) = withContext(Dispatchers.IO) {
        val bytes=context.contentResolver.openInputStream(uri)?.use { it.readNBytes(12_000_001) }
            ?: error("画像を読み込めません")
        store(id, bytes,stage)
    }

    suspend fun store(id: String, encoded: ByteArray,stage:Int=1) = withContext(Dispatchers.IO) {
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
            saveNative(id,sprite,stage)
        } finally { original.recycle() }
    }

    /** Save four native 64x64 pose frames in a 256x64 PNG sprite sheet. */
    suspend fun exportSpriteSheet(monster: WildMonster): Uri = withContext(Dispatchers.IO) {
        val sprite=loadNative(monster.id,monster.stage) ?: MonsterDesign.sprite(monster)
        val frames=MonsterAnimation.idle(sprite,monster)
        val sheet=MonsterAnimation.sheet(frames)
        val bitmap=PixelAssetPipeline.toBitmap(sheet)
        val resolver=context.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,
                "monsterquest-${monster.id.take(8)}-stage${monster.stage}-idle-4frames.png")
            put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/MonsterQuest")
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        try {
            val target=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
                ?: error("スプライトシートの保存先を作れません")
            try {
                resolver.openOutputStream(target)?.use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
                } ?: error("スプライトシートを書き込めません")
                resolver.update(target,ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING,0)
                },null,null)
                target
            } catch(e:Exception) {
                resolver.delete(target,null,null)
                throw e
            }
        } finally {bitmap.recycle()}
    }

    /** Export a REAL PNG containing native 64x64 or nearest-neighbour 256x256 pixels. */
    suspend fun exportPng(monster: WildMonster, size:Int): Uri = withContext(Dispatchers.IO) {
        require(size==64 || size==256)
        val sprite = loadNative(monster.id,monster.stage) ?: MonsterDesign.sprite(monster)
        val bitmap = if(size==64) PixelAssetPipeline.toBitmap(sprite)
                     else PixelAssetPipeline.display256(sprite)
        val resolver=context.contentResolver
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,
                "monsterquest-${monster.id.take(8)}-s${monster.stage}-${size}px.png")
            put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/MonsterQuest")
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        try {
            val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
                ?: error("画像の保存先を作成できません")
            try {
                resolver.openOutputStream(target)?.use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
                } ?: error("画像を書き込めません")
                resolver.update(target,ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING,0)
                },null,null)
                target
            } catch (e:Exception) {
                resolver.delete(target,null,null)
                throw e
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun saveNative(id: String,sprite:PixelSprite,stage:Int) {
        require(sprite.width==64 && sprite.height==64)
        check(folder.isDirectory || folder.mkdirs()) { "画像保存先を作成できません" }
        val file=pathFor(id,"png",stage)
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
