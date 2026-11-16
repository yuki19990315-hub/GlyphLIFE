package dev.glyphlife.monsterquest.model

import kotlin.random.Random

/**
 * The primary exploration loop is a single persistent road, not a succession
 * of short dungeon floors. The player has only two travel inputs: left and
 * right. Holding right walks until a foe is within melee range, then attacks
 * automatically; repeated taps do not wait for the hold-repeat timer.
 *
 * This is an original implementation of the publicly documented one-finger
 * RPG interaction, using Monster Quest's own creatures and reward system.
 */
object MinuteRoadRules {
    const val MAX_POSITION=99_999
    const val STEPS_PER_AREA=50
    const val METERS_PER_STEP=10
    const val WALK_INTERVAL_MS=260
    const val BACK_INTERVAL_MS=240
    const val ATTACK_INTERVAL_MS=420
    const val BOSS_EVERY_STEPS=50

    fun area(position:Int):Int=
        (position.coerceAtLeast(0)/STEPS_PER_AREA+1)
            .coerceAtMost(TapAdventure.MAX_FLOOR)

    fun inRange(state:QuestState):Boolean =
        state.tapRun.enemy!=null &&
            state.journey.roadEnemyPosition>=0 &&
            state.journey.roadEnemyPosition-state.journey.roadPosition<=1

    /** Import the current position and encounter from older dungeon saves. */
    fun prepare(state:QuestState):QuestState {
        val run=state.tapRun
        val legacy=(((run.floor-1)*TapAdventure.STEPS_PER_FLOOR)+run.steps)
            .coerceIn(0,MAX_POSITION)
        val starting=if(state.journey.roadPosition<0)legacy
            else state.journey.roadPosition.coerceIn(0,MAX_POSITION)
        val encounter=run.enemy!=null && run.phase in
            listOf(TapPhase.FIGHT,TapPhase.BOSS)
        val foeX=if(!encounter)-1
            else if(state.journey.roadEnemyPosition<0)starting+1
            else state.journey.roadEnemyPosition
        val cleanRun=if(run.phase==TapPhase.BRANCH)
            run.copy(phase=TapPhase.WALK,steps=starting%STEPS_PER_AREA,
                floor=area(starting),message="一本道の冒険を再開！")
            else run
        val nextJourney=state.journey.copy(
            roadPosition=starting,
            roadBestPosition=maxOf(state.journey.roadBestPosition,starting),
            roadEnemyPosition=foeX,
            shrinePending=false)
        if(cleanRun==run && nextJourney==state.journey)return state
        return state.copy(tapRun=cleanRun,journey=nextJourney)
    }

    private fun finishAction(before:QuestState,result:QuestState):QuestState {
        val rewarded=JourneyRules.record(before,result)
        val gained=(rewarded.companion.level-before.companion.level)
            .coerceAtLeast(0)
        val recorded=if(gained>0)rewarded.copy(journey=rewarded.journey.copy(
            roadFreeStats=(rewarded.journey.roadFreeStats+gained*5)
                .coerceAtMost(9999)))else rewarded
        if(recorded.tapRun.deaths>before.tapRun.deaths) {
            return recorded.copy(journey=recorded.journey.copy(
                roadPosition=0,roadEnemyPosition=-1,repeatMs=0,
                chain=0,burstGauge=0))
        }
        val cleared=before.tapRun.enemy!=null && recorded.tapRun.enemy==null
        if(!cleared)return recorded
        val won=recorded.defeated>before.defeated
        val bossWon=won && before.tapRun.enemy?.isBoss==true
        return recorded.copy(journey=recorded.journey.copy(
            roadEnemyPosition=-1,
            roadBossClearedPosition=if(bossWon)
                maxOf(recorded.journey.roadBossClearedPosition,
                    before.journey.roadEnemyPosition)
                else recorded.journey.roadBossClearedPosition))
    }

    fun pressRight(state:QuestState,rng:Random=Random.Default):QuestState {
        if(ChallengeRules.active(state))return state
        val s=prepare(state)
        val j=s.journey
        val run=s.tapRun
        if(run.enemy!=null && run.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS)) {
            if(!inRange(s)) {
                val x=(j.roadPosition+1).coerceAtMost(j.roadEnemyPosition-1)
                return s.copy(journey=j.copy(roadPosition=x,repeatMs=0),
                    tapRun=run.copy(message="敵に接近中…"))
            }
            val attacked=TapAdventure.tap(s,rng)
            val landed=attacked.tapRun.enemyHp<run.enemyHp
            val charged=if(landed)attacked.copy(journey=attacked.journey.copy(
                burstGauge=(j.burstGauge+15).coerceAtMost(JourneyRules.BURST_COST)))
                else attacked
            return finishAction(s,charged).let { it.copy(journey=it.journey.copy(repeatMs=0)) }
        }
        if(j.roadPosition>=MAX_POSITION)return s
        val x=j.roadPosition+1
        val next=s.copy(
            journey=j.copy(roadPosition=x,
                roadBestPosition=maxOf(j.roadBestPosition,x),repeatMs=0),
            tapRun=run.copy(floor=area(x),steps=x%TapAdventure.STEPS_PER_FLOOR,
                phase=TapPhase.WALK,enemy=null,enemyHp=0,
                distance=maxOf(run.distance,x),
                bestFloor=maxOf(run.bestFloor,area(x)),
                message="一本道を進行中 · ${x*METERS_PER_STEP}m"))
        val boss=x%BOSS_EVERY_STEPS==0 &&
            x>j.roadBossClearedPosition
        val encounter=boss || (x>=2 && rng.nextInt(100)<27)
        if(!encounter)return next
        val spawned=TapAdventure.roadEncounter(next,boss,rng)
        val encountered=finishAction(next,spawned)
        return encountered.copy(journey=encountered.journey.copy(
            roadEnemyPosition=x+1,repeatMs=0))
    }

    /** Backing off does not despawn the enemy or instantly flee the battle. */
    fun pressLeft(state:QuestState):QuestState {
        if(ChallengeRules.active(state))return state
        val s=prepare(state)
        val x=(s.journey.roadPosition-1).coerceAtLeast(0)
        if(x==s.journey.roadPosition)return s
        return s.copy(journey=s.journey.copy(roadPosition=x,repeatMs=0),
            tapRun=s.tapRun.copy(message="後退 · ${x*METERS_PER_STEP}m"))
    }

    fun capture(state:QuestState,rng:Random=Random.Default):QuestState {
        if(ChallengeRules.active(state))return state
        val s=prepare(state)
        if(!inRange(s))return s
        return finishAction(s,JourneyRules.capture(s,rng))
    }

    fun burst(state:QuestState,rng:Random=Random.Default):QuestState {
        val s=prepare(state)
        if(!inRange(s) || s.journey.burstGauge<JourneyRules.BURST_COST ||
            ChallengeRules.active(s))return s
        val foe=s.tapRun.enemy ?: return s
        val powered=s.copy(journey=s.journey.copy(
            burstGauge=0,burstUses=s.journey.burstUses+1))
        val damage=32+s.companion.level.coerceAtMost(80)*2+
            s.journey.roadWisdom*3+
            (s.journey.chain*2).coerceAtMost(48)+foe.maxHp/12
        return finishAction(s,TapAdventure.finisher(powered,damage,rng))
    }

    fun skill(state:QuestState,ownerId:String,bonus:Boolean=false,
              rng:Random=Random.Default):QuestState {
        if(ChallengeRules.active(state))return state
        val s=prepare(state)
        if(!inRange(s))return s
        return finishAction(s,TapAdventure.useSkill(s,ownerId,rng,bonus))
    }

    fun allocate(state:QuestState,stat:String):QuestState {
        val s=prepare(state)
        val j=s.journey
        if(j.roadFreeStats<=0 || ChallengeRules.active(s))return s
        val grown=when(stat) {
            "power" -> if(j.roadPower<9999)j.copy(roadPower=j.roadPower+1)
                else return s
            "vitality" -> if(j.roadVitality<9999)
                j.copy(roadVitality=j.roadVitality+1) else return s
            "speed" -> if(j.roadSpeed<9999)j.copy(roadSpeed=j.roadSpeed+1)
                else return s
            "wisdom" -> if(j.roadWisdom<9999)j.copy(roadWisdom=j.roadWisdom+1)
                else return s
            "luck" -> if(j.roadLuck<9999)j.copy(roadLuck=j.roadLuck+1)
                else return s
            else -> return s
        }
        return s.copy(journey=grown.copy(roadFreeStats=j.roadFreeStats-1))
    }

    /** A single 100ms clock owns both held input and proximity combat. */
    fun tick(state:QuestState,direction:Int,elapsedMs:Int,
             rng:Random=Random.Default):QuestState {
        if(elapsedMs<=0 || state.journey.classic ||
            ChallengeRules.active(state))return state
        val s=prepare(state)
        val dt=elapsedMs.coerceIn(1,160)
        val enemyTurn=if(inRange(s))TapAdventure.tick(s,dt,rng) else s
        val fought=finishAction(s,enemyTurn)
        if(fought.tapRun.deaths>s.tapRun.deaths)return fought
        val canCapture=fought.journey.autoCapture && inRange(fought) &&
            fought.tapRun.phase==TapPhase.FIGHT &&
            fought.tapRun.enemyHp>0 &&
            fought.tapRun.enemyHp*100<=
                fought.tapRun.enemy!!.maxHp.coerceAtLeast(1)*35 &&
            fought.journey.lastAutoCaptureId!=fought.tapRun.enemy!!.id &&
            fought.captureOrbs>0 && fought.captures.size<100
        val pre=if(canCapture) {
            val marked=fought.copy(journey=fought.journey.copy(
                lastAutoCaptureId=fought.tapRun.enemy!!.id))
            capture(marked,rng)
        } else fought
        val dir=direction.coerceIn(-1,1)
        if(dir==0)return if(pre==state)state else
            pre.copy(journey=pre.journey.copy(repeatMs=0))
        val speedBonus=(pre.journey.roadSpeed*4).coerceAtMost(120)
        val interval=(if(dir<0)BACK_INTERVAL_MS
            else if(pre.tapRun.enemy!=null && inRange(pre))ATTACK_INTERVAL_MS
            else WALK_INTERVAL_MS)-speedBonus
        val charged=pre.journey.repeatMs+dt
        if(charged<interval)return pre.copy(journey=pre.journey.copy(
            repeatMs=charged,travelMs=(pre.journey.travelMs+dt)
                .coerceAtMost(2_000_000_000)))
        val moved=if(dir<0)pressLeft(pre) else pressRight(pre,rng)
        return moved.copy(journey=moved.journey.copy(repeatMs=0,
            travelMs=(pre.journey.travelMs+dt).coerceAtMost(2_000_000_000)))
    }
}
