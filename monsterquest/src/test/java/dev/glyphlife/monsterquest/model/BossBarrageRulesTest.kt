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
        assertEquals(28,state.tapRun.bossPosition)
        state=JourneyRules.pressRight(state,Random(5))
        assertEquals(34,state.tapRun.bossPosition)
        assertEquals(450,state.tapRun.enemyHp)
        repeat(7){state=JourneyRules.pressRight(state,Random(5))}
        assertEquals(76,state.tapRun.bossPosition)
        assertEquals(450,state.tapRun.enemyHp)
        state=JourneyRules.pressRight(state,Random(5))
        assertTrue(state.tapRun.enemyHp<450)
        val previous=state.tapRun.enemyHp
        state=JourneyRules.pressLeft(state)
        assertEquals(70,state.tapRun.bossPosition)
        assertEquals(previous,state.tapRun.enemyHp)
        assertEquals(TapPhase.BOSS,state.tapRun.phase)
        repeat(30){state=JourneyRules.pressLeft(state)}
        assertEquals(BossBarrageRules.MIN_X,state.tapRun.bossPosition)
        assertEquals(state,JourneyRules.pressLeft(state))
    }

    @Test fun holdingDirectionsMovesWhileJumping() {
        var state=arena().copy(tapRun=arena().tapRun.copy(
            bossWaveCooldownMs=4500))
        state=JourneyRules.jump(state)
        assertEquals(BossBarrageRules.JUMP_DURATION_MS,state.tapRun.bossJumpMs)
        repeat(4){state=JourneyRules.tick(state,1,100,Random(1))}
        assertTrue(state.tapRun.bossPosition>BossBarrageRules.START_X)
        assertTrue(BossBarrageRules.jumpHeight(state.tapRun)>.75f)
        assertEquals(state,JourneyRules.jump(state))
        repeat(5){state=JourneyRules.tick(state,0,100,Random(1))}
        assertEquals(0,state.tapRun.bossJumpMs)
        assertTrue(state.tapRun.bossJumpCooldownMs>0)
        assertEquals(state,JourneyRules.jump(state))
        repeat(3){state=JourneyRules.tick(state,0,100,Random(1))}
        assertEquals(0,state.tapRun.bossJumpCooldownMs)
        assertTrue(JourneyRules.jump(state).tapRun.bossJumpMs>0)
    }

    @Test fun jumpOverGroundShotAvoidsDamageAndRewardsBurst() {
        var state=arena().copy(tapRun=arena().tapRun.copy(
            bossWaveCooldownMs=0,
            bossBullets=listOf(BossBarrageBullet(
                targetX=BossBarrageRules.START_X,ageMs=800,flightMs=1350))))
        state=JourneyRules.jump(state)
        repeat(6){state=JourneyRules.tick(state,0,100,Random(2))}
        assertEquals(120,state.playerHp)
        assertEquals(1,state.tapRun.bossVolleysDodged)
        assertEquals(3,state.tapRun.weakpointTaps)
        assertEquals(30,state.journey.burstGauge)
        assertTrue(BossBarrageRules.jumpHeight(state.tapRun)>.35f)
    }

    @Test fun highShotsPassOverGroundButCanCatchMidairJump() {
        val shot=BossBarrageBullet(targetX=28,ageMs=800,
            flightMs=1350,airborne=true)
        var grounded=arena().copy(tapRun=arena().tapRun.copy(
            bossBullets=listOf(shot)))
        repeat(6){grounded=JourneyRules.tick(grounded,0,100,Random(1))}
        assertEquals(120,grounded.playerHp)
        assertEquals(1,grounded.tapRun.bossVolleysDodged)

        var jumping=JourneyRules.jump(arena().copy(
            tapRun=arena().tapRun.copy(bossBullets=listOf(shot))))
        repeat(6){jumping=JourneyRules.tick(jumping,0,100,Random(1))}
        assertTrue(jumping.playerHp<120)
        assertEquals(0,jumping.tapRun.bossVolleysDodged)
    }

    @Test fun movingOutOfLandingAreaAlsoDodgesGroundShot() {
        val shot=BossBarrageBullet(targetX=28,ageMs=1280,flightMs=1350)
        val start=arena().copy(tapRun=arena().tapRun.copy(
            bossBullets=listOf(shot)))
        val moved=JourneyRules.pressLeft(JourneyRules.pressLeft(start))
        assertEquals(16,moved.tapRun.bossPosition)
        val done=JourneyRules.tick(moved,0,100,Random(4))
        assertEquals(120,done.playerHp)
        assertEquals(1,done.tapRun.bossVolleysDodged)
    }

    @Test fun staggeredVolleyCanOnlyHurtOnceAndDoesNotGrantDodgeBonus() {
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
        assertEquals(0,state.journey.burstGauge)
    }

    @Test fun barragePatternsHaveDifferentTargetPositionsAndHeights() {
        for((pattern,count) in listOf(2,3,3).withIndex()) {
            val start=arena().copy(tapRun=arena().tapRun.copy(
                bossWaveIndex=pattern,bossWaveCooldownMs=0))
            val bullets=BossBarrageRules.tick(start,100,Random(12))
                .tapRun.bossBullets
            assertEquals(count,bullets.size)
            assertTrue(bullets.all{it.targetX in 8..82})
            if(pattern==1)assertTrue(bullets.any{it.airborne})
            if(pattern==2)assertTrue(bullets.any{it.ageMs<0})
        }
        val enraged=arena().copy(tapRun=arena().tapRun.copy(
            enemyHp=200,bossWaveIndex=2,bossWaveCooldownMs=0))
        assertEquals(4,BossBarrageRules.tick(enraged,100,Random(9))
            .tapRun.bossBullets.size)
    }

    @Test fun dyingToGroundProjectileUsesExistingRetryRules() {
        val near=arena().copy(playerHp=1,
            journey=JourneyState(chain=8,burstGauge=100),
            tapRun=arena().tapRun.copy(
                bossPosition=28,
                bossBullets=listOf(BossBarrageBullet(28,1340,1350))))
        val result=JourneyRules.tick(near,0,100,Random(7))
        assertEquals(TapPhase.WALK,result.tapRun.phase)
        assertEquals(1,result.tapRun.floor)
        assertEquals(1,result.tapRun.deaths)
        assertEquals(0,result.journey.burstGauge)
        assertEquals(0,result.journey.chain)
        assertEquals(near.companion,result.companion)
    }
}
