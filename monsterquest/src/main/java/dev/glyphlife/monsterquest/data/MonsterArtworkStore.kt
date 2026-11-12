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
import dev.glyphlife.monsterquest.pixel.MonsterArmor
import dev.glyphlife.monsterquest.pixel.CustomArmorLayers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
    private val armorFolder = File(context.filesDir, "monster_armor_layers_v1")

    private fun layerFile(id: String): File {
        val canonical = UUID.fromString(id).toString()
        require(canonical.equals(id, ignoreCase = true)) { "無効な装備画像ID" }
        return File(armorFolder, "$canonical.png")
    }

    /**
     * Reusable local equipment library, newest first. Asset IDs are never
     * transformed into user-controlled paths except through layerFile().
     * Listing does not decode image data and does not contact AI services.
     */
    fun listArmorLayerIds(): List<String> {
        val files = armorFolder.listFiles()?.asSequence()
            ?.filter { it.isFile }
            ?.map { ArmorAssetFile(it.name, it.length(), it.lastModified()) }
            ?.toList() ?: return emptyList()
        return ArmorAssetCatalog.ids(files)
    }

    /** Missing imported assets never break old saves or gameplay. */
    fun loadArmorLayer(id: String): PixelSprite? = runCatching {
        val bitmap = BitmapFactory.decodeFile(layerFile(id).absolutePath)
            ?: return@runCatching null
        try {
            if (bitmap.width != 64 || bitmap.height != 64) return@runCatching null
            val pixels = IntArray(4096)
            bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64)
            PixelSprite.fromArgb(64, 64, pixels)
        } finally { bitmap.recycle() }
    }.getOrNull()

    suspend fun importArmorLayer(uri: Uri): String = withContext(Dispatchers.IO) {
        val encoded = context.contentResolver.openInputStream(uri)?.use {
            it.readNBytes(8_000_001)
        } ?: error("パーツ画像を開けません")
        storeArmorLayer(encoded)
    }

    /** Imports an isolated part, not a whole-monster image. Returns immutable asset ID. */
    suspend fun storeArmorLayer(encoded: ByteArray): String = withContext(Dispatchers.IO) {
        require(encoded.size in 64..8_000_000) { "パーツ画像が大きすぎます" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        require(bounds.outWidth in 16..4096 && bounds.outHeight in 16..4096) {
            "画像の縦横サイズが不正です"
        }
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 256) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            encoded, 0, encoded.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("画像を読み込めません")
        try {
            val cols = IntArray(decoded.width * decoded.height)
            decoded.getPixels(cols, 0, decoded.width, 0, 0, decoded.width, decoded.height)
            val asset = CustomArmorLayers.normalize(
                PixelSprite.fromArgb(decoded.width, decoded.height, cols))
            check(armorFolder.isDirectory || armorFolder.mkdirs()) { "装備画像の保存先を作れません" }
            val id = UUID.randomUUID().toString()
            val output = layerFile(id)
            val temp = File(armorFolder, "$id.pending")
            val bitmap = PixelAssetPipeline.toBitmap(asset)
            try {
                temp.outputStream().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                check(temp.length() in 100..3_500_000) { "パーツ画像の保存に失敗しました" }
                check(temp.renameTo(output)) { "パーツ画像を保存できません" }
                id
            } finally {
                bitmap.recycle()
                temp.delete()
            }
        } finally { decoded.recycle() }
    }

    /** Only use for a not-yet-confirmed asset. Shared fusion assets must be retained. */
    fun discardUnassignedLayer(id: String) {
        runCatching { layerFile(id).delete() }
    }

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

    /** Resolve the same layered art for UI, export, animation and Glyph. */
    fun spriteFor(monster: WildMonster): PixelSprite {
        val saved = loadNative(monster.id, monster.stage)
        val base = if (saved != null) MonsterArmor.compose(saved, monster)
            else MonsterDesign.sprite(monster)
        return CustomArmorLayers.compose(base, monster, ::loadArmorLayer)
    }

    /**
     * Explicit user request only: render the actual armored monster as a compact
     * reference image for the optional external image API. The AI sees this
     * 512x512 nearest-neighbour copy, not personal photo files or save data.
     * The source sprite is never modified or uploaded automatically.
     */
    fun referencePng(monster: WildMonster): ByteArray {
        val source = spriteFor(monster)
        require(source.width == 64 && source.height == 64)
        val native = source.pixels()
        val side = 512
        val background = 0xff192631.toInt()
        val displayed = IntArray(side * side) { index ->
            val x = (index % side) / 8
            val y = (index / side) / 8
            val color = native[y * 64 + x]
            if ((color ushr 24) >= 128) color else background
        }
        val bitmap = Bitmap.createBitmap(displayed, side, side, Bitmap.Config.ARGB_8888)
        try {
            val out = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "参考画像をPNGに変換できませんでした"
            }
            val bytes = out.toByteArray()
            require(bytes.size in 64..2_000_000) { "参考画像が大きすぎます" }
            return bytes
        } finally { bitmap.recycle() }
    }

    /** Render the 64-pixel master at 256 pixels without any interpolation. */
    fun load(id: String, stage:Int=1): Bitmap? =
        loadNative(id,stage)?.let(PixelAssetPipeline::display256)

    /** 13x13 Glyph variant. A custom imported body image always uses its actual
     * pixels instead of the built-in family's authored face insignia.
     */
    fun glyphFor(monster: WildMonster): String =
        glyphFrameFor(monster, spriteFor(monster), 0)

    fun glyphFrameFor(monster: WildMonster, frame: PixelSprite, pose: Int): String =
        if (hasArt(monster.id, monster.stage)) GlyphProjector.project(frame)
        else GlyphProjector.projectFor(monster, frame, pose)

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
        val sprite=spriteFor(monster)
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
        val sprite = spriteFor(monster)
        val bitmap = if(size==64) PixelAssetPipeline.toBitmap(sprite)
                     else PixelAssetPipeline.display256(sprite)
        val resolver=context.contentResolver
        val candidateNumber=monster.designSeed.removePrefix("atelier-").toIntOrNull()
        val suffix=if(monster.designSeed.startsWith("atelier-") &&
            candidateNumber != null && candidateNumber in 0..63)
            "-v$candidateNumber" else ""
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,
                "monsterquest-${monster.id.take(8)}${suffix}-s${monster.stage}-${size}px.png")
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
