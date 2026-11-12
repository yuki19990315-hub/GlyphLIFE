package dev.glyphlife.monsterquest.ai

import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.ArmorSlot
import dev.glyphlife.monsterquest.pixel.ArmorStyle
import dev.glyphlife.monsterquest.pixel.MonsterArmor
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional user-initiated BYOK text-only concept assistant.
 * The output is a constrained six-slot plan, NOT generated pixels or gameplay
 * parameters. No calls without a button press and no API-key persistence.
 */
object ArmorAi {
    private const val MODEL = "gemini-3.5-flash-lite"
    private const val URL_PATH =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    fun prompt(monster: WildMonster): String = """
        You are a character-design art director for an ORIGINAL monster RPG.
        Design an appealing, cohesive modular armor / horns / crystals scheme
        for an EXISTING 64 by 64 pixel monster body, without changing anatomy.
        Character: ${monster.species.take(32)}
        Element: ${monster.element.take(16)}
        Body architecture: ${MonsterDesign.bodyType(monster)}
        Lore: ${monster.description.take(120)}
        The pixel renderer owns all shapes and colors. You may ONLY SELECT
        a named style for each of the six fixed attachment slots.
        Return ONLY a JSON object, exactly six keys:
        {"HEAD":"SPIKE","CORE":"CRYSTAL","TAIL":"PLATE",
         "SHOULDER":"PLATE","BACK":"RUNE","WING":"CRYSTAL"}
        Valid keys: ${ArmorSlot.entries.joinToString(",") { it.name }}
        Valid values: ${ArmorStyle.entries.joinToString(",") { it.name }}
        Build a meaningful theme: one iconic facial element, complementary
        plating, matching color and elemental motifs, and readable shapes.
        HEAD, CORE, TAIL visible at stage 1; SHOULDER/BACK at stage 2;
        WING at stage 3. Do not output stats, image URLs, extra fields or
        any references to existing franchise characters.
    """.trimIndent()

    fun parse(raw: String): Map<String, String> {
        require(raw.length in 2..4000) { "AI装備案のサイズが不正です" }
        val obj = JSONObject(raw)
        require(obj.length() == ArmorSlot.entries.size) {
            "6つの装備スロットが必要です"
        }
        val map = ArmorSlot.entries.associate { slot ->
            require(obj.has(slot.name)) { "スロットがありません: ${slot.name}" }
            slot.name to obj.getString(slot.name)
        }
        return MonsterArmor.validated(map)
    }

    suspend fun suggest(apiKey: String, monster: WildMonster): Map<String, String> =
        withContext(Dispatchers.IO) {
            require(apiKey.length in 1..256) { "有効なAPIキーを入力してください" }
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject()
                        .put("text", prompt(monster))))))
                .put("generationConfig", JSONObject()
                    .put("temperature", 0.7)
                    .put("responseMimeType", "application/json")
                    .put("maxOutputTokens", 550))
            val con = (URL(URL_PATH).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 40_000
                instanceFollowRedirects = false
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }
            try {
                con.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
                val status = con.responseCode
                if (status !in 200..299) {
                    error("AIの装備提案に失敗（HTTP $status）。APIキー、提供地域、利用枠を確認してください")
                }
                val bytes = con.inputStream.use { it.readNBytes(8_001) }
                require(bytes.size <= 8_000) { "AIの応答が大きすぎます" }
                val parts = JSONObject(bytes.toString(Charsets.UTF_8))
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                val text = buildString {
                    for (i in 0 until parts.length()) {
                        append(parts.getJSONObject(i).optString("text"))
                    }
                }
                parse(text.trim())
            } finally {
                con.disconnect()
            }
        }
}
