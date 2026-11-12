package dev.glyphlife.monsterquest.model

import org.json.JSONObject
import dev.glyphlife.monsterquest.pixel.GlyphProjector
import java.util.UUID

/** Both applications exchange simple JSON through Android ACTION_SEND text. */
object QuestProtocol {
    fun parseGlyphPet(raw: String): Companion {
        require(raw.length in 1..50_000) { "データが長すぎます" }
        val obj = JSONObject(raw)
        require(obj.getInt("schemaVersion") == 1 && obj.getString("source") == "glyph-life")
        val pet = obj.getJSONObject("pet")
        val glyph = obj.getJSONObject("glyph")
        require(glyph.getInt("width") == 13 && glyph.getInt("height") == 13)
        val pixels = glyph.getString("pixels")
        require(pixels.length == 169 && pixels.all { it == '0' || it == '1' })
        val id = pet.getString("id")
        UUID.fromString(id)
        val name = pet.getString("name")
        val species = pet.getString("species")
        val element = pet.getString("element")
        val level = pet.getInt("level")
        require(name.length in 1..50 && species.length in 1..50 && element.length in 1..30)
        require(level in 1..200)
        return Companion(id, name, species, element, level, pixels, true)
    }

    /** Backwards-compatible static frame export. */
    fun exportGlyphAnimation(wild: WildMonster, pixels13: String): String =
        exportGlyphAnimationFrames(wild, listOf(pixels13 to 350))

    /**
     * Derivative animation format shared with GlyphLIFE Creator.
     * Each 64px source frame must be projected independently before calling.
     */
    fun exportGlyphAnimationFrames(
        wild: WildMonster,
        sourceFrames: List<Pair<String, Int>>,
        brightnessFrames: List<IntArray>? = null
    ): String {
        require(sourceFrames.size in 1..64)
        val frames = org.json.JSONArray()
        require(brightnessFrames == null || brightnessFrames.size == sourceFrames.size)
        sourceFrames.forEachIndexed { frameIndex, (pixels13, durationMs) ->
            require(pixels13.length == 169 && pixels13.all { it == '0' || it == '1' })
            require(pixels13.withIndex().all { (i, bit) ->
                bit != '1' || GlyphProjector.isPhysical(i)
            }) { "Glyph実LEDの範囲外に画素があります" }
            require(durationMs in 100..2000) { "コマの再生時間が範囲外です" }
            val frame = JSONObject().put("pixels", pixels13).put("durationMs", durationMs)
            brightnessFrames?.get(frameIndex)?.let { brightness ->
                require(brightness.size == 169 && brightness.all { it in 0..255 })
                require(brightness.withIndex().all { (i, level) ->
                    level == 0 || GlyphProjector.isPhysical(i)
                }) { "物理LEDの範囲外に輝度があります" }
                // Optional v1 extension. Legacy consumers ignore unknown JSON fields.
                // Two hex characters per LED -> 338 chars per 13x13 frame.
                val encoded = brightness.joinToString("") { level ->
                    level.toString(16).padStart(2, '0')
                }
                frame.put("levelsHex", encoded)
            }
            frames.put(frame)
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("type", "glyph-life-animation")
            .put("source", "monster-quest")
            .put("monsterName", wild.species.take(50))
            .put("width", 13).put("height", 13).put("selected", 0)
            .put("frames", frames)
            .toString(2)
    }

    fun rewardJson(state: QuestState): String {
        require(state.companion.importedFromGlyphLife)
        val eventId = state.rewardEventId ?: error("先に冒険を終了してください")
        UUID.fromString(eventId)
        return JSONObject()
            .put("schemaVersion", 1)
            .put("source", "monster-quest")
            .put("eventId", eventId)
            .put("petId", state.companion.id)
            .put("experience", state.earnedXp.coerceIn(0, 100))
            .put("happiness", state.earnedHappiness.coerceIn(-20, 20))
            .toString(2)
    }
}
