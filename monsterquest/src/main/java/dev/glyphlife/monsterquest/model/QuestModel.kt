package dev.glyphlife.monsterquest.model

import java.util.UUID
import kotlin.random.Random

/** Glyph Life PET export snapshot. Its fields are descriptive, not trusted game authority. */
data class Companion(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "ルナ",
    val species: String = "精霊",
    val element: String = "光",
    val level: Int = 1,
    val glyph: String = "0".repeat(169),
    val importedFromGlyphLife: Boolean = false
)

data class WildMonster(
    val id: String,
    val species: String,
    val element: String,
    val maxHp: Int,
    val attack: Int,
    val description: String = "",
    val pixels: String = ""
)

data class QuestState(
    val companion: Companion = Companion(),
    val room: Int = 1,
    val position: Int = 0,
    val walls: Set<Int> = emptySet(),
    val enemy: WildMonster? = null,
    val enemyHp: Int = 0,
    val playerHp: Int = 100,
    val inBattle: Boolean = false,
    val roomCleared: Boolean = false,
    val completed: Boolean = false,
    val captures: List<WildMonster> = emptyList(),
    val defeated: Int = 0,
    val earnedXp: Int = 0,
    val earnedHappiness: Int = 0,
    val rewardEventId: String? = null,
    val message: String = "冒険を始めよう！"
)

object QuestRules {
    const val ROOM_COUNT = 5
    const val BOARD_SIZE = 4

    fun start(companion: Companion, random: Random = Random.Default): QuestState =
        generateRoom(QuestState(companion = companion), random)

    private fun generateRoom(state: QuestState, random: Random): QuestState {
        val safe = setOf(0, 1, 2, 3, 7, 11, 15)
        val walls = (0..15).filter { it !in safe && random.nextInt(100) < 28 }.toSet()
        val kinds = listOf(
            "モコ" to "草", "フレア" to "火", "アクア" to "水",
            "ギア" to "雷", "ルーン" to "闇"
        )
        val picked = kinds[random.nextInt(kinds.size)]
        val wild = WildMonster(
            id = UUID.randomUUID().toString(),
            species = picked.first, element = picked.second,
            maxHp = 17 + state.room * 7,
            attack = 3 + state.room
        )
        return state.copy(
            position = 0, walls = walls, enemy = wild, enemyHp = wild.maxHp,
            inBattle = false, roomCleared = false, completed = false,
            message = "部屋${state.room}：モンスターを探そう"
        )
    }

    fun move(state: QuestState, destination: Int): QuestState {
        if (state.inBattle || state.roomCleared || state.completed) return state
        if (destination !in 0..15 || destination in state.walls) return state
        val sx = state.position % BOARD_SIZE
        val sy = state.position / BOARD_SIZE
        val x = destination % BOARD_SIZE
        val y = destination / BOARD_SIZE
        if (kotlin.math.abs(x - sx) + kotlin.math.abs(y - sy) != 1) return state
        val encounter = destination == 15
        return state.copy(
            position = destination,
            inBattle = encounter,
            message = if (encounter) "${state.enemy?.species ?: "敵"}が現れた！"
                      else "部屋の中を移動した"
        )
    }

    private fun advantage(element: String, target: String): Boolean =
        (element == "水" && target == "火") ||
        (element == "火" && target == "草") ||
        (element == "草" && target == "水") ||
        (element == "光" && target == "闇") ||
        (element == "闇" && target == "光")

    fun attack(state: QuestState, magic: Boolean, random: Random = Random.Default): QuestState {
        if (!state.inBattle) return state
        val wild = state.enemy ?: return state
        val base = 5 + state.companion.level.coerceAtMost(30) +
            (if (magic) 3 else 0) + random.nextInt(4)
        val damage = if (magic && advantage(state.companion.element, wild.element)) base * 2 else base
        val remaining = (state.enemyHp - damage).coerceAtLeast(0)
        if (remaining == 0) return finishRoom(state.copy(enemyHp = 0), captured = false)
        val damageTaken = wild.attack + random.nextInt(3)
        val hp = (state.playerHp - damageTaken).coerceAtLeast(0)
        if (hp == 0) return state.copy(
            playerHp = 100, inBattle = false, completed = true,
            message = "力尽きた。冒険終了。報酬を戻してから再挑戦できます"
        )
        return state.copy(
            enemyHp = remaining, playerHp = hp,
            message = "敵に${damage}ダメージ、反撃で${damageTaken}ダメージ"
        )
    }

    fun capture(state: QuestState, random: Random = Random.Default): QuestState {
        val wild = state.enemy ?: return state
        if (!state.inBattle) return state
        val hpRatio = state.enemyHp.toFloat() / wild.maxHp.toFloat()
        val chance = (75 - hpRatio * 55).toInt().coerceIn(15, 80)
        if (random.nextInt(100) < chance) {
            return finishRoom(state.copy(
                captures = state.captures + wild, enemyHp = 0
            ), captured = true)
        }
        val hp = (state.playerHp - wild.attack).coerceAtLeast(0)
        return if (hp == 0) state.copy(
            playerHp = 100, inBattle = false, completed = true, message = "捕獲失敗。冒険終了"
        ) else state.copy(playerHp = hp, message = "捕獲失敗！ HP -${wild.attack}")
    }

    private fun finishRoom(state: QuestState, captured: Boolean): QuestState {
        val end = state.room >= ROOM_COUNT
        return state.copy(
            inBattle = false, roomCleared = true, completed = end,
            defeated = state.defeated + 1,
            earnedXp = (state.earnedXp + 12).coerceAtMost(100),
            earnedHappiness = (state.earnedHappiness + 2).coerceAtMost(20),
            message = if (captured) "仲間を捕獲した！"
                      else if (end) "5部屋を突破！ 冒険クリア！"
                      else "敵を倒した！ 次の部屋へ"
        )
    }

    fun summon(state: QuestState, wild: WildMonster): QuestState {
        if (state.inBattle || state.completed || state.roomCleared) return state
        return state.copy(
            enemy = wild, enemyHp = wild.maxHp, position = 15, inBattle = true,
            message = "AI生成の${wild.species}が現れた！"
        )
    }

    fun nextRoom(state: QuestState, random: Random = Random.Default): QuestState {
        if (!state.roomCleared || state.completed) return state
        return generateRoom(state.copy(room = state.room + 1), random)
    }

    fun endRun(state: QuestState): QuestState {
        if (state.rewardEventId != null) return state
        return state.copy(completed = true, inBattle = false,
            rewardEventId = UUID.randomUUID().toString())
    }
}
