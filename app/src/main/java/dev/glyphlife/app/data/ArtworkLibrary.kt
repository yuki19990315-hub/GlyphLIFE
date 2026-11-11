package dev.glyphlife.app.data

import android.content.Context
import dev.glyphlife.app.model.GlyphAnimation
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ArtworkSummary(
    val id: String,
    val name: String,
    val frameCount: Int,
    val savedAt: Long
)

/**
 * Small offline gallery of up to 30 named Glyph animations.
 * Gallery works independently of the per-pet auto-saved working document.
 */
class ArtworkLibrary(context: Context) {
    private val preferences = context.getSharedPreferences("glyph_gallery_v1", Context.MODE_PRIVATE)
    private val parser = PetRepository(context)

    private fun readEntries(): List<JSONObject> = runCatching {
        val raw = preferences.getString("artworks", "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())

    fun list(): List<ArtworkSummary> = readEntries().mapNotNull { item ->
        runCatching {
            ArtworkSummary(
                id = item.getString("id"),
                name = item.getString("name"),
                frameCount = item.getJSONObject("animation").getJSONArray("frames").length(),
                savedAt = item.optLong("savedAt", 0L)
            )
        }.getOrNull()
    }.sortedByDescending { it.savedAt }

    @Synchronized
    fun save(name: String, animation: GlyphAnimation): ArtworkSummary {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 40) { "作品名は1～40字にしてください" }
        val previous = readEntries()
        require(previous.size < 30) { "作品は最大30件です" }
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val entry = JSONObject()
            .put("id", id).put("name", cleanName).put("savedAt", timestamp)
            .put("animation", JSONObject(parser.encodeAnimation(animation)))
        val all = JSONArray()
        previous.forEach { all.put(it) }
        all.put(entry)
        check(preferences.edit().putString("artworks", all.toString()).commit()) {
            "作品を保存できませんでした"
        }
        return ArtworkSummary(id, cleanName, animation.frames.size, timestamp)
    }

    fun load(id: String): GlyphAnimation {
        val entry = readEntries().firstOrNull { it.optString("id") == id }
            ?: throw IllegalArgumentException("作品が見つかりません")
        return parser.parseAnimation(entry.getJSONObject("animation").toString())
    }

    @Synchronized
    fun delete(id: String) {
        val previous = readEntries()
        require(previous.any { it.optString("id") == id }) { "作品が見つかりません" }
        val output = JSONArray()
        previous.filterNot { it.optString("id") == id }.forEach { output.put(it) }
        check(preferences.edit().putString("artworks", output.toString()).commit()) {
            "作品を削除できませんでした"
        }
    }
}
