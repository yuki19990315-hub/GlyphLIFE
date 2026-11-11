package dev.glyphlife.monsterquest.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.glyphlife.monsterquest.model.WildMonster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** One local illustration per creature ID. No image bytes in JSON/SharedPreferences. */
class MonsterArtworkStore(private val context: Context) {
    private val folder = File(context.filesDir, "monster_art_v1")
    private fun fileFor(id: String): File {
        UUID.fromString(id) // Disallow traversal and untrusted filenames.
        return File(folder, "$id.jpg")
    }

    fun load(id: String): Bitmap? = runCatching {
        val file = fileFor(id)
        if (!file.isFile) null else BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    fun hasArt(id: String): Boolean = runCatching { fileFor(id).isFile }.getOrDefault(false)

    suspend fun importFromUri(id: String, uri: Uri) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(12_000_001) }
            ?: error("写真を読み込めません")
        store(id, bytes)
    }

    suspend fun store(id: String, encoded: ByteArray) = withContext(Dispatchers.IO) {
        require(encoded.size in 64..12_000_000) { "画像ファイルが大きすぎます" }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
        require(options.outWidth in 128..8192 && options.outHeight in 128..8192) {
            "読み込めない画像サイズです"
        }
        var sample = 1
        while (maxOf(options.outWidth / sample, options.outHeight / sample) > 1100) sample *= 2
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, decode)
            ?: error("画像形式が不正です")
        try {
            require(folder.isDirectory || folder.mkdirs()) { "保存先を準備できません" }
            val target = fileFor(id)
            val temp = File(folder, "$id.tmp")
            try {
                temp.outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) {
                        "画像の変換に失敗しました"
                    }
                }
                require(temp.length() in 100..3_500_000)
                // Failed writes leave an existing portrait intact.
                check(temp.renameTo(target)) { "画像の保存に失敗しました" }
            } finally {
                temp.delete()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
