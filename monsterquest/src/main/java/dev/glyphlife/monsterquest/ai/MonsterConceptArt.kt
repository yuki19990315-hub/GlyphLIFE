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
        Produce ONE finished, original full-body MONSTER PIXEL SPRITE for a serious
        monster-collecting fantasy RPG. The creature must be imposing and
        memorable, NOT a cute chibi, smooth painting, plush toy or simple blob.
        Render as genuine retro hand-placed pixel art: BIG, CONSISTENT
        pixel clusters, hard 1-pixel edges, NO antialiasing, NO gradients,
        NO soft shading, NO smooth vector lines. The native sprite will be
        sampled to EXACTLY 64x64 pixels and quantized to MAXIMUM 16 COLORS.

        Character identity: ${wild.species.take(60)}
        Element: ${wild.element.take(20)}
        Lore: ${wild.description.take(180)}
        Design route: ${style.direction}.

        Art direction:
        - Full-body single creature, strong readable silhouette, 3/4 dramatic pose,
          anatomically coherent original design.
        - Distinct head and face, powerful limbs, tail or ornamentation appropriate
          to species, carefully considered proportions, intricate armor/organic plates.
        - Primary body palette, secondary material and small high-contrast accents
          selected to reflect its element. Rich multi-tone shading, rim lighting,
          sophisticated material details, mystical energy effects and cinematic depth.
        - A sense of threat, intelligence, personality and iconic visual identity.
        - Crisp, deliberate pixel clusters with a restrained indexed palette,
          classic handcrafted 2D game sprite craftsmanship.
        - Use EXACTLY a solid flat #FF00FF magenta background, no texture,
          gradient, cast shadow, glow, lettering or decorative border.
          Magenta is a background key for local transparent PNG conversion.
        - Center entire character including horns, claws, wings and tail within
          a SQUARE 1:1 frame, with safety margins so NOTHING is cropped.
          Front/3-quarter game sprite pose, clear outline, strong silhouette.
        - NO text, NO typography, NO letters, NO logos, NO watermarks, NO UI panels,
          NO repeated bodies, NO collage, NO human character.
        - Original character design, do not copy existing franchises or copyrighted characters.
        Return the illustration itself, not a textual description.
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
