package dev.glyphlife.monsterquest.model

import java.util.UUID
import kotlin.random.Random

/** Collectible combat equipment earned ONLY from actual boss victories. */
data class BossRelic(
    val id:String,
    val kind:String,
    val source:String,
    val floor:Int
)
data class RelicStats(
    val attack:Int=0,
    val defense:Int=0,
    val critical:Int=0,
    val heal:Int=0,
    val charge:Int=0
)
data class RelicSpec(
    val kind:String,
    val name:String,
    val effect:String,
    val stats:RelicStats
)

object RelicRules {
    const val INVENTORY_LIMIT=80
    const val DROP_PERCENT=35
    const val PITY_LIMIT=4

    val catalog=listOf(
        RelicSpec("moonfang","月蝕の牙","攻撃+6",RelicStats(attack=6)),
        RelicSpec("clockgear","雷機の歯車","会心率+8%",RelicStats(critical=8)),
        RelicSpec("starseal","星護の印章","被ダメージ-4",RelicStats(defense=4)),
        RelicSpec("abysscore","深淵の核","攻撃+4・防御+2",
            RelicStats(attack=4,defense=2)),
        RelicSpec("lifepearl","生命の珠","勝利時HP+8",RelicStats(heal=8)),
        RelicSpec("spiritclock","秘術の時計","固有スキル再使用までの通常攻撃-1",
            RelicStats(charge=1))
    )
    fun spec(kind:String):RelicSpec?=catalog.firstOrNull {it.kind==kind}
    fun isBattle(state:QuestState):Boolean =
        state.inBattle || state.tapRun.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS) ||
            ChallengeRules.active(state)

    fun activeOwners(state:QuestState):Set<String> =
        (listOf(state.companion.id)+state.partyIds).toSet()

    /** Only one unique instance per owner; the same instance can never stack twice. */
    fun activeRelics(state:QuestState):List<BossRelic> {
        val owners=activeOwners(state)
        val ids=state.relicEquipment.filterKeys {it in owners}.values.toSet()
        return state.bossRelics.filter {it.id in ids}
    }
    fun stats(state:QuestState):RelicStats {
        val list=activeRelics(state).mapNotNull{spec(it.kind)?.stats}
        return RelicStats(
            attack=list.sumOf{it.attack}.coerceAtMost(25),
            defense=list.sumOf{it.defense}.coerceAtMost(14),
            critical=list.sumOf{it.critical}.coerceAtMost(32),
            heal=list.sumOf{it.heal}.coerceAtMost(24),
            charge=list.sumOf{it.charge}.coerceAtMost(3)
        )
    }

    fun equip(state:QuestState,ownerId:String,relicId:String):QuestState {
        if(isBattle(state))return state
        if(ownerId !in (state.captures.map{it.id}+state.companion.id))return state
        val relic=state.bossRelics.firstOrNull {it.id==relicId} ?: return state
        // Transfer the instance from the previous owner if necessary.
        val next=state.relicEquipment.filterValues{it!=relicId}+(ownerId to relicId)
        return state.copy(relicEquipment=next,
            tapRun=state.tapRun.copy(message=
                relicName(relic)+"を"+ownerName(state,ownerId)+"に装備した"))
    }

    fun unequip(state:QuestState,ownerId:String):QuestState {
        if(isBattle(state) || ownerId !in state.relicEquipment)return state
        return state.copy(relicEquipment=state.relicEquipment-ownerId,
            tapRun=state.tapRun.copy(message="ボス装備を外した"))
    }

    private fun ownerName(state:QuestState,id:String):String =
        if(id==state.companion.id)state.companion.name
        else state.captures.firstOrNull{it.id==id}?.species ?: "仲間"

    fun relicName(relic:BossRelic):String =
        spec(relic.kind)?.name ?: "未知の遺物"

    /** 35% per boss; guarantee an item by the fourth consecutive dry boss.
     *  No rolls occur on non-boss wins or while the bag is full. */
    fun reward(state:QuestState,foe:WildMonster,floor:Int,
               rng:Random=Random.Default,challenge:Boolean=false):QuestState {
        if(!foe.isBoss || state.bossRelics.size>=INVENTORY_LIMIT)return state
        val success=state.relicDryBosses>=PITY_LIMIT-1 ||
            rng.nextInt(100)<DROP_PERCENT
        if(!success)return state.copy(
            relicDryBosses=(state.relicDryBosses+1).coerceAtMost(PITY_LIMIT-1))
        val thematic=when(foe.element) {
            "闇" -> "moonfang"
            "雷" -> "clockgear"
            "光" -> "starseal"
            else -> "abysscore"
        }
        val kind=if(rng.nextInt(100)<65)thematic
            else catalog[rng.nextInt(catalog.size)].kind
        val newRelic=BossRelic(UUID.randomUUID().toString(),kind,
            foe.species.take(80),floor.coerceIn(1,999))
        val label="【ボス限定装備】"+relicName(newRelic)+"を獲得！"
        return state.copy(
            bossRelics=state.bossRelics+newRelic,relicDryBosses=0,
            challenge=if(challenge)
                state.challenge.copy(message=state.challenge.message+"　"+label)
                else state.challenge,
            tapRun=if(challenge)state.tapRun
                else state.tapRun.copy(message=state.tapRun.message+"　"+label)
        )
    }

    fun afterVictory(state:QuestState,challenge:Boolean):QuestState {
        val heal=stats(state).heal
        if(heal<=0)return state
        return if(challenge)state.copy(challenge=state.challenge.copy(
            playerHp=(state.challenge.playerHp+heal).coerceAtMost(100)))
        else state.copy(playerHp=(state.playerHp+heal).coerceAtMost(100))
    }
}
