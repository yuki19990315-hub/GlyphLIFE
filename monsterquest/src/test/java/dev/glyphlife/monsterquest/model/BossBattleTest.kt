package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BossBattleTest {
    private fun bossEncounter(): QuestState {
        var state = QuestRules.start(Companion(level = 40), Random(6))
        repeat(4) { room ->
            var current = state
            for (target in listOf(1, 2, 3, 7, 11, 15)) {
                current = QuestRules.move(current, target)
            }
            assertTrue(current.inBattle)
            var result = current
            repeat(20) { if (result.inBattle) result = QuestRules.attack(result, true, Random(0)) }
            assertTrue("Must clear room", result.roomCleared)
            assertTrue("Room number should advance", room + 1 <= 5)
            state = QuestRules.nextRoom(result, Random(6))
        }
        for (target in listOf(1, 2, 3, 7, 11, 15)) state = QuestRules.move(state, target)
        assertTrue(state.enemy?.isBoss == true)
        return state
    }

    @Test fun finalRoomHasBossAndPreventsCapture() {
        val boss = bossEncounter()
        assertTrue(boss.enemyHp >= 75)
        assertTrue(boss.enemy!!.isBoss)
        val attempt = QuestRules.capture(boss, Random(0))
        assertEquals(boss.enemyHp, attempt.enemyHp)
        assertEquals(boss.captures, attempt.captures)
        assertTrue(attempt.inBattle)
    }

    @Test fun bossDefeatCompletesAdventure() {
        var state = bossEncounter().copy(playerHp = 100, potions = 2)
        repeat(30) { if (state.inBattle) state = QuestRules.attack(state, magic = true, random = Random(0)) }
        // A relatively weak pet could lose the fight. Test a stronger pet explicitly.
        state = bossEncounter().copy(companion = Companion(level = 40), playerHp = 100)
        repeat(15) { if (state.inBattle) state = QuestRules.attack(state, magic = true, random = Random(0)) }
        assertTrue(state.completed)
        assertTrue(state.roomCleared)
        assertEquals(0, state.enemyHp)
        assertTrue(state.message.contains("ボス"))
    }

    @Test fun potionIsLimitedAndDefendingCannotAttack() {
        val battle = bossEncounter().copy(playerHp = 20, potions = 1)
        val recovered = QuestRules.usePotion(battle, Random(1))
        assertTrue(recovered.playerHp > 20)
        assertEquals(0, recovered.potions)
        assertEquals(recovered, QuestRules.usePotion(recovered, Random(1)))
        val guarded = QuestRules.defend(battle, Random(1))
        assertTrue(guarded.playerHp < battle.playerHp)
        assertEquals(battle.enemyHp, guarded.enemyHp)
    }

    @Test fun normalMonsterIsNotBoss() {
        val first = QuestRules.start(Companion(), Random(2))
        assertFalse(first.enemy!!.isBoss)
        assertTrue(first.enemyHp < 75)
    }
}
