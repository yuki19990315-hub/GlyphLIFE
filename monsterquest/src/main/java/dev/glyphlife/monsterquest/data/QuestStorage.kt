package dev.glyphlife.monsterquest.data

import android.content.Context
import dev.glyphlife.monsterquest.model.*
import org.json.JSONArray
import org.json.JSONObject

/** A small local JSON save for the standalone Monster Quest MVP. */
class QuestStorage(context: Context) {
    private val prefs = context.getSharedPreferences("monsterquest_v1", Context.MODE_PRIVATE)

    fun load(): QuestState {
        val raw = prefs.getString("session", null)
        if (raw == null) return QuestRules.start(Companion())
        return runCatching {
            val obj = JSONObject(raw)
            val p = obj.getJSONObject("companion")
            val pet = Companion(
                id = p.getString("id"), name = p.getString("name"),
                species = p.getString("species"), element = p.getString("element"),
                level = p.getInt("level"), glyph = p.getString("glyph"),
                importedFromGlyphLife = p.optBoolean("imported", false)
            )
            val wallsArray = obj.getJSONArray("walls")
            val walls = (0 until wallsArray.length()).map { wallsArray.getInt(it) }.toSet()
            val capturesArray = obj.getJSONArray("captures")
            val captures = (0 until capturesArray.length()).map { wildFromJson(capturesArray.getJSONObject(it)) }
            QuestState(
                companion = pet,
                room = obj.getInt("room").coerceIn(1, QuestRules.ROOM_COUNT),
                position = obj.getInt("position").coerceIn(0, 15),
                walls = walls,
                enemy = obj.optJSONObject("enemy")?.let(::wildFromJson),
                enemyHp = obj.optInt("enemyHp", 0).coerceAtLeast(0),
                playerHp = obj.getInt("playerHp").coerceIn(0, 100),
                potions = obj.optInt("potions", 2).coerceIn(0, 2),
                supportUsedInBattle = obj.optBoolean("supportUsedInBattle", false),
                inBattle = obj.optBoolean("inBattle", false),
                roomCleared = obj.optBoolean("roomCleared", false),
                completed = obj.optBoolean("completed", false),
                captures = captures.take(100),
                defeated = obj.optInt("defeated", 0).coerceIn(0, 1000),
                earnedXp = obj.optInt("earnedXp", 0).coerceIn(0, 100),
                earnedHappiness = obj.optInt("earnedHappiness", 0).coerceIn(0, 20),
                rewardEventId = obj.optString("rewardEventId").ifBlank { null },
                message = obj.optString("message", "冒険しよう").take(200)
            )
        }.getOrElse { QuestRules.start(Companion()) }
    }

    fun save(s: QuestState) {
        val data = JSONObject()
            .put("companion", JSONObject()
                .put("id", s.companion.id).put("name", s.companion.name)
                .put("species", s.companion.species).put("element", s.companion.element)
                .put("level", s.companion.level).put("glyph", s.companion.glyph)
                .put("imported", s.companion.importedFromGlyphLife))
            .put("room", s.room).put("position", s.position)
            .put("walls", JSONArray().apply { s.walls.forEach { put(it) } })
            .put("enemy", s.enemy?.let(::wildToJson))
            .put("enemyHp", s.enemyHp).put("playerHp", s.playerHp)
            .put("potions", s.potions)
            .put("supportUsedInBattle", s.supportUsedInBattle)
            .put("inBattle", s.inBattle).put("roomCleared", s.roomCleared)
            .put("completed", s.completed).put("defeated", s.defeated)
            .put("earnedXp", s.earnedXp).put("earnedHappiness", s.earnedHappiness)
            .put("rewardEventId", s.rewardEventId ?: "")
            .put("message", s.message)
            .put("captures", JSONArray().apply { s.captures.takeLast(100).forEach { put(wildToJson(it)) } })
        check(prefs.edit().putString("session", data.toString()).commit()) { "ゲームを保存できませんでした" }
    }

    private fun wildToJson(w: WildMonster) = JSONObject()
        .put("id", w.id).put("species", w.species).put("element", w.element)
        .put("maxHp", w.maxHp).put("attack", w.attack)
        .put("description", w.description).put("pixels", w.pixels)
        .put("isBoss", w.isBoss)
        .put("bodyType", w.bodyType).put("stage", w.stage)
        .put("lineage", w.lineage)
        .put("motifPrimary", w.motifPrimary).put("motifSecondary", w.motifSecondary)
        .put("designSeed", w.designSeed)
        .put("armorParts", JSONObject(w.armorParts))
        .put("customArmorParts", JSONObject(w.customArmorParts))
        .put("customArmorOffsets", JSONObject().apply {
            w.customArmorOffsets.forEach { (slot, offset) ->
                put(slot, JSONObject().put("dx", offset.dx).put("dy", offset.dy))
            }
        })

    private fun wildFromJson(o: JSONObject): WildMonster = WildMonster(
        id = o.getString("id"), species = o.getString("species"),
        element = o.getString("element"), maxHp = o.getInt("maxHp"),
        attack = o.getInt("attack"),
        description = o.optString("description", "").take(160),
        pixels = o.optString("pixels", "").take(169),
        isBoss = o.optBoolean("isBoss", false),
        bodyType = o.optString("bodyType", "").take(32),
        stage = o.optInt("stage", 1).coerceIn(1, 3),
        lineage = o.optString("lineage", "").take(130),
        motifPrimary = o.optString("motifPrimary", "").take(60),
        motifSecondary = o.optString("motifSecondary", "").take(60),
        designSeed = o.optString("designSeed", "").take(64),
        armorParts = readArmorParts(o.optJSONObject("armorParts")),
        customArmorParts = readCustomLayers(o.optJSONObject("customArmorParts")),
        customArmorOffsets = readCustomOffsets(o.optJSONObject("customArmorOffsets"))
    )

    private fun readArmorParts(value: JSONObject?): Map<String, String> {
        if(value == null) return emptyMap()
        return runCatching {
            val allowed = dev.glyphlife.monsterquest.pixel.ArmorSlot.entries
            val names = value.keys().asSequence().toList()
            dev.glyphlife.monsterquest.pixel.MonsterArmor.validated(
                names.filter { name -> allowed.any { it.name == name } }
                    .associateWith { name -> value.getString(name) }
            )
        }.getOrDefault(emptyMap())
    }

    private fun readCustomLayers(value: JSONObject?): Map<String, String> {
        if(value == null) return emptyMap()
        return runCatching {
            val result = value.keys().asSequence()
                .filter { key -> dev.glyphlife.monsterquest.pixel.ArmorSlot.entries
                    .any { it.name == key } }
                .associateWith { key -> value.getString(key) }
            dev.glyphlife.monsterquest.pixel.CustomArmorLayers.validated(result)
        }.getOrDefault(emptyMap())
    }
    private fun readCustomOffsets(
        value: JSONObject?
    ): Map<String, dev.glyphlife.monsterquest.pixel.ArmorOffset> {
        if (value == null) return emptyMap()
        return dev.glyphlife.monsterquest.pixel.ArmorSlot.entries.mapNotNull { slot ->
            val pos = value.optJSONObject(slot.name) ?: return@mapNotNull null
            val offset = runCatching {
                dev.glyphlife.monsterquest.pixel.ArmorOffset(
                    pos.getInt("dx"), pos.getInt("dy"))
            }.getOrNull() ?: return@mapNotNull null
            slot.name to offset
        }.toMap()
    }

}
