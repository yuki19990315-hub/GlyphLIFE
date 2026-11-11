package dev.glyphlife.monsterquest.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import dev.glyphlife.monsterquest.model.WildMonster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class MonsterIdea(
    val name: String,
    val element: String,
    val description: String,
    val pixels: String
) {
    fun asWildMonster(room: Int): WildMonster = WildMonster(
        id = UUID.randomUUID().toString(),
        species = name,
        element = element,
        maxHp = 18 + room * 7,
        attack = 4 + room,
        description = description,
        pixels = pixels
    )
}

/**
 * BYOK AI monster designer. User provides a Google Gemini API key only for the
 * current process. Image data goes to Google ONLY when the user selects an
 * image and requests generation. Game stats are never provided by the model.
 */
object MonsterAi {
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"

    private val elements = setOf("火", "水", "草", "雷", "光", "闇", "無")

    fun parse(raw: String): MonsterIdea {
        require(raw.length <= 12000) { "AIデータが長すぎます" }
        val j = JSONObject(raw)
        val name = j.getString("name").trim()
        val element = j.getString("element").trim()
        val description = j.getString("description").trim()
        val pixels = j.getString("pixels")
        require(name.length in 1..16 && !name.any { java.lang.Character.isISOControl(it) })
        require(element in elements) { "未対応の属性です" }
        require(description.length in 1..160)
        require(pixels.length == 169 && pixels.all { it == '0' || it == '1' })
        return MonsterIdea(name, element, description, pixels)
    }

    fun prompt(userPrompt: String): String = """
        Invent one original cute but distinctive fantasy monster for a mobile collection game.
        Inspiration: ${userPrompt.take(240)}
        Return ONLY valid JSON in the form:
        {"name":"...","element":"水","description":"...",
        "pixels":"169 binary digits"}
        - name: original Japanese name, max 16 chars.
        - element: EXACTLY one of 火, 水, 草, 雷, 光, 闇, 無.
        - description: a fun Japanese one-sentence description, max 160 chars.
        - pixels: ONE string of 169 characters, each 0 or 1, representing
          13 rows × 13 columns in row-major order. 1 is a lit white LED.
          Draw a readable 13x13 pixel art portrait with a distinctive silhouette.
        - No status numbers such as HP, rarity or attack.
        - No markdown, no code fences, no extra fields.
    """.trimIndent()

    suspend fun generate(
        apiKey: String, description: String,
        photoJpeg: ByteArray? = null
    ): MonsterIdea = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank() && apiKey.length <= 256)
        require(description.isNotBlank() && description.length <= 500)
        if (photoJpeg != null) require(photoJpeg.size <= 1_000_000)
        val parts = JSONArray().put(JSONObject().put("text", prompt(description)))
        if (photoJpeg != null) {
            parts.put(JSONObject().put("inline_data", JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", Base64.encodeToString(photoJpeg, Base64.NO_WRAP))))
        }
        val request = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.8)
                .put("responseMimeType", "application/json")
                .put("maxOutputTokens", 1800))

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }
        try {
            connection.outputStream.use {
                it.write(request.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                error("Gemini HTTP $status。APIキー、利用枠、モデルの提供状況をご確認ください")
            }
            val bytes = connection.inputStream.use { it.readNBytes(60_001) }
            require(bytes.size <= 60_000)
            val result = JSONObject(bytes.toString(Charsets.UTF_8))
            val partsOut = result.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val raw = buildString {
                for (i in 0 until partsOut.length()) {
                    append(partsOut.getJSONObject(i).optString("text"))
                }
            }.trim()
            parse(raw)
        } finally {
            connection.disconnect()
        }
    }

    /** Resizes locally before uploading; a large original photo is never sent. */
    suspend fun preparePhoto(context: Context, uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            require(options.outWidth in 1..20_000 && options.outHeight in 1..20_000)
            var sample = 1
            while (maxOf(options.outWidth / sample, options.outHeight / sample) > 720) {
                sample *= 2
            }
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("画像を読み込めません")
            try {
                val output = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output))
                val bytes = output.toByteArray()
                require(bytes.size <= 1_000_000) { "写真を小さくしてから再試行してください" }
                bytes
            } finally {
                bitmap.recycle()
            }
        }
}
