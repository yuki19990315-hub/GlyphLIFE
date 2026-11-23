package dev.glyphlife.monsterquest.ai

import dev.glyphlife.monsterquest.pixel.GbaMonsterArtStandard
import dev.glyphlife.monsterquest.pixel.PixelSprite
import org.junit.Assert.*
import org.junit.Test

class MonsterWorkshopTest {
    private val idea = MonsterIdea("雷狐", "雷", "雷を蓄える獣")
    private fun draft(art: MonsterWorkshop.Art?) =
        MonsterWorkshopDraft(idea, idea.asWildMonster(1)).let { base ->
            if (art == null) base else base.addCandidate(art)
        }

    @Test fun noImageIsClearlyAnOptionalPlaceholder() {
        val candidate = draft(null)
        assertTrue(candidate.readyForApproval)
        assertNull(candidate.art)
    }

    @Test fun compliantNativeArtCanBeReviewed() {
        val colors = IntArray(4096)
        for (y in 10..45) for (x in 14..46) colors[y*64+x] = 0xff336699.toInt()
        val sprite = PixelSprite.fromArgb(64,64,colors)
        val audit = GbaMonsterArtStandard.audit(sprite)
        assertTrue(audit.compliant)
        assertTrue(draft(MonsterWorkshop.Art(sprite,byteArrayOf(1),audit)).readyForApproval)
    }

    @Test fun fourAlternativesCanBeComparedWithoutLosingEarlierResults() {
        val colors = IntArray(4096)
        colors[20*64+20] = 0xff336699.toInt()
        val sprite = PixelSprite.fromArgb(64,64,colors)
        val audit = GbaMonsterArtStandard.audit(sprite)
        val art = MonsterWorkshop.Art(sprite,byteArrayOf(1),audit)
        val four = (1..4).fold(draft(null)) { current, _ ->
            current.addCandidate(art)
        }
        assertEquals(4, four.variants.size)
        assertEquals(3, four.selectedIndex)
        assertFalse(four.canGenerateMore)
        assertEquals(0, four.choose(0).selectedIndex)
        assertEquals(4, four.choose(0).variants.size)
        try {
            four.addCandidate(art)
            fail("Fifth candidate should be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun opaqueFullCanvasIsRejected() {
        val sprite = PixelSprite.fromArgb(64,64,IntArray(4096){ 0xff112233.toInt() })
        val audit = GbaMonsterArtStandard.audit(sprite)
        assertFalse(audit.compliant)
        assertFalse(draft(MonsterWorkshop.Art(sprite,byteArrayOf(1),audit)).readyForApproval)
    }
}
