package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BossBarrageRulesTest {
    private fun enemy():WildMonster=WildMonster(id="arena-boss",
        species="月蝕の竜",element="闇",isBoss=true,
        bodyType="DRAGON",maxHp=450,attack=15)

    private fun arena():QuestState=QuestState(
        companion=Companion(id="fighter",level=20,element="光"),
        playerHp=120,potions=2,
        tapRun=TapRun(floor=5,phase=TapPhase.BOSS,
            enemy=enemy(),enemyHp=450,
            bossPosition=BossBarrageRules.START_X,bossWaveCooldownMs=0))

    @Test fun freeMovementNeedsNoFiveFixedLanesAndAttackRequiresRange() {
        var state=arena()
        state=JourneyRules.pressRight(state,Random(5))
        assertEquals(34,state.tapRun.bossPosition)
        assertEquals(450,state.tapRun.enemyHp)
        repeat(7){state=JourneyRules.pressRight(state,Random(5))}
        assertEquals(76,state.tapRun.bossPosition)
        state=JourneyRules.pressRight(state,Random(5))
        assertTrue(state.tapRun.enemyHp<450)
        state=JourneyRules.pressLeft(state)
        assertEquals(70,state.tapRun.bossPosition)
        assertEquals(TapPhase.BOSS,state.tapRun.phase)
        repeat(30){state=JourneyRules.pressLeft(state)}
        assertEquals(BossBarrageRules.MIN_X,state.tapRun.bossPosition)
    }

    @Test fun forwardAndBackwardMovementAutomaticallyHopAgainAndAgain() {
        assertEquals(0f,BossBarrageRules.travelHop(180,0),.0001f)
        assertTrue(BossBarrageRules.travelHop(180,1)>.95f)
        assertTrue(BossBarrageRules.travelHop(180,-1)>.95f)
        assertEquals(0f,BossBarrageRules.travelHop(380,-1),.0001f)
        var state=arena().copy(tapRun=arena().tapRun.copy(
            bossWaveCooldownMs=4500))
        state=JourneyRules.pressLeft(state)
        assertEquals(BossBarrageRules.HOP_DURATION_MS,state.tapRun.bossHopMs)
        state=JourneyRules.tick(state,0,100,Random(1))
        assertTrue(BossBarrageRules.hopHeight(state.tapRun)>.7f)
        repeat(2){state=JourneyRules.tick(state,0,100,Random(1))}
        assertTrue(state.tapRun.bossHopMs<=100)
        state=JourneyRules.pressRight(state)
        assertEquals(BossBarrageRules.HOP_DURATION_MS,state.tapRun.bossHopMs)
        repeat(4){state=JourneyRules.tick(state,0,100,Random(1))}
        assertEquals(0,state.tapRun.bossHopMs)
        assertEquals(0f,BossBarrageRules.hopHeight(state.tapRun),.0001f)
    }

    @Test fun holdingBackwardMovesAndKeepsBouncing() {
        var state=arena().copy(tapRun=arena().tapRun.copy(
            bossWaveCooldownMs=4500))
        repeat(4){state=JourneyRules.tick(state,-1,100,Random(1))}
        assertEquals(BossBarrageRules.MIN_X,state.tapRun.bossPosition)
        assertTrue(state.tapRun.bossHopMs>0)
        assertEquals(TapPhase.BOSS,state.tapRun.phase)
    }

    @Test fun groundShotCanBeClearedByMovementTriggeredHop() {
        val start=arena().copy(tapRun=arena().tapRun.copy(
            bossBullets=listOf(BossBarrageBullet(
                targetX=22,ageMs=1150,flightMs=1350))))
        var state=JourneyRules.pressLeft(start)
        repeat(2){state=JourneyRules.tick(state,0,100,Random(2))}
        assertEquals(120,state.playerHp)
        assertEquals(1,state.tapRun.bossVolleysDodged)
        assertEquals(30,state.journey.burstGauge)
        assertTrue(BossBarrageRules.hopHeight(state.tapRun)>.35f)
    }

    @Test fun highShotMissesGroundHeroButHitsDuringAutomaticHop() {
        val shot=BossBarrageBullet(targetX=22,ageMs=1150,
            flightMs=1350,airborne=true)
        var grounded=arena().copy(tapRun=arena().tapRun.copy(
            bossPosition=22,bossBullets=listOf(shot)))
        repeat(2){grounded=JourneyRules.tick(grounded,0,100,Random(1))}
        assertEquals(120,grounded.playerHp)

        var moving=JourneyRules.pressLeft(arena().copy(
            tapRun=arena().tapRun.copy(
                bossPosition=28,bossBullets=listOf(shot))))
        repeat(2){moving=JourneyRules.tick(moving,0,100,Random(1))}
        assertTrue(moving.playerHp<120)
        assertEquals(0,moving.tapRun.bossVolleysDodged)
    }

    @Test fun steppingOutOfLandingAreaAvoidsAimedGroundShot() {
        val shot=BossBarrageBullet(targetX=28,ageMs=1280,flightMs=1350)
        val start=arena().copy(tapRun=arena().tapRun.copy(
            bossBullets=listOf(shot)))
        val moved=JourneyRules.pressLeft(JourneyRules.pressLeft(start))
        assertEquals(16,moved.tapRun.bossPosition)
        val done=JourneyRules.tick(moved,0,100,Random(4))
        assertEquals(120,done.playerHp)
        assertEquals(1,done.tapRun.bossVolleysDodged)
    }

    @Test fun staggeredVolleyOnlyDamagesOnce() {
        var state=arena().copy(tapRun=arena().tapRun.copy(
            bossBullets=listOf(
                BossBarrageBullet(targetX=28,ageMs=1300,flightMs=1350),
                BossBarrageBullet(targetX=28,ageMs=1190,flightMs=1350),
                BossBarrageBullet(targetX=55,ageMs=1070,flightMs=1350))))
        state=JourneyRules.tick(state,0,100,Random(5))
        assertTrue(state.playerHp<120)
        val hp=state.playerHp
        repeat(5){state=JourneyRules.tick(state,0,100,Random(5))}
        assertEquals(hp,state.playerHp)
        assertEquals(0,state.tapRun.bossVolleysDodged)
    }

    @Test fun patternsVaryAndDeathUsesExistingRetryRules() {
        for((pattern,count) in listOf(2,3,3).withIndex()) {
            val start=arena().copy(tapRun=arena().tapRun.copy(
                bossWaveIndex=pattern,bossWaveCooldownMs=0))
            val bullets=BossBarrageRules.tick(start,100,Random(12))
                .tapRun.bossBullets
            assertEquals(count,bullets.size)
            assertTrue(bullets.all{it.targetX in 8..82})
        }
        val near=arena().copy(playerHp=1,
            journey=JourneyState(chain=8,burstGauge=100),
            tapRun=arena().tapRun.copy(
                bossBullets=listOf(BossBarrageBullet(28,1340,1350))))
        val died=JourneyRules.tick(near,0,100,Random(7))
        assertEquals(1,died.tapRun.floor)
        assertEquals(1,died.tapRun.deaths)
        assertEquals(0,died.journey.burstGauge)
        assertEquals(near.companion,died.companion)
    }
}
