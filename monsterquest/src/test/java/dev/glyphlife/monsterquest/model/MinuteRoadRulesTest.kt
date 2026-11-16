package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MinuteRoadRulesTest {
    private val spawn=object:Random() {
        override fun nextBits(bitCount:Int):Int=0
    }
    private val clear=object:Random() {
        override fun nextBits(bitCount:Int):Int =
            if(bitCount==32)199 else if(bitCount>=7)99
            else (1 shl bitCount)-1
    }
    private fun fresh():QuestState=QuestState(
        companion=Companion(id="hero",element="火",level=8),
        tapRun=TapRun(),potions=2,captureOrbs=3)

    @Test fun roadNeverBranchesAndContinuesAcrossOldFloorBoundary() {
        var state=fresh()
        repeat(13){state=MinuteRoadRules.pressRight(state,clear)}
        assertEquals(TapPhase.WALK,state.tapRun.phase)
        assertEquals(13,state.journey.roadPosition)
        assertEquals(130,state.journey.roadBestPosition*
            MinuteRoadRules.METERS_PER_STEP)
        assertEquals(1,MinuteRoadRules.area(state.journey.roadPosition))
        assertEquals(13,state.tapRun.distance)
    }

    @Test fun enemyStaysAtItsWorldPositionWhenHeroRetreats() {
        var state=MinuteRoadRules.pressRight(fresh(),clear)
        state=MinuteRoadRules.pressRight(state,spawn)
        assertEquals(TapPhase.FIGHT,state.tapRun.phase)
        assertEquals(2,state.journey.roadPosition)
        assertEquals(3,state.journey.roadEnemyPosition)
        assertTrue(MinuteRoadRules.inRange(state))

        val retreated=MinuteRoadRules.pressLeft(state)
        assertEquals(1,retreated.journey.roadPosition)
        assertEquals(state.tapRun.enemy?.id,retreated.tapRun.enemy?.id)
        assertFalse(MinuteRoadRules.inRange(retreated))
        val approached=MinuteRoadRules.pressRight(retreated,clear)
        assertEquals(2,approached.journey.roadPosition)
        assertEquals(state.tapRun.enemyHp,approached.tapRun.enemyHp)
        val attacked=MinuteRoadRules.pressRight(approached,clear)
        assertTrue(attacked.tapRun.enemyHp<approached.tapRun.enemyHp)
    }

    @Test fun holdingRightAttacksAutomaticallyButSteppingBackAvoidsHits() {
        var s=MinuteRoadRules.pressRight(fresh(),clear)
        s=MinuteRoadRules.pressRight(s,spawn)
        s=s.copy(tapRun=s.tapRun.copy(
            enemyHp=500,enemy=s.tapRun.enemy!!.copy(maxHp=500),
            attackTimerMs=200))
        val hp=s.playerHp
        s=MinuteRoadRules.pressLeft(s)
        repeat(6){s=MinuteRoadRules.tick(s,0,100,clear)}
        assertEquals("enemy cannot hit across a long gap",hp,s.playerHp)
        s=MinuteRoadRules.pressRight(s,clear)
        repeat(5){s=MinuteRoadRules.tick(s,1,100,clear)}
        assertTrue("holding right must automatically deal damage",
            s.tapRun.enemyHp<500)
        assertTrue("enemy may retaliate only when close",s.playerHp<hp)
    }

    @Test fun bossOccursOnRoadMilestonesInsteadOfFloorBranch() {
        var s=fresh()
        repeat(50){s=MinuteRoadRules.pressRight(s,clear)}
        assertEquals(50,s.journey.roadPosition)
        assertEquals(TapPhase.BOSS,s.tapRun.phase)
        assertTrue(s.tapRun.enemy!!.isBoss)
        assertEquals(51,s.journey.roadEnemyPosition)
        val hit=MinuteRoadRules.pressRight(s,clear)
        assertTrue(hit.tapRun.enemyHp<s.tapRun.enemyHp)
        assertEquals(s.journey.roadPosition,hit.journey.roadPosition)
    }

    @Test fun roadBossVictoryKeepsTheCurrentRoadAreaAndPosition() {
        var s=fresh()
        repeat(50){s=MinuteRoadRules.pressRight(s,clear)}
        assertEquals(TapPhase.BOSS,s.tapRun.phase)
        assertTrue(s.tapRun.enemy!!.description.contains("左へ下がろう"))
        s=s.copy(tapRun=s.tapRun.copy(enemyHp=1))
        val won=MinuteRoadRules.pressRight(s,clear)
        assertEquals(TapPhase.WALK,won.tapRun.phase)
        assertEquals(50,won.journey.roadPosition)
        assertEquals(2,won.tapRun.floor)
        assertEquals(50%TapAdventure.STEPS_PER_FLOOR,won.tapRun.steps)
        assertEquals(51,won.journey.roadBossClearedPosition)
        assertTrue(won.tapRun.message.contains("一本道"))
    }

    @Test fun roadBossDoesNotUseClassicDodgeLanes() {
        var s=fresh().copy(journey=JourneyState(
            roadPosition=50,roadBestPosition=50,roadEnemyPosition=51),
            tapRun=TapRun(floor=2,phase=TapPhase.BOSS,
                enemy=WildMonster("boss","月蝕の竜","闇",maxHp=500,
                    attack=20,isBoss=true),enemyHp=500,
                attackTimerMs=100,lane=-1,bossTarget=1))
        val hp=s.playerHp
        s=MinuteRoadRules.tick(s,0,100,clear)
        assertTrue(s.playerHp<hp)
        assertEquals(0,s.tapRun.weakpointTaps)
        assertTrue(s.tapRun.message.contains("射程外"))

        val away=MinuteRoadRules.pressLeft(s)
        val safeHp=away.playerHp
        var waited=away
        repeat(50){waited=MinuteRoadRules.tick(waited,0,100,clear)}
        assertEquals(safeHp,waited.playerHp)
        assertEquals(49,waited.journey.roadPosition)
    }

    @Test fun oldSaveMigratesWithoutDroppingActiveEnemy() {
        val old=fresh().copy(tapRun=TapRun(floor=4,steps=3,
            phase=TapPhase.FIGHT,
            enemy=WildMonster("f","モコ","草",maxHp=50,attack=3),
            enemyHp=50))
        val migrated=MinuteRoadRules.prepare(old)
        assertEquals(24,migrated.journey.roadPosition)
        assertEquals(25,migrated.journey.roadEnemyPosition)
        assertEquals(old.tapRun.enemy,migrated.tapRun.enemy)
        assertEquals(migrated,MinuteRoadRules.prepare(migrated))
    }

    @Test fun challengeModeCannotConsumeExploreItemsOrAdvanceTheRoad() {
        val foe=WildMonster("wild","モコ","草",maxHp=60,attack=5)
        val paused=fresh().copy(
            playerHp=45,potions=2,captureOrbs=3,
            challenge=ChallengeState(mode=ChallengeMode.TOWER),
            tapRun=TapRun(phase=TapPhase.FIGHT,enemy=foe,enemyHp=20),
            journey=JourneyState(roadPosition=2,roadEnemyPosition=3,
                burstGauge=JourneyRules.BURST_COST,roadFreeStats=2))

        assertEquals(paused,MinuteRoadRules.pressRight(paused,clear))
        assertEquals(paused,MinuteRoadRules.pressLeft(paused))
        assertEquals(paused,MinuteRoadRules.capture(paused,clear))
        assertEquals(paused,MinuteRoadRules.skill(paused,"hero",rng=clear))
        assertEquals(paused,MinuteRoadRules.burst(paused,clear))
        assertEquals(paused,JourneyRules.heal(paused))
        assertEquals(paused,JourneyRules.toggleAutoCapture(paused))
        assertEquals(paused,MinuteRoadRules.allocate(paused,"power"))
        assertEquals(paused,MinuteRoadRules.tick(paused,1,100,clear))
    }

    @Test fun levelsAwardFiveAssignableStatsAndChoicesArePersistableValues() {
        val monster=WildMonster("exp","モコ","草",maxHp=60,attack=3)
        val start=fresh().copy(
            companionXp=GrowthRules.xpNeeded(8)-1,
            tapRun=TapRun(phase=TapPhase.FIGHT,enemy=monster,enemyHp=1),
            journey=JourneyState(roadPosition=5,roadBestPosition=5,
                roadEnemyPosition=6))
        val win=MinuteRoadRules.pressRight(start,clear)
        assertEquals(TapPhase.WALK,win.tapRun.phase)
        assertEquals(5,win.journey.roadFreeStats)
        val power=MinuteRoadRules.allocate(win,"power")
        assertEquals(4,power.journey.roadFreeStats)
        assertEquals(1,power.journey.roadPower)
        val vitality=MinuteRoadRules.allocate(power,"vitality")
        assertEquals(TalentRules.maxHp(power)+4,TalentRules.maxHp(vitality))
        assertEquals(vitality,MinuteRoadRules.allocate(vitality,"unknown"))
    }
}
