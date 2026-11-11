package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QuestRulesTest {
    @Test fun pathToEnemyAlwaysRemainsPossible() {
        repeat(40) { seed ->
            val state = QuestRules.start(Companion(), Random(seed))
            assertTrue(listOf(0, 1, 2, 3, 7, 11, 15).none { it in state.walls })
            var player = state
            for (dest in listOf(1, 2, 3, 7, 11, 15)) player = QuestRules.move(player, dest)
            assertTrue(player.inBattle)
            assertEquals(15, player.position)
        }
    }

    @Test fun attackCannotOverflowHp() {
        var s = QuestRules.start(Companion(level = 50), Random(5))
        for (p in listOf(1, 2, 3, 7, 11, 15)) s = QuestRules.move(s, p)
        val after = QuestRules.attack(s, magic = true, random = Random(4))
        assertTrue(after.enemyHp >= 0)
        assertTrue(after.playerHp in 0..100)
    }

    @Test fun cannotMoveTwoCellsInOneTap() {
        val s = QuestRules.start(Companion(), Random(3))
        assertEquals(s.position, QuestRules.move(s, 2).position)
    }

    @Test fun noRewardBeforeEnd() {
        val state = QuestRules.start(Companion(), Random(3))
        try {
            QuestProtocol.rewardJson(state)
            fail("Expected failure")
        } catch (_: IllegalArgumentException) {}
        catch (_: IllegalStateException) {}
    }

    @Test fun rewardIdStableForRepeatedExport() {
        val companion = Companion(importedFromGlyphLife = true)
        val state = QuestRules.endRun(QuestRules.start(companion, Random(1)))
        assertEquals(QuestProtocol.rewardJson(state), QuestProtocol.rewardJson(QuestRules.endRun(state)))
    }
}
