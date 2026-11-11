package dev.glyphlife.monsterquest.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuestProtocolTest {
    private val petId = "467437c1-7e85-4755-aeca-b23b09f6fd12"
    private val frame = "0".repeat(169)

    private fun snapshot() = JSONObject()
        .put("schemaVersion", 1).put("source", "glyph-life")
        .put("pet", JSONObject()
            .put("id", petId).put("name", "ピコ").put("species", "スライム")
            .put("element", "水").put("level", 3))
        .put("glyph", JSONObject().put("width", 13).put("height", 13).put("pixels", frame))

    @Test fun importAcceptsGlyphLifePetSnapshot() {
        val pet = QuestProtocol.parseGlyphPet(snapshot().toString())
        assertEquals(petId, pet.id)
        assertEquals("ピコ", pet.name)
        assertEquals(169, pet.glyph.length)
        assertTrue(pet.importedFromGlyphLife)
    }

    @Test fun importRejectsMalformedMatrix() {
        val s = snapshot()
        s.getJSONObject("glyph").put("pixels", "0101")
        try {
            QuestProtocol.parseGlyphPet(s.toString())
            fail("Must reject mismatched size")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun importRejectsUnrelatedSource() {
        val s = snapshot().put("source", "other")
        try {
            QuestProtocol.parseGlyphPet(s.toString())
            fail("Must reject unrelated JSON")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun exportHasExactGlyphLifeRewardContract() {
        val pet = QuestProtocol.parseGlyphPet(snapshot().toString())
        val session = QuestRules.endRun(QuestRules.start(pet))
        val json = JSONObject(QuestProtocol.rewardJson(session))
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("monster-quest", json.getString("source"))
        assertEquals(petId, json.getString("petId"))
        assertEquals(session.rewardEventId, json.getString("eventId"))
        assertEquals(0, json.getInt("experience"))
        assertEquals(0, json.getInt("happiness"))
    }
}
