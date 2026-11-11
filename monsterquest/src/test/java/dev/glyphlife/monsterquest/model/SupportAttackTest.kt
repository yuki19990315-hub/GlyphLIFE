package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SupportAttackTest {
    private val helper = WildMonster("helper-id", "アクア", "水", 40, 8)
    private val fireEnemy = WildMonster("enemy-id", "フレア", "火", 40, 4)

    private fun battle() = QuestState(
        inBattle = true, enemy = fireEnemy, enemyHp = fireEnemy.maxHp,
        playerHp = 80, captures = listOf(helper)
    )

    @Test fun supportAttackIsOncePerBattle() {
        val start = battle()
        val next = QuestRules.supportAttack(start, Random(1))
        assertTrue(next.supportUsedInBattle)
        assertTrue(next.enemyHp < start.enemyHp)
        assertTrue(next.playerHp < start.playerHp)
        assertEquals(next, QuestRules.supportAttack(next, Random(1)))
    }

    @Test fun elementalAdvantageAmplifiesAttack() {
        val strong = QuestRules.supportAttack(battle(), Random(0))
        val neutral = QuestRules.supportAttack(
            battle().copy(captures = listOf(helper.copy(element = "無"))), Random(0)
        )
        assertTrue(strong.enemyHp < neutral.enemyHp)
    }

    @Test fun unsupportedWithoutCaptureOrOutsideBattle() {
        val noCapture = battle().copy(captures = emptyList())
        assertEquals(noCapture, QuestRules.supportAttack(noCapture))
        val outside = battle().copy(inBattle = false)
        assertEquals(outside, QuestRules.supportAttack(outside))
    }

    @Test fun assistCanFinishRoom() {
        val strongHit = battle().copy(enemyHp = 6)
        val result = QuestRules.supportAttack(strongHit)
        assertEquals(0, result.enemyHp)
        assertTrue(result.roomCleared)
        assertFalse(result.inBattle)
        assertEquals(1, result.defeated)
    }

    @Test fun nextRoomResetsSupport() {
        val completedRoom = battle().copy(
            enemyHp = 0, inBattle = false, supportUsedInBattle = true, roomCleared = true
        )
        val next = QuestRules.nextRoom(completedRoom, Random(1))
        assertFalse(next.supportUsedInBattle)
        assertEquals(2, next.room)
    }
}
