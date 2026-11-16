package dev.glyphlife.monsterquest.pixel

import org.junit.Assert.*
import org.junit.Test

/** Pixel compliance for the replacement sources actually used by the game. */
class CuratedMonsterRepairsTest {
    @Test fun everyReconstructedMasterHasFullEightPixelRimAndSixteenColors() {
        val repaired = CuratedMonsterRepairs.indexes()
        assertEquals("Every missing-head source should have a repair", 27, repaired.size)
        assertTrue("Flare stage 1 must use the repaired source", 3 in repaired)
        assertTrue("Flare stage 2 must use the repaired source", 4 in repaired)
        assertTrue("Flare stage 3 must use the repaired source", 5 in repaired)
        for (index in repaired) {
            val sprite = CuratedMonsterRepairs.find(index)!!
            val audit = GbaMonsterArtStandard.audit(sprite)
            assertTrue("64x64/16-color PNG compliance failed at $index", audit.compliant)
            val bounds = audit.opaqueBounds!!
            assertTrue("left margin $index", bounds.left >= 8)
            assertTrue("top margin $index", bounds.top >= 8)
            assertTrue("right margin $index", bounds.right <= 55)
            assertTrue("bottom margin $index", bounds.bottom <= 55)
        }
    }

    @Test fun unchangedEyeArtFallsBackToOriginalMaster() {
        assertNull(CuratedMonsterRepairs.find(15))
        assertNull(CuratedMonsterRepairs.find(16))
        assertNull(CuratedMonsterRepairs.find(17))
    }
}
