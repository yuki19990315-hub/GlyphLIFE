package dev.glyphlife.app.data

import android.content.Context
import dev.glyphlife.app.model.*
import org.json.JSONArray
import org.json.JSONObject

data class ChatEntry(val fromUser: Boolean, val text: String)

/** Local MVP storage with migration from single-frame v0.1 projects. */
class PetRepository(context: Context) {
    private val preferences = context.getSharedPreferences("glyph_life_v1", Context.MODE_PRIVATE)

    fun loadPet(): PetState {
        val raw = preferences.getString("pet", null) ?: return PetState()
        return runCatching {
            val o = JSONObject(raw)
            PetState(
                id = o.getString("id"), name = o.optString("name", "ピコ"),
                species = o.optString("species", "スライム"),
                element = o.optString("element", "水"),
                personality = o.optString("personality", "好奇心旺盛"),
                level = o.optInt("level", 1).coerceAtLeast(1),
                experience = o.optInt("experience", 0).coerceIn(0, 99),
                happiness = o.optInt("happiness", 60).coerceIn(0, 100),
                energy = o.optInt("energy", 80).coerceIn(0, 100)
            )
        }.getOrDefault(PetState())
    }

    fun savePet(p: PetState) {
        val o = JSONObject()
            .put("id", p.id).put("name", p.name)
            .put("species", p.species).put("element", p.element)
            .put("personality", p.personality)
            .put("level", p.level).put("experience", p.experience)
            .put("happiness", p.happiness).put("energy", p.energy)
        preferences.edit().putString("pet", o.toString()).apply()
    }

    fun loadAnimation(): GlyphAnimation {
        val json = preferences.getString("animation_v1", null)
        if (json != null) {
            runCatching { parseAnimation(json) }.getOrNull()?.let { return it }
        }
        val prior = preferences.getString("pixels", null)
        val pixels = runCatching { if (prior != null) GlyphOps.decode(prior) else PixelArt.defaultPet() }
            .getOrDefault(PixelArt.defaultPet())
        return GlyphAnimation(listOf(GlyphFrame(pixels)))
    }

    fun saveAnimation(a: GlyphAnimation) {
        preferences.edit().putString("animation_v1", encodeAnimation(a)).apply()
    }

    /** This is a portable animation project, not a Monster Quest pet snapshot. */
    fun encodeAnimation(a: GlyphAnimation): String {
        val frames = JSONArray()
        a.frames.forEach { frame ->
            frames.put(JSONObject()
                .put("pixels", GlyphOps.encode(frame.pixels))
                .put("durationMs", frame.durationMs))
        }
        return JSONObject().put("schemaVersion", 1)
            .put("type", "glyph-life-animation")
            .put("width", 13).put("height", 13)
            .put("selected", a.selected)
            .put("frames", frames).toString(2)
    }

    fun parseAnimation(raw: String): GlyphAnimation {
        require(raw.length < 200_000)
        val o = JSONObject(raw)
        require(o.getInt("schemaVersion") == 1)
        require(o.getString("type") == "glyph-life-animation")
        require(o.getInt("width") == 13 && o.getInt("height") == 13)
        val rawFrames = o.getJSONArray("frames")
        require(rawFrames.length() in 1..64)
        val frames = (0 until rawFrames.length()).map { i ->
            val frame = rawFrames.getJSONObject(i)
            GlyphFrame(
                pixels = GlyphOps.decode(frame.getString("pixels")),
                durationMs = frame.getInt("durationMs")
            )
        }
        return GlyphAnimation(frames, o.optInt("selected", 0).coerceIn(0, frames.lastIndex))
    }

    fun loadChat(): List<ChatEntry> = runCatching {
        val a = JSONArray(preferences.getString("chat_v1", "[]"))
        (0 until a.length()).map { i ->
            val item = a.getJSONObject(i)
            ChatEntry(item.getBoolean("fromUser"), item.getString("text"))
        }.takeLast(30)
    }.getOrDefault(emptyList())

    fun saveChat(lines: List<ChatEntry>) {
        val arr = JSONArray()
        lines.takeLast(30).forEach { arr.put(JSONObject().put("fromUser", it.fromUser).put("text", it.text)) }
        preferences.edit().putString("chat_v1", arr.toString()).apply()
    }

    fun exportPet(p: PetState, a: GlyphAnimation): String {
        val o = JSONObject()
            .put("schemaVersion", 1).put("source", "glyph-life")
            .put("pet", JSONObject()
                .put("id", p.id).put("name", p.name).put("species", p.species)
                .put("element", p.element).put("personality", p.personality)
                .put("level", p.level).put("experience", p.experience)
                .put("happiness", p.happiness).put("energy", p.energy))
            .put("glyph", JSONObject()
                .put("width", 13).put("height", 13)
                .put("pixels", GlyphOps.encode(a.frames.first().pixels)))
        return o.toString(2)
    }

    @Synchronized
    fun applyQuestResult(raw: String, pet: PetState): PetState {
        val result = QuestResult.parse(raw)
        require(pet.id == result.petId) { "別のペット宛ての結果です" }
        val used = preferences.getStringSet("used_quests", emptySet())?.toSet() ?: emptySet()
        require(result.eventId !in used) { "この冒険結果は反映済みです" }
        val updated = result.apply(pet)
        // Store pet data and event ID in one editor transaction.
        val o = JSONObject()
            .put("id", updated.id).put("name", updated.name)
            .put("species", updated.species).put("element", updated.element)
            .put("personality", updated.personality)
            .put("level", updated.level).put("experience", updated.experience)
            .put("happiness", updated.happiness).put("energy", updated.energy)
        check(preferences.edit()
            .putString("pet", o.toString())
            .putStringSet("used_quests", used + result.eventId)
            .commit()) { "保存に失敗しました" }
        return updated
    }
}
