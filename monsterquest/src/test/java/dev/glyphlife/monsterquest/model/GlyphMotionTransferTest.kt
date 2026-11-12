package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterAnimation
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GlyphMotionTransferTest {
    private val wild=WildMonster(
        id="b044c444-fb9d-4cd1-9991-d21e77bc1dc8",
        species="霜翼の守護者",element="水",maxHp=80,attack=10,bodyType="AVIAN"
    )

    @Test fun projectFourNativeFramesSeparately() {
        val native=MonsterDesign.sprite(wild)
        val motion=MonsterAnimation.idle(native,wild)
        val source=motion.map { GlyphProjector.project(it.sprite) to it.durationMs }
        val data=JSONObject(QuestProtocol.exportGlyphAnimationFrames(wild,source))
        val frames=data.getJSONArray("frames")
        assertEquals(4,frames.length())
        assertEquals(listOf(500,250,140,250),(0 until 4).map {
            frames.getJSONObject(it).getInt("durationMs")
        })
        assertTrue((0 until 4).all {
            frames.getJSONObject(it).getString("pixels").length==169
        })
        assertTrue((0 until 4).all {
            val bits=frames.getJSONObject(it).getString("pixels")
            bits.withIndex().all { (index,c) -> c=='0' || GlyphProjector.isPhysical(index) }
        })
    }

    @Test fun impossibleDurationAndFrameCountAreRejected() {
        val glyph=GlyphProjector.project(MonsterDesign.sprite(wild))
        try {
            QuestProtocol.exportGlyphAnimationFrames(wild,listOf(glyph to 10))
            fail("duration")
        } catch (_:IllegalArgumentException) {}
        try {
            QuestProtocol.exportGlyphAnimationFrames(wild,emptyList())
            fail("empty")
        } catch (_:IllegalArgumentException) {}
        try {
            QuestProtocol.exportGlyphAnimationFrames(wild,List(65) { glyph to 100 })
            fail("too many frames")
        } catch (_:IllegalArgumentException) {}
    }
}
