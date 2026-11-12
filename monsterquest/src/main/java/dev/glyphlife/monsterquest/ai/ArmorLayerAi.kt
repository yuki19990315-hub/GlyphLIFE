package dev.glyphlife.monsterquest.ai

import android.graphics.Bitmap
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.ArmorSlot
import dev.glyphlife.monsterquest.pixel.MonsterBodyType
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline
import dev.glyphlife.monsterquest.pixel.PixelSprite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * One user-triggered, optionally paid image request per attachment.
 * We send the existing sprite as an image reference to preserve alignment.
 * The generated result is untrusted and must pass local pixel/alpha validation
 * and an explicit preview+confirmation before becoming a game asset.
 */
object ArmorLayerAi {
    const val MODEL = MonsterConceptArt.MODEL
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1/models/$MODEL:generateContent"
    private const val MAX_RESPONSE_BYTES = 18_000_000

    private fun anchor(slot: ArmorSlot, body: MonsterBodyType): Pair<Int, Int> =
        when (slot) {
            ArmorSlot.HEAD -> when (body) {
                MonsterBodyType.BEAST, MonsterBodyType.DRAGON,
                MonsterBodyType.SERPENT -> 23 to 17
                MonsterBodyType.AQUATIC -> 20 to 25
                MonsterBodyType.AVIAN -> 31 to 15
                else -> 32 to 15
            }
            ArmorSlot.CORE -> 32 to 34
            ArmorSlot.TAIL -> 53 to 43
            ArmorSlot.SHOULDER -> 19 to 30
            ArmorSlot.BACK -> 39 to 24
            ArmorSlot.WING -> 49 to 29
        }

    fun prompt(wild: WildMonster, slot: ArmorSlot): String {
        val (x, y) = anchor(slot, MonsterDesign.bodyType(wild))
        return """
            Design ONE ORIGINAL pixel-art armor PART, not a whole monster.
            Existing body reference image is supplied in a separate image part.
            Creature: ${wild.species.take(35)}; Element: ${wild.element.take(12)};
            Anatomy: ${MonsterDesign.bodyType(wild)}.
            Part slot: ${slot.name} (${slot.label}).
            Use the exact SAME square coordinate system as the reference.
            Attachment center on the 64x64 grid: ($x,$y). The input image
            scales the 64x64 pixel grid to 256x256. Keep this local attachment
            in a compact area around that center; it must FIT the original
            silhouette, not cover the eyes or erase the face. Wings and tails
            can extend farther along the outer silhouette.
            Output ONLY the attachment pixels on a SOLID FLAT #FF00FF
            MAGENTA BACKGROUND (RGB 255,0,255). No checkerboard or floor.
            DO NOT reproduce the monster body. DO NOT add text, UI, borders,
            shadows or a picture of a complete character.
            Sharp chunky hand-placed pixel clusters, deliberately few colors,
            aligned exactly to the body. The application will downsample to
            a transparent 64x64 PNG and reject opaque backgrounds.
            Invent a distinct original design, not a protected character.
        """.trimIndent()
    }

    fun buildRequest(
        wild: WildMonster, slot: ArmorSlot, basePng: ByteArray
    ): JSONObject {
        require(basePng.size in 64..1_000_000)
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt(wild, slot)))
            .put(JSONObject().put("inlineData", JSONObject()
                .put("mimeType", "image/png")
                .put("data", Base64.getEncoder().encodeToString(basePng))))
        return JSONObject().put("contents", JSONArray().put(
            JSONObject().put("role", "user").put("parts", parts)
        )).put("generationConfig", JSONObject()
            .put("responseModalities", JSONArray().put("IMAGE"))
            .put("responseFormat", JSONObject().put("image", JSONObject()
                .put("aspectRatio", "1:1").put("imageSize", "1K"))))
    }

    suspend fun generate(
        apiKey: String, wild: WildMonster, slot: ArmorSlot, reference: PixelSprite
    ): ByteArray = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank() && apiKey.length <= 256)
        require(reference.width == 64 && reference.height == 64)
        val enlarged = PixelAssetPipeline.display256(reference)
        val png = try {
            ByteArrayOutputStream().use { output ->
                check(enlarged.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally { enlarged.recycle() }
        val body = buildRequest(wild, slot, png)
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("x-goog-api-key", apiKey.trim())
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val response = connection.responseCode
            if (response !in 200..299) error(
                "画像パーツ生成 HTTP $response。APIキー、利用枠・料金設定を確認してください")
            val encoded = connection.inputStream.use {
                it.readNBytes(MAX_RESPONSE_BYTES + 1)
            }
            require(encoded.size <= MAX_RESPONSE_BYTES)
            MonsterConceptArt.parseImagePayload(
                encoded.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}
