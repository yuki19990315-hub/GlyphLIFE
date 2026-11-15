package dev.glyphlife.monsterquest.model

import kotlin.random.Random

/**
 * Short-session, one-finger RPG journey. This is NOT a platformer:
 * left/right are the only travel inputs, and holding right automatically
 * walks/attacks outside boss battles. Bosses use independent movement and
 * attack inputs so projectiles can be dodged without an unintended strike.
 * All enemy generation, loot, growth and captures use the
 * existing TapAdventure engine; the old dungeon save continues unchanged.
 */
data class JourneyState(
    val classic:Boolean=false,
    /** Time accumulated toward the next held-input step/hit. */
    val repeatMs:Int=0,
    val chain:Int=0,
    val bestChain:Int=0,
    val totalEncounters:Int=0,
    val totalCaptures:Int=0,
    /** One optional auto-capture try at <=35% enemy HP per encounter. */
    val autoCapture:Boolean=false,
    val lastAutoCaptureId:String="",
    val travelMs:Int=0,
    val bonusText:String="",
    /** Stored across encounters, lost only when the expedition fails. */
    val burstGauge:Int=0,
    val burstUses:Int=0,
    /** A discovered shrine halts auto-walking until the player decides. */
    val shrinePending:Boolean=false,
    val lastShrineFloor:Int=0,
    val shrinesVisited:Int=0
)

object JourneyRules {
    const val WALK_INTERVAL_MS=290
    const val ATTACK_INTERVAL_MS=390
    const val BACK_INTERVAL_MS=210
    const val CHAIN_REWARD_EVERY=5
    const val BURST_COST=100

    private fun shrineDue(state:QuestState):Boolean =
        state.tapRun.phase==TapPhase.WALK &&
            state.tapRun.floor%4==2 && state.tapRun.steps>=3 &&
            state.journey.lastShrineFloor!=state.tapRun.floor &&
            !state.journey.shrinePending

    /** Choice is explicit: holding right must never select a shrine reward. */
    fun chooseShrine(state:QuestState,choice:Int):QuestState {
        if(!state.journey.shrinePending || choice !in 0..2 ||
            state.tapRun.phase!=TapPhase.WALK)return state
        val floor=state.tapRun.floor
        val visited=state.journey.copy(shrinePending=false,
            lastShrineFloor=floor,shrinesVisited=state.journey.shrinesVisited+1,
            repeatMs=0)
        return when(choice) {
            0 -> state.copy(
                playerHp=(state.playerHp+50+floor).coerceAtMost(TalentRules.maxHp(state)),
                journey=visited,
                tapRun=state.tapRun.copy(message="精霊の祠：傷を癒やした！ HP回復"))
            1 -> state.copy(
                captureOrbs=(state.captureOrbs+2).coerceAtMost(99),
                journey=visited,
                tapRun=state.tapRun.copy(message="精霊の祠：捕獲玉+2を入手！"))
            else -> {
                val cost=(TalentRules.maxHp(state)/4).coerceAtLeast(10)
                state.copy(
                    playerHp=(state.playerHp-cost).coerceAtLeast(1),
                    summonStones=(state.summonStones+2).coerceAtMost(999),
                    journey=visited,
                    tapRun=state.tapRun.copy(message=
                        "古い契約：HP-${minOf(cost,state.playerHp-1)} / 召喚結晶+2！"))
            }
        }
    }

    /** A charged finisher works against bosses as well as ordinary foes. */
    fun burst(state:QuestState,rng:Random=Random.Default):QuestState {
        if(state.journey.burstGauge<BURST_COST ||
            state.tapRun.phase !in listOf(TapPhase.FIGHT,TapPhase.BOSS) ||
            ChallengeRules.active(state))return state
        val foe=state.tapRun.enemy ?: return state
        val powered=state.copy(journey=state.journey.copy(
            burstGauge=0,burstUses=state.journey.burstUses+1,repeatMs=0))
        val impact=32+state.companion.level.coerceAtMost(80)*2+
            (state.journey.chain*2).coerceAtMost(48)+foe.maxHp/12
        return record(state,TapAdventure.finisher(powered,impact,rng))
    }

    fun toggleAutoCapture(state:QuestState):QuestState=
        state.copy(journey=state.journey.copy(
            autoCapture=!state.journey.autoCapture))

    fun phaseInterval(phase:TapPhase):Int = when(phase) {
        TapPhase.WALK -> WALK_INTERVAL_MS
        TapPhase.FIGHT,TapPhase.BOSS -> ATTACK_INTERVAL_MS
        TapPhase.BRANCH -> 900
    }

    /**
     * Synchronize a supported action with run-specific progress rewards.
     * Only a transition from the exact active foe's positive HP to a cleared
     * encounter can increase the streak, preventing repeated reward farming.
     */
    fun record(previous:QuestState,now:QuestState):QuestState {
        if(previous==now)return now
        val from=previous.tapRun
        val to=now.tapRun
        val prev=previous.journey

        if(to.deaths>from.deaths)
            return now.copy(journey=prev.copy(repeatMs=0,chain=0,
                burstGauge=0,shrinePending=false,
                bonusText="リトライ！ 仲間・装備・成長はそのまま。"))

        val wasFighting=from.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS)
        val victory=wasFighting && from.enemy!=null && from.enemyHp>0 &&
            to.phase==TapPhase.WALK && to.enemy==null &&
            now.defeated>previous.defeated
        if(victory) {
            val chain=prev.chain+1
            val reward=chain%CHAIN_REWARD_EVERY==0
            val bonusGold=if(reward) (30+from.floor*3).coerceAtMost(3500) else 0
            val bonusOrbs=if(reward)1 else 0
            val bonusCrystal=if(chain%10==0)1 else 0
            val rewardText=if(reward)
                "✦ 連続討伐 "+chain+"！ +"+bonusGold+"G"+
                    " / 捕獲玉+"+bonusOrbs+
                    (if(bonusCrystal>0)" / 召喚結晶+1" else "") else ""
            return now.copy(
                captureOrbs=(now.captureOrbs+bonusOrbs).coerceAtMost(99),
                summonStones=(now.summonStones+bonusCrystal).coerceAtMost(999),
                tapRun=now.tapRun.copy(gold=
                    (now.tapRun.gold+bonusGold).coerceAtMost(999999),
                    message=if(reward)now.tapRun.message+"　"+rewardText
                        else now.tapRun.message),
                journey=now.journey.copy(
                    chain=chain,bestChain=maxOf(prev.bestChain,chain),
                    repeatMs=0,bonusText=rewardText)
            )
        }
        // Fleeing does not count as a victory and breaks the winning streak.
        if(wasFighting && to.phase==TapPhase.WALK &&
            to.enemy==null && from.enemy!=null) {
            return now.copy(journey=prev.copy(chain=0,repeatMs=0,
                bonusText="退却：連続討伐はリセット"))
        }
        // Clear old bonus banner whenever the next encounter starts.
        if(from.enemy?.id!=to.enemy?.id && to.enemy!=null)
            return now.copy(journey=prev.copy(
                totalEncounters=prev.totalEncounters+1,
                bonusText="",repeatMs=0))
        return now
    }

    fun pressRight(state:QuestState,rng:Random=Random.Default):QuestState {
        if(ChallengeRules.active(state) || state.tapRun.phase==TapPhase.BRANCH ||
            state.journey.shrinePending)
            return state
        if(shrineDue(state))return state.copy(
            journey=state.journey.copy(shrinePending=true,repeatMs=0),
            tapRun=state.tapRun.copy(message=
                "✦ 精霊の祠を発見！ 自分に合った加護を選ぼう"))
        val result=if(state.tapRun.phase==TapPhase.BOSS)
            BossBarrageRules.move(state,1)
            else TapAdventure.tap(state,rng)
        return afterStrike(state,result)
    }

    /** Attack deliberately stays separate from boss movement. The hero's
     * little hop is a travel animation, not a jump or an invulnerability key. */
    fun pressAttack(state:QuestState,rng:Random=Random.Default):QuestState {
        if(ChallengeRules.active(state) ||
            state.tapRun.phase !in listOf(TapPhase.FIGHT,TapPhase.BOSS) ||
            state.journey.shrinePending)return state
        if(state.tapRun.phase==TapPhase.BOSS &&
            state.tapRun.bossPosition<BossBarrageRules.ATTACK_X)
            return state.copy(tapRun=state.tapRun.copy(message=
                "まだ攻撃が届かない！ 右に進んで間合いを詰めよう"))
        return afterStrike(state,TapAdventure.tap(state,rng))
    }

    private fun afterStrike(state:QuestState,result:QuestState):QuestState {
        val landed=state.tapRun.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS) &&
            result.tapRun.enemyHp<state.tapRun.enemyHp
        val charged=if(landed)result.copy(journey=result.journey.copy(
            burstGauge=(result.journey.burstGauge+15).coerceAtMost(BURST_COST)))
            else result
        val progressed=record(state,charged)
        return progressed.copy(journey=progressed.journey.copy(repeatMs=0))
    }

    fun pressLeft(state:QuestState):QuestState {
        if(ChallengeRules.active(state))return state
        if(state.journey.shrinePending)return state
        val run=state.tapRun
        return when(run.phase) {
            TapPhase.BOSS -> BossBarrageRules.move(state,-1)
            TapPhase.FIGHT -> retreat(state)
            TapPhase.WALK -> backStep(state)
            TapPhase.BRANCH -> state
        }.let{if(it==state)state else it.copy(journey=it.journey.copy(repeatMs=0))}
    }

    private fun retreat(state:QuestState):QuestState {
        val run=state.tapRun
        if(run.phase!=TapPhase.FIGHT)return state
        val escaped=state.copy(tapRun=run.copy(
            phase=TapPhase.WALK,enemy=null,enemyHp=0,combo=0,
            steps=(run.steps-2).coerceAtLeast(0),
            skillCooldowns=emptyMap(),guardHits=0,venomCharges=0,
            message="退却した！ 2歩下がった。連続討伐は途切れた"))
        return escaped.copy(journey=state.journey.copy(
            chain=0,repeatMs=0,bonusText=""))
    }

    private fun backStep(state:QuestState):QuestState {
        val run=state.tapRun
        if(run.phase!=TapPhase.WALK || run.steps<=0)return state
        return state.copy(tapRun=run.copy(
            steps=run.steps-1,
            message="一歩後退。安全な距離で作戦を立てよう"))
    }

    fun chooseBranch(state:QuestState,route:Int,
                     rng:Random=Random.Default):QuestState {
        if(ChallengeRules.active(state))return state
        return record(state,TapAdventure.branch(state,route,rng))
    }

    fun useSkill(state:QuestState,ownerId:String,bonus:Boolean=false,
                 rng:Random=Random.Default):QuestState {
        if(ChallengeRules.active(state))return state
        return record(state,TapAdventure.useSkill(state,ownerId,rng,bonus))
    }

    fun capture(state:QuestState,rng:Random=Random.Default):QuestState {
        if(ChallengeRules.active(state))return state
        val next=TapAdventure.capture(state,rng)
        val caught=next.captures.size>state.captures.size
        if(!caught)return record(state,next)
        return next.copy(journey=state.journey.copy(
            totalCaptures=state.journey.totalCaptures+1,
            repeatMs=0,bonusText="✦ 新しい個体を捕獲！"))
    }

    fun heal(state:QuestState):QuestState=TapAdventure.heal(state)

    /**
     * Only one clock runs in the active exploration tab.
     * Enemy timer keeps advancing while standing still, making it possible
     * to pause movement to use skills, dodge, heal or attempt capture.
     */
    fun tick(state:QuestState,direction:Int,elapsedMs:Int,
             rng:Random=Random.Default):QuestState {
        if(elapsedMs<=0 || state.journey.classic ||
            state.journey.shrinePending ||
            ChallengeRules.active(state))return state
        val dt=elapsedMs.coerceIn(1,160)
        val attacked=if(state.tapRun.phase==TapPhase.BOSS)
            BossBarrageRules.tick(state,dt,rng) else TapAdventure.tick(state,dt,rng)
        val clocked=record(state,attacked)
        // Death returns the player to the first floor; never also walk one
        // step on the exact frame of the retry transition.
        if(clocked.tapRun.deaths>state.tapRun.deaths)return clocked
        // Automation is opt-in and tries at most once per distinct monster.
        // It cannot consume orbs on a boss or on a full roster.
        val canAuto=clocked.journey.autoCapture &&
            clocked.tapRun.phase==TapPhase.FIGHT &&
            clocked.tapRun.enemy!=null &&
            clocked.tapRun.enemyHp>0 &&
            clocked.tapRun.enemyHp*100<=
                clocked.tapRun.enemy!!.maxHp*35 &&
            clocked.journey.lastAutoCaptureId!=clocked.tapRun.enemy!!.id &&
            clocked.captureOrbs>0 && clocked.captures.size<100
        val pre=if(canAuto) {
            val marked=clocked.copy(journey=clocked.journey.copy(
                lastAutoCaptureId=clocked.tapRun.enemy!!.id))
            capture(marked,rng)
        } else clocked
        val journey=pre.journey
        val phase=pre.tapRun.phase
        val dir=direction.coerceIn(-1,1)
        // Standing in a cleared corridor should not redraw/rewrite the full
        // 100-monster roster ten times a second. Paused game time is free.
        if(dir==0 && phase==TapPhase.WALK && pre==state)return state
        val travelMs=(journey.travelMs+dt).coerceAtMost(2_000_000_000)
        if(dir==0 || phase==TapPhase.BRANCH || pre.journey.shrinePending) {
            return pre.copy(journey=journey.copy(repeatMs=0,travelMs=travelMs))
        }
        if(dir<0 && phase==TapPhase.FIGHT)
            return retreat(pre).copy(journey=journey.copy(
                repeatMs=0,chain=0,travelMs=travelMs))
        val threshold=when {
            phase==TapPhase.BOSS ->
                BossBarrageRules.MOVE_INTERVAL_MS
            dir<0 -> BACK_INTERVAL_MS
            else -> phaseInterval(phase)
        }
        val charge=(journey.repeatMs+dt).coerceAtMost(threshold)
        if(charge<threshold)
            return pre.copy(journey=journey.copy(repeatMs=charge,travelMs=travelMs))
        val stepped=if(dir<0)pressLeft(pre) else pressRight(pre,rng)
        // The returned step has already consumed its repeat charge. Timer
        // uses a fresh interval even if the phase changed on this frame.
        return stepped.copy(journey=stepped.journey.copy(
            repeatMs=0,travelMs=travelMs))
    }
}
