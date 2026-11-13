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
    val pixels: String = "",
    val isBoss: Boolean = false,
    /** Native game art metadata; legacy saves get default values. */
    val bodyType: String = "",
    val stage: Int = 1,
    /** Personal growth; no longer unlocked by another monster's victories. */
    val level: Int = 1,
    val xp: Int = 0,
    val victories: Int = 0,
    val lineage: String = "",
    val motifPrimary: String = "",
    val motifSecondary: String = "",
    /** Stable rendering identity, independent of random encounter UUID. Legacy default preserves art. */
    val designSeed: String = "",
    /** Immutable authored-art identity; empty means the legacy procedural generator. */
    val artFamily: String = "",
    /** Optional safe named overlays. Empty for legacy saves, preserving appearance. */
    val armorParts: Map<String, String> = emptyMap(),
    /** Slot -> immutable UUID for imported/generated transparent PNG armor layer. */
    val customArmorParts: Map<String, String> = emptyMap(),
    /** Per-slot alignment of imported AI PNG modules, in native 64px coordinates. */
    val customArmorOffsets:
        Map<String, dev.glyphlife.monsterquest.pixel.ArmorOffset> = emptyMap(),
    /** Shared artwork owner UUID for AI-generated enemies; never a network URL. */
    val artSourceId: String = ""
)

/** Only explicit, opt-in featured art families use authored sprites. Blank keeps legacy procedural art. */
object MonsterArtFamilies {
    const val RAIZEL = "raizel"
    const val LUNA = "luna"
}

/** The built-in demo companion gets a matching authored art style.
 * Linked GlyphLIFE companions retain their previous rendering behavior.
 */
fun Companion.gameArtFamily(): String =
    if (!importedFromGlyphLife && name == "ルナ" && species == "精霊" && element == "光")
        MonsterArtFamilies.LUNA
    else ""

data class QuestState(
    val companion: Companion = Companion(),
    /** Tower and arena are separate from the main exploration session. */
    val challenge: ChallengeState = ChallengeState(),
    /** Collectible boss equipment and its unique one-owner assignments. */
    val bossRelics:List<BossRelic> = emptyList(),
    val relicEquipment:Map<String,String> = emptyMap(),
    /** Counts consecutive boss victories without a relic; the fourth guarantees. */
    val relicDryBosses:Int = 0,
    val companionXp: Int = 0,
    val attackUpgrade: Int = 0,
    val defenseUpgrade: Int = 0,
    val bossWins: Int = 0,
    val achievementsClaimed: Set<String> = emptySet(),
    val room: Int = 1,
    val position: Int = 0,
    val walls: Set<Int> = emptySet(),
    val enemy: WildMonster? = null,
    val enemyHp: Int = 0,
    val playerHp: Int = 100,
    val potions: Int = 2,
    val supportUsedInBattle: Boolean = false,
    val inBattle: Boolean = false,
    val roomCleared: Boolean = false,
    val completed: Boolean = false,
    val captures: List<WildMonster> = emptyList(),
    val defeated: Int = 0,
    val earnedXp: Int = 0,
    val earnedHappiness: Int = 0,
    val rewardEventId: String? = null,
    /** New tap adventure state; legacy 5-room game fields remain valid. */
    val tapRun: TapRun = TapRun(),
    val summonStones: Int = 0,
    val captureOrbs: Int = 3,
    val catalog: List<WildMonster> = emptyList(),
    val partyIds: List<String> = emptyList(),
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
            "ギア" to "雷", "ルーン" to "闇", "ライゼル" to "雷"
        )
        val bossRoom = state.room == ROOM_COUNT
        val bosses = listOf(
            "月蝕の竜" to "闇", "古代の歯車王" to "雷", "星光の守護者" to "光"
        )
        val picked = if (bossRoom) bosses[random.nextInt(bosses.size)]
                     else kinds[random.nextInt(kinds.size)]
        val wild = WildMonster(
            id = UUID.randomUUID().toString(),
            species = picked.first, element = picked.second,
            maxHp = if (bossRoom) 75 + random.nextInt(21) else 17 + state.room * 7,
            attack = if (bossRoom) 9 else 3 + state.room,
            description = if (bossRoom) "最終階層に現れた強大な守護者。" else "",
            isBoss = bossRoom
        )
        return state.copy(
            position = 0, walls = walls, enemy = wild, enemyHp = wild.maxHp,
            inBattle = false, supportUsedInBattle = false,
            roomCleared = false, completed = false,
            message = if (bossRoom) "最終階層：ボス${wild.species}を倒そう！"
                      else "部屋${state.room}：モンスターを探そう"
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
        val enraged = wild.isBoss && remaining <= wild.maxHp / 2
        val damageTaken = wild.attack + (if (enraged) 4 else 0) + random.nextInt(3)
        val hp = (state.playerHp - damageTaken).coerceAtLeast(0)
        if (hp == 0) return state.copy(
            playerHp = 0, inBattle = false, completed = true,
            message = "力尽きた。冒険終了。報酬を確定してから再挑戦できます"
        )
        return state.copy(
            enemyHp = remaining, playerHp = hp,
            message = "敵に${damage}ダメージ、反撃で${damageTaken}ダメージ"
        )
    }

    fun capture(state: QuestState, random: Random = Random.Default): QuestState {
        val wild = state.enemy ?: return state
        if (!state.inBattle) return state
        if (wild.isBoss) return state.copy(message = "ボスは捕獲できない！ 戦って倒そう")
        if (state.captures.size >= 100)
            return state.copy(message = "仲間は最大100匹です。先に合成して空きを作ろう")
        val hpRatio = state.enemyHp.toFloat() / wild.maxHp.toFloat()
        val chance = (75 - hpRatio * 55).toInt().coerceIn(15, 80)
        if (random.nextInt(100) < chance) {
            return finishRoom(state.copy(
                captures = state.captures + wild, enemyHp = 0
            ), captured = true)
        }
        val hp = (state.playerHp - wild.attack).coerceAtLeast(0)
        return if (hp == 0) state.copy(
            playerHp = 0, inBattle = false, completed = true, message = "捕獲失敗。冒険終了"
        ) else state.copy(playerHp = hp, message = "捕獲失敗！ HP -${wild.attack}")
    }

    /**
     * Once per encounter, a previously captured monster can assist the active
     * GlyphLIFE pet. The original companion UUID and rewards are unchanged.
     */
    fun supportAttack(state: QuestState, random: Random = Random.Default): QuestState {
        if (!state.inBattle || state.supportUsedInBattle) return state
        val assistant = state.captures.lastOrNull() ?: return state
        val wild = state.enemy ?: return state
        val baseDamage = 4 + assistant.attack.coerceIn(1, 30)
        val damage = if (advantage(assistant.element, wild.element)) baseDamage * 2
                     else baseDamage
        val remaining = (state.enemyHp - damage).coerceAtLeast(0)
        val assisted = state.copy(
            enemyHp = remaining,
            supportUsedInBattle = true
        )
        if (remaining == 0) return finishRoom(assisted, captured = false)
        val taken = wild.attack + random.nextInt(2)
        val hp = (state.playerHp - taken).coerceAtLeast(0)
        return if (hp == 0) assisted.copy(
            playerHp = 0, inBattle = false, completed = true,
            message = "${assistant.species}が援護したが、敵の反撃で倒れた"
        ) else assisted.copy(
            playerHp = hp,
            message = "${assistant.species}の援護！ 敵に${damage}ダメージ、反撃で${taken}ダメージ"
        )
    }

    /** Uses a limited item; healing is followed by a counterattack. */
    fun usePotion(state: QuestState, random: Random = Random.Default): QuestState {
        if (!state.inBattle || state.potions <= 0) return state
        val wild = state.enemy ?: return state
        val healed = (state.playerHp + 40).coerceAtMost(100)
        val taken = wild.attack / 2 + random.nextInt(2)
        return state.copy(
            potions = state.potions - 1,
            playerHp = (healed - taken).coerceAtLeast(1),
            message = "回復薬でHP回復、敵の反撃を受けた（残り回復薬${state.potions - 1}）"
        )
    }

    /** Defend halves a single incoming attack without changing offensive state. */
    fun defend(state: QuestState, random: Random = Random.Default): QuestState {
        if (!state.inBattle) return state
        val wild = state.enemy ?: return state
        val taken = (wild.attack + random.nextInt(3)) / 2
        val hp = (state.playerHp - taken).coerceAtLeast(0)
        return if (hp == 0) state.copy(inBattle = false, completed = true,
            playerHp = 0, message = "力尽きた。冒険終了")
        else state.copy(playerHp = hp, message = "防御した！ 受けたダメージ${taken}")
    }

    private fun finishRoom(state: QuestState, captured: Boolean): QuestState {
        val end = state.room >= ROOM_COUNT
        return state.copy(
            inBattle = false, roomCleared = true, completed = end,
            defeated = state.defeated + 1,
            earnedXp = (state.earnedXp + (if (state.enemy?.isBoss == true) 30 else 12))
                .coerceAtMost(100),
            earnedHappiness = (state.earnedHappiness + 2).coerceAtMost(20),
            message = if (end) "ボスを撃破！ 全5部屋を攻略した！"
                      else if (captured) "仲間を捕獲した！"
                      else "敵を倒した！ 次の部屋へ"
        )
    }

    fun summon(state: QuestState, wild: WildMonster): QuestState {
        if (state.inBattle || state.completed || state.roomCleared || state.room == ROOM_COUNT)
            return state
        return state.copy(
            enemy = wild, enemyHp = wild.maxHp, position = 15, inBattle = true,
            supportUsedInBattle = false,
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
