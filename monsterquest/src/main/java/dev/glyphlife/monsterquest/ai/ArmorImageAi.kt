package dev.glyphlife.monsterquest.ai

import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.ArmorSlot
import dev.glyphlife.monsterquest.pixel.MonsterBodyType
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional PAID/BYOK visual generation. The AI returns ONLY the isolated
 * component image. Local code validates transparency and scales it to an
 * allowed body anchor; the model never controls arbitrary placement or stats.
 */
object ArmorImageAi {
    private const val URL_PATH =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-3.1-flash-image:generateContent"
    private const val MAX_JSON_BYTES = 18_000_000

    fun prompt(monster: WildMonster, slot: ArmorSlot): String {
        val body = MonsterDesign.bodyType(monster)
        val description = when(slot) {
            ArmorSlot.HEAD -> "one helmet, pair of horns, crown or expressive protective facepiece"
            ArmorSlot.CORE -> "one compact glowing chest core or chest armor plate"
            ArmorSlot.TAIL -> "one blade, fin or tail-tip weapon attached to an existing tail"
            ArmorSlot.SHOULDER -> "one matching shoulder pauldron or shoulder spike module"
            ArmorSlot.BACK -> "one dorsal armored crest or array of spines"
            ArmorSlot.WING -> "one isolated pair of mechanical wings or supplementary wing armor"
        }
        return """
            Create ONE original isolated PIXEL-ART EQUIPMENT COMPONENT for
            a fantasy creature game. The output is an ART ASSET, not a
            complete monster illustration.
            Creature: ${monster.species.take(60)}.
            Body: ${body.name}; element: ${monster.element.take(20)}.
            Equipment SLOT: ${slot.name} (${slot.label}).
            Draw exactly: $description.
            The asset is attached to a pre-existing 64x64 body with a
            predefined anchor and a deterministic size; no body or face may
            appear in the output. Make it immediately recognizable at
            12-30 pixels wide after downsampling. Crisp clusters, thick
            silhouette, 2-level shadows and bright highlights.
            Draw exactly ONE separated component (paired pieces can touch),
            at center of a SQUARE canvas with generous padding.
            Use SOLID #FF00FF MAGENTA as the entire background, sharp flat
            boundary, no drop shadows on background, NO gradients,
            anti-aliasing, lettering, logos, full creatures, scenery or UI.
            The app locally makes magenta transparent and limits colors;
            image-model output quality and background cleanliness are not
            guaranteed. Avoid copying any franchise or character.
            Return IMAGE ONLY.
        """.trimIndent()
    }

    suspend fun generate(
        apiKey: String, monster: WildMonster, slot: ArmorSlot
    ): ByteArray = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank() && apiKey.length <= 256)
        val body = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts",
                    JSONArray().put(JSONObject().put("text", prompt(monster, slot))))
            ))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("IMAGE"))
                .put("responseFormat", JSONObject()
                    .put("image", JSONObject()
                        .put("aspectRatio", "1:1")
                        .put("imageSize", "512"))))
        val con = (URL(URL_PATH).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }
        try {
            con.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = con.responseCode
            if (status !in 200..299)
                error("画像生成HTTP $status。APIキー・利用枠・請求設定を確認してください")
            val bytes = con.inputStream.use { it.readNBytes(MAX_JSON_BYTES + 1) }
            require(bytes.size <= MAX_JSON_BYTES) { "画像応答が上限を超えています" }
            MonsterConceptArt.parseImagePayload(bytes.toString(Charsets.UTF_8))
        } finally { con.disconnect() }
    }
}
