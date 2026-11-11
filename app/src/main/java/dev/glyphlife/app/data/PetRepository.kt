package dev.glyphlife.app.data

import android.content.Context
import dev.glyphlife.app.model.PetState
import dev.glyphlife.app.model.PixelArt
import org.json.JSONObject

/** MVP storage. Room + migrations are planned for multi-pet support. */
class PetRepository(context: Context) {
    private val preferences = context.getSharedPreferences("glyph_life_v1", Context.MODE_PRIVATE)

    fun loadPet(): PetState {
        val raw = preferences.getString("pet", null) ?: return PetState()
        return runCatching {
            val obj = JSONObject(raw)
            PetState(
                id = obj.getString("id"),
                name = obj.optString("name", "ピコ"),
                species = obj.optString("species", "スライム"),
                element = obj.optString("element", "水"),
                personality = obj.optString("personality", "好奇心旺盛"),
                level = obj.optInt("level", 1).coerceAtLeast(1),
                experience = obj.optInt("experience", 0).coerceIn(0, 99),
                happiness = obj.optInt("happiness", 60).coerceIn(0, 100),
                energy = obj.optInt("energy", 80).coerceIn(0, 100),
            )
        }.getOrDefault(PetState())
    }

    fun savePet(pet: PetState) {
        val obj = JSONObject()
            .put("id", pet.id).put("name", pet.name)
            .put("species", pet.species).put("element", pet.element)
            .put("personality", pet.personality)
            .put("level", pet.level).put("experience", pet.experience)
            .put("happiness", pet.happiness).put("energy", pet.energy)
        preferences.edit().putString("pet", obj.toString()).apply()
    }

    fun loadPixels(): List<Boolean> {
        val raw = preferences.getString("pixels", null)
        return if (raw != null && raw.length == PixelArt.COUNT && raw.all { it == '0' || it == '1' }) {
            raw.map { it == '1' }
        } else PixelArt.defaultPet()
    }

    fun savePixels(pixels: List<Boolean>) {
        require(pixels.size == PixelArt.COUNT)
        preferences.edit().putString("pixels", pixels.joinToString("") { if (it) "1" else "0" }).apply()
    }

    fun exportPet(pet: PetState, pixels: List<Boolean>): String {
        require(pixels.size == PixelArt.COUNT)
        val obj = JSONObject()
            .put("schemaVersion", 1)
            .put("source", "glyph-life")
            .put("pet", JSONObject()
                .put("id", pet.id).put("name", pet.name)
                .put("species", pet.species).put("element", pet.element)
                .put("personality", pet.personality)
                .put("level", pet.level).put("experience", pet.experience)
                .put("happiness", pet.happiness).put("energy", pet.energy))
            .put("glyph", JSONObject()
                .put("width", PixelArt.WIDTH)
                .put("height", PixelArt.HEIGHT)
                .put("pixels", pixels.joinToString("") { if (it) "1" else "0" }))
        return obj.toString(2)
    }
}
