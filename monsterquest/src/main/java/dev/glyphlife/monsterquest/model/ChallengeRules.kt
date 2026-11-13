package dev.glyphlife.monsterquest.model

import java.util.UUID
import kotlin.random.Random

/** Offline endgame challenges. No server, PvP matchmaking or real player identities. */
enum class ChallengeMode { NONE, TOWER, ARENA }

data class ChallengeState(
    val mode:ChallengeMode=ChallengeMode.NONE,
    val towerFloor:Int=1,                 // current encounter, if TOWER
    val towerBest:Int=0,                  // floors cleared, forever
    val towerClears:Int=0,
    val towerMilestones:Set<Int> = emptySet(),
    val arenaRating:Int=1000,
    val arenaBestRating:Int=1000,
    val arenaWins:Int=0,
    val arenaLosses:Int=0,
    val arenaStreak:Int=0,
    val arenaWave:Int=1,                  // 1..3, if ARENA
    val opponent:WildMonster?=null,
    val opponentHp:Int=0,
    val playerHp:Int=100,
    val attackTimerMs:Int=3000,
    val guardCooldownMs:Int=0,
    val guardActive:Boolean=false,
    val skillCooldowns:Map<String,Int> = emptyMap(),
    val guardHits:Int=0,
    val venomCharges:Int=0,
    val combo:Int=0,
    val message:String="試練の塔かアリーナに挑戦しよう！"
)

/**
 * A self-contained loop: tapping only hurts the opponent; timed strikes are
 * driven by an independent 100ms UI tick while the challenge tab is visible.
 * Exploration HP/floor and progression are never reset by these modes.
 */
object ChallengeRules {
    const val TOWER_LAST_FLOOR=20
    const val ARENA_WAVES=3
    const val GUARD_COOLDOWN_MS=9000
    const val TICK_LIMIT_MS=250
    private val towerMobs=listOf(
        Triple("鉱晶ゴーレム","水","GOLEM"),
        Triple("影翼コウモリ","闇","AVIAN"),
        Triple("ライゼル","雷","BEAST"),
        Triple("炎翼竜","火","DRAGON"),
        Triple("深淵の魔眼","闇","SPIRIT"),
        Triple("翠刃の虫騎士","草","INSECTOID"),
        Triple("アクア","水","AQUATIC")
    )
    private val towerBosses=listOf(
        Triple("古代の歯車王","雷","MECHA"),
        Triple("月蝕の竜","闇","DRAGON"),
        Triple("星光の守護者","光","SPIRIT"),
        Triple("終焉の守護者","闇","GOLEM")
    )
    private val arenaMobs=listOf(
        Triple("紅蓮の竜騎士","火","DRAGON"),
        Triple("氷晶の魔術師","水","SPIRIT"),
        Triple("翠刃の虫騎士","草","INSECTOID"),
        Triple("蒼雷の獣","雷","BEAST"),
        Triple("魔導の機兵","闇","MECHA"),
        Triple("光の守護騎士","光","BIPED")
    )
    fun active(state:QuestState):Boolean =
        state.challenge.mode!=ChallengeMode.NONE

    fun tier(rating:Int):String=when {
        rating>=2100 -> "マスター"
        rating>=1750 -> "プラチナ"
        rating>=1450 -> "ゴールド"
        rating>=1200 -> "シルバー"
        else -> "ブロンズ"
    }

    private fun newOpponent(mode:ChallengeMode,number:Int,rating:Int):WildMonster {
        val boss=mode==ChallengeMode.TOWER && number%5==0
        val names=if(mode==ChallengeMode.TOWER) {
            if(boss)towerBosses else towerMobs
        } else arenaMobs
        val index=if(mode==ChallengeMode.TOWER) {
            if(boss)number/5-1 else (number-1)%names.size
        } else (((rating-1000)/100).coerceAtLeast(0)+number-1)%names.size
        val form=names[Math.floorMod(index,names.size)]
        val hp=if(mode==ChallengeMode.TOWER) {
            if(boss) 110+number*16 else 30+number*11
        } else 46+((rating-1000).coerceAtLeast(0)/15)+number*19
        val atk=if(mode==ChallengeMode.TOWER) {
            4+number/2+(if(boss)9 else 0)
        } else 5+((rating-1000).coerceAtLeast(0)/240)+number*2
        val stage=if(mode==ChallengeMode.TOWER)
            (1+(number-1)/7).coerceIn(1,3)
            else (1+((rating-1000).coerceAtLeast(0)/500)).coerceIn(1,3)
        return WildMonster(
            id=UUID.randomUUID().toString(),species=form.first,element=form.second,
            bodyType=form.third,maxHp=hp.coerceAtMost(5000),attack=atk,
            stage=stage,isBoss=boss,
            description=if(boss)"強力なタメ攻撃。盾で受け止めよう！" else ""
        )
    }

    private fun towerTimer(floor:Int):Int=when {
        floor%5==0 -> 3600
        floor>=15 -> 2500
        else -> 3100
    }
    private fun arenaTimer(wave:Int):Int=2800-(wave-1)*200

    fun startTower(state:QuestState):QuestState {
        if(active(state) || state.tapRun.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS))
            return state
        val foe=newOpponent(ChallengeMode.TOWER,1,1000)
        return state.copy(challenge=state.challenge.copy(
            mode=ChallengeMode.TOWER,towerFloor=1,opponent=foe,
            opponentHp=foe.maxHp,playerHp=100,attackTimerMs=towerTimer(1),
            guardCooldownMs=0,guardActive=false,combo=0,
            skillCooldowns=emptyMap(),guardHits=0,venomCharges=0,
            message="試練の塔に入った！ 第1階層から20階までの連戦"
        ))
    }

    fun startArena(state:QuestState):QuestState {
        if(active(state) || state.tapRun.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS))
            return state
        val c=state.challenge
        val foe=newOpponent(ChallengeMode.ARENA,1,c.arenaRating)
        return state.copy(challenge=c.copy(
            mode=ChallengeMode.ARENA,arenaWave=1,opponent=foe,
            opponentHp=foe.maxHp,playerHp=100,attackTimerMs=arenaTimer(1),
            guardCooldownMs=0,guardActive=false,combo=0,
            skillCooldowns=emptyMap(),guardHits=0,venomCharges=0,
            message="模擬アリーナ開幕！ NPCの3体を連続撃破しよう"
        ))
    }

    fun attack(state:QuestState,rng:Random=Random.Default):QuestState {
        val c=state.challenge
        val enemy=c.opponent ?: return state
        if(c.mode==ChallengeMode.NONE) return state
        // Capture party growth explicitly: the three active allies contribute
        // their actual Attack stats and evolution stages to the player's power.
        val team=state.partyIds.mapNotNull { id ->
            state.captures.firstOrNull { it.id==id }
        }
        val support=(team.sumOf { it.attack.coerceIn(0,300)/10+
            it.stage*2 }).coerceAtMost(90)
        val relic=RelicRules.stats(state)
        val crit=rng.nextInt(100)<12+relic.critical
        val poison=if(c.venomCharges>0)8 else 0
        val damage=(7+state.companion.level.coerceIn(1,50)/3+
            state.attackUpgrade*2+support/3+relic.attack+rng.nextInt(5))*
            (if(crit)2 else 1)+poison
        val remaining=(c.opponentHp-damage).coerceAtLeast(0)
        val now=state.copy(challenge=c.copy(
            opponentHp=remaining,combo=c.combo+1,
            venomCharges=(c.venomCharges-1).coerceAtLeast(0),
            skillCooldowns=SkillRules.countdown(c.skillCooldowns,relic.charge),
            message=(if(crit)"会心！ " else "")+damage+
                "ダメージ！ "+(c.combo+1)+" HIT"))
        return if(remaining==0) win(now,enemy,rng) else now
    }

    fun useSkill(state:QuestState,ownerId:String,
                 rng:Random=Random.Default):QuestState {
        val c=state.challenge
        val foe=c.opponent ?: return state
        val updated=SkillRules.useChallenge(state,ownerId)
        if(updated==state)return state
        return if(c.opponentHp>0 && updated.challenge.opponentHp==0)
            win(updated,foe,rng) else updated
    }

    fun defend(state:QuestState):QuestState {
        val c=state.challenge
        if(c.mode==ChallengeMode.NONE || c.opponent==null ||
            c.guardActive || c.guardCooldownMs>0)return state
        return state.copy(challenge=c.copy(
            guardActive=true,guardCooldownMs=GUARD_COOLDOWN_MS,
            message="守護障壁を展開！ 次の敵攻撃を大きく軽減"))
    }

    /** Real-time processing is independent of player taps and does not pile up
     * missed hits on app suspension; clamp large deltas to one small tick. */
    fun tick(state:QuestState,elapsedMs:Int):QuestState {
        val c=state.challenge
        if(c.mode==ChallengeMode.NONE || c.opponent==null || elapsedMs<=0)return state
        val step=elapsedMs.coerceIn(1,TICK_LIMIT_MS)
        val cd=(c.guardCooldownMs-step).coerceAtLeast(0)
        val remaining=c.attackTimerMs-step
        if(remaining>0) return state.copy(challenge=c.copy(
            attackTimerMs=remaining,guardCooldownMs=cd))
        val opponent=c.opponent
        val boss=opponent.isBoss
        val raw=opponent.attack+(if(boss)7 else 0)
        val reduced=(raw-state.defenseUpgrade-
            RelicRules.stats(state).defense).coerceAtLeast(1)
        val damage=if(c.guardActive)(reduced/4).coerceAtLeast(1)
            else if(c.guardHits>0)(reduced*3/10).coerceAtLeast(1)
            else reduced
        val hp=(c.playerHp-damage).coerceAtLeast(0)
        if(hp==0)return lose(state.copy(challenge=c.copy(playerHp=0)),
            retreat=false)
        val newTimer=if(c.mode==ChallengeMode.TOWER)
            towerTimer(c.towerFloor) else arenaTimer(c.arenaWave)
        return state.copy(challenge=c.copy(
            playerHp=hp,guardActive=false,guardCooldownMs=cd,
            guardHits=(c.guardHits-1).coerceAtLeast(0),
            attackTimerMs=newTimer,
            message=(if(c.guardActive)"障壁で軽減！ " else "敵の反撃！ ")+
                "HP-"+damage+"　あと"+hp))
    }

    fun retreat(state:QuestState):QuestState=lose(state,retreat=true)

    private fun lose(state:QuestState,retreat:Boolean):QuestState {
        val c=state.challenge
        if(c.mode==ChallengeMode.NONE)return state
        if(c.mode==ChallengeMode.TOWER) {
            return state.copy(challenge=c.copy(
                mode=ChallengeMode.NONE,opponent=null,opponentHp=0,
                message=if(retreat)"塔から帰還。最高記録は保存"
                else "塔で敗北！ 第1階層から再挑戦。獲得報酬は保持"
            ))
        }
        val nextRating=(c.arenaRating-(if(retreat)12 else 22)).coerceAtLeast(900)
        return state.copy(challenge=c.copy(
            mode=ChallengeMode.NONE,opponent=null,opponentHp=0,
            arenaLosses=(c.arenaLosses+1).coerceAtMost(999999),
            arenaStreak=0,arenaRating=nextRating,
            message=(if(retreat)"模擬戦を棄権" else "模擬戦に敗北")+
                "。レート "+c.arenaRating+" → "+nextRating
        ))
    }

    private fun win(state:QuestState,foe:WildMonster,
                    rng:Random=Random.Default):QuestState {
        val c=state.challenge
        val tower=c.mode==ChallengeMode.TOWER
        val floor=if(tower)c.towerFloor else c.arenaWave
        // Existing EXP/individual victory rules apply to genuine victories.
        val grown=GrowthRules.rewardBattle(state,boss=tower && foe.isBoss,
            floor=if(tower)floor else 2+c.arenaRating/350)
        val base=grown.copy(
            defeated=(grown.defeated+1).coerceAtMost(999999),
            // Challenge log is separate from the exploration log.
            tapRun=state.tapRun
        )
        val result=if(tower) winTower(base,c,floor)
            else winArena(base,c,floor)
        return RelicRules.afterVictory(
            if(tower && foe.isBoss)
                RelicRules.reward(result,foe,floor,rng,challenge=true)
            else result,challenge=true)
    }

    private fun winTower(base:QuestState,c:ChallengeState,floor:Int):QuestState {
        val milestone=floor%5==0
        val first=milestone && floor !in c.towerMilestones
        val reward=if(first) when(floor) { 5->2;10->3;15->4;20->6;else->0 }
            else 0
        val totalMilestones=if(first)c.towerMilestones+floor else c.towerMilestones
        val rewardGold=18+floor*4+(if(milestone)75 else 0)
        val baseState=base.copy(
            summonStones=(base.summonStones+reward).coerceAtMost(999),
            tapRun=base.tapRun.copy(
                gold=(base.tapRun.gold+rewardGold).coerceAtMost(999999))
        )
        val best=maxOf(c.towerBest,floor)
        if(floor==TOWER_LAST_FLOOR) {
            return baseState.copy(challenge=c.copy(
                mode=ChallengeMode.NONE,towerBest=best,
                towerMilestones=totalMilestones,
                towerClears=(c.towerClears+1).coerceAtMost(999999),
                opponent=null,opponentHp=0,combo=0,
                message="試練の塔20Fを完全踏破！ "+
                    (if(reward>0)"初回結晶+"+reward+"！ " else "")+
                    "再挑戦して報酬と育成経験値を稼ごう"
            ))
        }
        val nextFloor=floor+1
        val next=newOpponent(ChallengeMode.TOWER,nextFloor,1000)
        return baseState.copy(challenge=c.copy(
            towerFloor=nextFloor,towerBest=best,towerMilestones=totalMilestones,
            playerHp=(c.playerHp+16).coerceAtMost(100),
            opponent=next,opponentHp=next.maxHp,
            attackTimerMs=towerTimer(nextFloor),
            guardActive=false,guardCooldownMs=0,combo=0,
            skillCooldowns=emptyMap(),guardHits=0,venomCharges=0,
            message=floor.toString()+"F突破！ "+
                (if(reward>0)"初回報酬 結晶+"+reward+"。 " else "")+
                "HPを16回復。次は"+nextFloor+"F"
        ))
    }

    private fun winArena(base:QuestState,c:ChallengeState,wave:Int):QuestState {
        val earnedGold=20+wave*10
        val waveState=base.copy(tapRun=base.tapRun.copy(
            gold=(base.tapRun.gold+earnedGold).coerceAtMost(999999)))
        if(wave<ARENA_WAVES) {
            val next=newOpponent(ChallengeMode.ARENA,wave+1,c.arenaRating)
            return waveState.copy(challenge=c.copy(
                arenaWave=wave+1,
                opponent=next,opponentHp=next.maxHp,
                playerHp=(c.playerHp+12).coerceAtMost(100),
                guardCooldownMs=0,guardActive=false,combo=0,
                skillCooldowns=emptyMap(),guardHits=0,venomCharges=0,
                attackTimerMs=arenaTimer(wave+1),
                message=wave.toString()+"体撃破！ HP+12。次は"+(wave+1)+"体目"
            ))
        }
        val streak=(c.arenaStreak+1).coerceAtMost(999999)
        val newRating=(c.arenaRating+25+minOf(streak,5)*3).coerceAtMost(3000)
        val firstTimeRewards=listOf(1200 to 1,1450 to 2,1750 to 3,2100 to 5)
            .filter { (threshold,_) ->
                c.arenaBestRating<threshold && newRating>=threshold }
            .sumOf { it.second }
        return waveState.copy(
            summonStones=(waveState.summonStones+firstTimeRewards).coerceAtMost(999),
            tapRun=waveState.tapRun.copy(
                gold=(waveState.tapRun.gold+90).coerceAtMost(999999)),
            challenge=c.copy(
                mode=ChallengeMode.NONE,
                opponent=null,opponentHp=0,combo=0,
                arenaRating=newRating,
                arenaBestRating=maxOf(c.arenaBestRating,newRating),
                arenaStreak=streak,
                arenaWins=(c.arenaWins+1).coerceAtMost(999999),
                message="模擬アリーナ3戦全勝！ レート "+
                    c.arenaRating+" → "+newRating+
                    " ("+tier(newRating)+")"+
                    (if(firstTimeRewards>0)" 初昇格結晶+"+firstTimeRewards else "")
            )
        )
    }
}
