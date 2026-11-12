package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GlyphAnimationProtocolTest {
    private val monster=WildMonster(
        id="e69a397b-15d5-4708-ab6f-4415ad3e4671",
        species="深雷竜", element="雷", maxHp=90, attack=12, bodyType="DRAGON"
    )

    @Test fun exportingNativeArtCreatesGlyphLifeCompatibleAnimation() {
        val bits=GlyphProjector.project(MonsterDesign.sprite(monster))
        val data=JSONObject(QuestProtocol.exportGlyphAnimation(monster,bits))
        assertEquals("glyph-life-animation",data.getString("type"))
        assertEquals("monster-quest",data.getString("source"))
        assertEquals(13,data.getInt("width"))
        assertEquals(13,data.getInt("height"))
        val frames=data.getJSONArray("frames")
        assertEquals(1,frames.length())
        assertEquals(bits,frames.getJSONObject(0).getString("pixels"))
        assertEquals(350,frames.getJSONObject(0).getInt("durationMs"))
    }

    @Test fun rejectsGlyphCellsNotWiredOnPhysicalDevice() {
        try {
            QuestProtocol.exportGlyphAnimation(monster,"1".repeat(169))
            fail("unwired pixels should be rejected")
        } catch (_: IllegalArgumentException) {}
    }
}
