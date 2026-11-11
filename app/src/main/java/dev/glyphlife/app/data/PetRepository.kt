package dev.glyphlife.app.data

import android.content.Context
import dev.glyphlife.app.model.*
import org.json.JSONArray
import org.json.JSONObject

data class ChatEntry(val fromUser: Boolean, val text: String)

/**
 * Local multi-pet storage (1..20 pets). Existing v0.1/v0.3 pet, animation and
 * chat data are migrated without discarding the original drawing.
 *
 * Room migration remains planned for larger libraries and history.
 */
class PetRepository(context: Context) {
    private val preferences = context.getSharedPreferences("glyph_life_v1", Context.MODE_PRIVATE)
    private val maxPets = 20

    private fun petObject(p: PetState) = JSONObject()
        .put("id", p.id).put("name", p.name)
        .put("species", p.species).put("element", p.element)
        .put("personality", p.personality)
        .put("level", p.level).put("experience", p.experience)
        .put("happiness", p.happiness).put("energy", p.energy)

    private fun decodePet(o: JSONObject): PetState = PetState(
        id = o.getString("id"),
        name = o.optString("name", "ピコ"),
        species = o.optString("species", "スライム"),
        element = o.optString("element", "水"),
        personality = o.optString("personality", "好奇心旺盛"),
        level = o.optInt("level", 1).coerceAtLeast(1),
        experience = o.optInt("experience", 0).coerceIn(0, 99),
        happiness = o.optInt("happiness", 60).coerceIn(0, 100),
        energy = o.optInt("energy", 80).coerceIn(0, 100)
    )

    private fun encodeRoster(pets: List<PetState>): String = JSONArray().apply {
        pets.forEach { put(petObject(it)) }
    }.toString()

    @Synchronized
    fun loadPets(): List<PetState> {
        val raw = preferences.getString("roster_v1", null)
        if (raw != null) {
            runCatching {
                val a = JSONArray(raw)
                require(a.length() in 1..maxPets)
                val pets = (0 until a.length()).map { decodePet(a.getJSONObject(it)) }
                require(pets.map { it.id }.distinct().size == pets.size)
                pets
            }.getOrNull()?.let { return it }
        }
        // v0.1 / v0.3 compatibility: don't discard a hand-drawn animation or chat.
        val older = preferences.getString("pet", null)
        val first = older?.let {
            runCatching { decodePet(JSONObject(it)) }.getOrNull()
        } ?: PetState()

        val edit = preferences.edit()
            .putString("roster_v1", encodeRoster(listOf(first)))
            .putString("active_pet_id", first.id)
            .putString("pet", petObject(first).toString())
        preferences.getString("animation_v1", null)?.let {
            edit.putString("animation_v1_${first.id}", it)
        }
        preferences.getString("chat_v1", null)?.let {
            edit.putString("chat_v1_${first.id}", it)
        }
        check(edit.commit()) { "ペットの移行データを保存できませんでした" }
        return listOf(first)
    }

    fun loadPet(): PetState {
        val pets = loadPets()
        val selected = preferences.getString("active_pet_id", null)
        return pets.find { it.id == selected } ?: pets.first()
    }

    @Synchronized
    fun selectPet(petId: String): PetState {
        val selected = loadPets().firstOrNull { it.id == petId }
            ?: throw IllegalArgumentException("ペットが見つかりません")
        check(preferences.edit()
            .putString("active_pet_id", selected.id)
            .putString("pet", petObject(selected).toString())
            .commit())
        return selected
    }

    @Synchronized
    fun addPet(pet: PetState): PetState {
        val previous = loadPets()
        require(previous.size < maxPets) { "ペットは最大20匹です" }
        require(previous.none { it.id == pet.id }) { "個体IDが重複しています" }
        val newArt = encodeAnimation(PetSpriteGenerator.create(pet))
        check(preferences.edit()
            .putString("roster_v1", encodeRoster(previous + pet))
            .putString("active_pet_id", pet.id)
            .putString("pet", petObject(pet).toString())
            .putString("animation_v1_${pet.id}", newArt)
            .commit()) { "新しいペットを保存できませんでした" }
        return pet
    }

    @Synchronized
    fun savePet(pet: PetState) {
        val roster = loadPets()
        require(roster.any { it.id == pet.id })
        val updated = roster.map { if (it.id == pet.id) pet else it }
        val edit = preferences.edit().putString("roster_v1", encodeRoster(updated))
        if (loadPet().id == pet.id) edit.putString("pet", petObject(pet).toString())
        edit.apply()
    }

    fun loadAnimation(petId: String = loadPet().id): GlyphAnimation {
        val raw = preferences.getString("animation_v1_$petId", null)
        if (raw != null) runCatching { parseAnimation(raw) }.getOrNull()?.let { return it }
        val pixels = preferences.getString("pixels", null)
            ?.takeIf { petId == loadPet().id }
            ?.let { runCatching { GlyphOps.decode(it) }.getOrNull() }
        if (pixels != null) return GlyphAnimation(listOf(GlyphFrame(pixels)))
        val pet = loadPets().firstOrNull { it.id == petId }
            ?: throw IllegalArgumentException("ペットが見つかりません")
        return PetSpriteGenerator.create(pet)
    }

    fun saveAnimation(a: GlyphAnimation, petId: String = loadPet().id) {
        require(loadPets().any { it.id == petId })
        preferences.edit().putString("animation_v1_$petId", encodeAnimation(a)).apply()
    }

    /** Portable animation project independent of the pet snapshot schema. */
    fun encodeAnimation(a: GlyphAnimation): String {
        val frames = JSONArray()
        a.frames.forEach { frame ->
            frames.put(JSONObject().put("pixels", GlyphOps.encode(frame.pixels))
                .put("durationMs", frame.durationMs))
        }
        return JSONObject().put("schemaVersion", 1)
            .put("type", "glyph-life-animation")
            .put("width", 13).put("height", 13).put("selected", a.selected)
            .put("frames", frames).toString(2)
    }

    fun parseAnimation(raw: String): GlyphAnimation {
        require(raw.length < 200_000)
        val o = JSONObject(raw)
        require(o.getInt("schemaVersion") == 1)
        require(o.getString("type") == "glyph-life-animation")
        require(o.getInt("width") == 13 && o.getInt("height") == 13)
        val arr = o.getJSONArray("frames")
        require(arr.length() in 1..64)
        val frames = (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            GlyphFrame(GlyphOps.decode(f.getString("pixels")), f.getInt("durationMs"))
        }
        return GlyphAnimation(frames, o.optInt("selected", 0).coerceIn(0, frames.lastIndex))
    }

    fun loadChat(petId: String = loadPet().id): List<ChatEntry> = runCatching {
        val arr = JSONArray(preferences.getString("chat_v1_$petId", "[]"))
        (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            ChatEntry(item.getBoolean("fromUser"), item.getString("text"))
        }.takeLast(30)
    }.getOrDefault(emptyList())

    fun saveChat(lines: List<ChatEntry>, petId: String = loadPet().id) {
        require(loadPets().any { it.id == petId })
        val arr = JSONArray()
        lines.takeLast(30).forEach {
            arr.put(JSONObject().put("fromUser", it.fromUser).put("text", it.text))
        }
        preferences.edit().putString("chat_v1_$petId", arr.toString()).apply()
    }

    fun exportPet(p: PetState, a: GlyphAnimation): String {
        val data = JSONObject()
            .put("schemaVersion", 1).put("source", "glyph-life")
            .put("pet", petObject(p))
            .put("glyph", JSONObject().put("width", 13).put("height", 13)
                .put("pixels", GlyphOps.encode(a.frames.first().pixels)))
        return data.toString(2)
    }

    @Synchronized
    fun applyQuestResult(raw: String, pet: PetState): PetState {
        val result = QuestResult.parse(raw)
        require(pet.id == result.petId) { "別のペット宛ての結果です" }
        val roster = loadPets()
        require(roster.any { it.id == pet.id })
        val used = preferences.getStringSet("used_quests", emptySet())?.toSet() ?: emptySet()
        require(result.eventId !in used) { "この冒険結果は反映済みです" }
        val updated = result.apply(pet)
        val next = roster.map { if (it.id == pet.id) updated else it }
        // Update roster and consumed event atomically to prevent double awards.
        check(preferences.edit()
            .putString("roster_v1", encodeRoster(next))
            .putString("pet", petObject(updated).toString())
            .putStringSet("used_quests", used + result.eventId)
            .commit()) { "保存に失敗しました" }
        return updated
    }
}
