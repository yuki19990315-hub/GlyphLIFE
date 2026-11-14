package dev.glyphlife.monsterquest.model

import kotlin.random.Random

/**
 * Short-session, one-finger RPG journey. This is NOT a platformer:
 * left/right are the only travel inputs, and holding right automatically
 * walks/attacks. All enemy generation, loot, growth and captures use the
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
    val travelMs:Int=0,
    val bonusText:String=""
)

object JourneyRules {
    const val WALK_INTERVAL_MS=290
    const val ATTACK_INTERVAL_MS=390
    const val BACK_INTERVAL_MS=210
    const val CHAIN_REWARD_EVERY=5

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
                journey=prev.copy(
                    chain=chain,bestChain=maxOf(prev.bestChain,chain),
                    totalEncounters=prev.totalEncounters+1,
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
        if(ChallengeRules.active(state) || state.tapRun.phase==TapPhase.BRANCH)
            return state
        val result=TapAdventure.tap(state,rng)
        return record(state,result).copy(
            journey=record(state,result).journey.copy(repeatMs=0))
    }

    fun pressLeft(state:QuestState):QuestState {
        if(ChallengeRules.active(state))return state
        val run=state.tapRun
        return when(run.phase) {
            TapPhase.BOSS -> TapAdventure.dodge(state,-1)
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
        if(elapsedMs<=0 || ChallengeRules.active(state))return state
        val dt=elapsedMs.coerceIn(1,160)
        val attacked=TapAdventure.tick(state,dt,rng)
        val pre=record(state,attacked)
        val journey=pre.journey
        val travelMs=(journey.travelMs+dt).coerceAtMost(2_000_000_000)
        val phase=pre.tapRun.phase
        val dir=direction.coerceIn(-1,1)
        if(dir==0 || phase==TapPhase.BRANCH ||
            (dir<0 && phase==TapPhase.BOSS)) {
            return pre.copy(journey=journey.copy(repeatMs=0,travelMs=travelMs))
        }
        if(dir<0 && phase==TapPhase.FIGHT)
            return retreat(pre).copy(journey=journey.copy(
                repeatMs=0,chain=0,travelMs=travelMs))
        val threshold=if(dir<0)BACK_INTERVAL_MS else phaseInterval(phase)
        val charge=(journey.repeatMs+dt).coerceAtMost(threshold)
        if(charge<threshold)
            return pre.copy(journey=journey.copy(repeatMs=charge,travelMs=travelMs))
        val stepped=if(dir<0)backStep(pre) else pressRight(pre,rng)
        // The returned step has already consumed its repeat charge. Timer
        // uses a fresh interval even if the phase changed on this frame.
        return stepped.copy(journey=stepped.journey.copy(
            repeatMs=0,travelMs=travelMs))
    }
}
