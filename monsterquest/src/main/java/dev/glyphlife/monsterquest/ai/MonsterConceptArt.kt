package dev.glyphlife.monsterquest.ai

import android.util.Base64
import dev.glyphlife.monsterquest.model.WildMonster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** An optional paid image request separate from the offline 13×13 Glyph asset. */
enum class ConceptStyle(val label: String, val direction: String) {
    GBA_PIXEL("GBA風ドット絵",
        "Classic Game Boy Advance era original creature: compact sprite readability, distinct body silhouette and expressive eyes"),
    DARK_FANTASY("ダークファンタジー",
        "Eerie ancient monster, dark fantasy realism, rough hide, bone, armor and spectral energy"),
    MECHA("メカ・機甲",
        "Magnificent biomechanical monster, layered hard-surface armor, pistons, glowing reactor cores"),
    DRAGON("竜・幻獣",
        "Legendary armored dragon or mythic beast, intricate wings, horns, scales and elemental aura"),
    CELESTIAL("神秘・神獣",
        "Otherworldly divine creature, crystalline structures, cosmic motifs, mystical luminous anatomy")
}

object MonsterConceptArt {
    // API image-generation model: deliberately NOT the text/JSON model.
    const val MODEL = "gemini-3.1-flash-image"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1/models/$MODEL:generateContent"
    private const val MAX_RESPONSE = 18_000_000

    fun prompt(wild: WildMonster, style: ConceptStyle): String = """
        Create ONE original GAME BOY ADVANCE-STYLE full-body pixel-art monster
        sprite for a creature collecting and evolving game. This must look like
        coherent collectible monster character design, NOT a procedural circle,
        geometric placeholder, blurred miniature painting or pasted collage.

        IMPORTANT ART CONTRACT:
        - The playable master canvas is an EXACT 64x64 pixel grid.
        - Design for 16 total palette entries INCLUDING one transparent color.
          Maximum 15 opaque colors for outline, materials, shadows and accents.
        - Strong, deliberate clusters of square pixels aligned to a real grid.
          No antialiasing, gradients, vector smoothing, photographic texture.
        - Draw the WHOLE creature including both feet, horns, wings and tail,
          inside a square with a clear safety margin on every side.
        - A single complete character, three-quarter RIGHT-FACING battle pose.
        - Explicit head, eyes, core silhouette, limbs and element-bearing parts;
          avoid shapeless ovals and disconnected decoration.
        - Stage 1 is small, appealing and expressive, not forcibly menacing.
          Stage 2 grows distinctive horns, limbs or armor.
          Stage 3 is impressive, but keeps the same species' facial identity,
          base colors and elemental signature.
        - Current stage: ${wild.stage.coerceIn(1,3)} / 3. Only ONE creature
          at the requested stage, NOT a three-panel evolution sheet.
        - Design may be beast, spirit, mechanical or mystical as appropriate.
          Theme: ${style.direction}.

        Character name: ${wild.species.take(60)}
        Element: ${wild.element.take(20)}
        Lore / distinctive anatomy: ${wild.description.take(180)}
        Primary motif: ${wild.motifPrimary.take(40)}
        Secondary motif: ${wild.motifSecondary.take(40)}

        Keep large shape, leg/wing/tail separation, facial features, and high
        contrast more important than micro-detail. Pixel readability is judged
        AT 64x64 rather than by looking attractive only at 1024x1024.

        Use EXACT solid flat #FF00FF background for automated transparency
        extraction (do NOT use magenta as a creature color), with no lighting,
        terrain, shadows, type, text, borders, logo, watermarks or UI.
        Return an image only. Do not depict any protected franchise creature.
    """.trimIndent()

    fun parseImagePayload(rawJson: String): ByteArray {
        require(rawJson.length <= MAX_RESPONSE) { "画像レスポンスが大きすぎます" }
        val root = JSONObject(rawJson)
        val candidates = root.optJSONArray("candidates") ?: error("画像候補がありません")
        require(candidates.length() > 0) { "画像候補が空です" }
        val parts = candidates.getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val data = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
            if (data != null) {
                val mime = data.optString("mimeType",
                    data.optString("mime_type", ""))
                require(mime in setOf("image/png", "image/jpeg", "image/webp")) {
                    "未対応の画像形式です"
                }
                val b64 = data.getString("data")
                require(b64.length in 128..(MAX_RESPONSE - 1000))
                val decoded = Base64.decode(b64, Base64.DEFAULT)
                require(decoded.size in 64..12_000_000) { "画像データが不正です" }
                return decoded
            }
        }
        error("画像生成結果に画像がありません。画像専用モデルの利用枠をご確認ください")
    }

    suspend fun generate(apiKey: String, wild: WildMonster, style: ConceptStyle): ByteArray =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank() && apiKey.length <= 256)
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject()
                    .put("parts", JSONArray().put(JSONObject()
                        .put("text", prompt(wild, style))))))
                .put("generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("IMAGE"))
                    .put("responseFormat", JSONObject()
                        .put("image", JSONObject()
                            .put("aspectRatio", "1:1")
                            .put("imageSize", "1K"))))
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
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    error("画像生成API HTTP $status。課金設定、APIキー、利用枠を確認してください")
                }
                val raw = connection.inputStream.use { it.readNBytes(MAX_RESPONSE + 1) }
                require(raw.size <= MAX_RESPONSE) { "画像応答が容量上限を超えました" }
                parseImagePayload(raw.toString(Charsets.UTF_8))
            } finally {
                connection.disconnect()
            }
        }
}
