package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class MonsterAnimationTest {
    private val monster=WildMonster(
        "d54fe5ca-8f8b-4b43-a937-74369357a6ce",
        "月蝕の竜","闇",60,10,bodyType="DRAGON"
    )

    @Test fun idleAnimationsAre64PixelFullColor() {
        val master=MonsterDesign.sprite(monster)
        val frames=MonsterAnimation.idle(master,monster)
        assertEquals(4,frames.size)
        assertEquals(500,frames[0].durationMs)
        assertTrue(frames.all { it.durationMs in 100..1000 })
        assertTrue(frames.all { it.sprite.width==64 && it.sprite.height==64 })
        assertTrue(frames.map { it.sprite.pixels().toList() }.distinct().size>=3)
        assertTrue(frames.all { it.sprite.colors().size<=16 })
    }

    @Test fun attackChangesPoseAndColorsStayLimited() {
        val master=MonsterDesign.sprite(monster)
        val attack=MonsterAnimation.attack(master,monster)
        assertEquals(4,attack.size)
        assertTrue(attack[1].sprite.opaqueCount()>0)
        assertFalse(attack[0].sprite.pixels().contentEquals(attack[1].sprite.pixels()))
        assertEquals(master.pixels().toList(),attack.last().sprite.pixels().toList())
        assertTrue(attack.all { it.sprite.colors().size <= 15 })
        assertTrue(attack.all { it.sprite.pixel(0,0)==0 })
    }

    @Test fun spriteSheetIsARealFourFramePixelAsset() {
        val frames=MonsterAnimation.idle(MonsterDesign.sprite(monster),monster)
        val sheet=MonsterAnimation.sheet(frames)
        assertEquals(256,sheet.width)
        assertEquals(64,sheet.height)
        assertEquals(frames.first().sprite.pixel(30,30),sheet.pixel(30,30))
        assertEquals(frames[1].sprite.pixel(30,30),sheet.pixel(64+30,30))
    }

    @Test fun noGlyphDownscaleOccursBeforeAnimation() {
        val frames=MonsterAnimation.idle(MonsterDesign.sprite(monster),monster)
        assertTrue(frames.all { it.sprite.pixels().size==4096 })
        val converted=frames.map { GlyphProjector.project(it.sprite) }
        assertTrue(converted.all { it.length==169 })
        assertTrue(converted.all { str ->
            str.withIndex().all { (i,c)-> c=='0'||GlyphProjector.isPhysical(i) }
        })
    }
}
