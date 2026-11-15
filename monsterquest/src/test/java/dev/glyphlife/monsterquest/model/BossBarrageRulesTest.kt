package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BossBarrageRulesTest {
    private val deterministic=Random(19)
    private fun enemy():WildMonster=WildMonster(id="arena-boss",
        species="月蝕の竜",element="闇",isBoss=true,
        bodyType="DRAGON",maxHp=450,attack=15)

    private fun arena():QuestState=QuestState(
        companion=Companion(id="fighter",level=20,element="光"),
        playerHp=120,potions=2,
        tapRun=TapRun(floor=5,phase=TapPhase.BOSS,
            enemy=enemy(),enemyHp=450,bossArenaLane=1,
            bossWaveCooldownMs=0))

    @Test fun bossIsOnlyAttackableFromFrontAndRetreatDoesNotFlee() {
        val start=arena()
        val close=JourneyRules.pressRight(start,deterministic)
        assertEquals(2,close.tapRun.bossArenaLane)
        assertEquals(450,close.tapRun.enemyHp)
        val front=JourneyRules.pressRight(
            JourneyRules.pressRight(close,deterministic),deterministic)
        assertEquals(4,front.tapRun.bossArenaLane)
        val hit=JourneyRules.pressRight(front,deterministic)
        assertTrue(hit.tapRun.enemyHp<450)
        assertEquals(4,hit.tapRun.bossArenaLane)
        val retreat=JourneyRules.pressLeft(hit)
        assertEquals(TapPhase.BOSS,retreat.tapRun.phase)
        assertEquals(3,retreat.tapRun.bossArenaLane)
        assertEquals(hit.tapRun.enemyHp,retreat.tapRun.enemyHp)
        var back=retreat
        repeat(10){back=JourneyRules.pressLeft(back)}
        assertEquals(0,back.tapRun.bossArenaLane)
        assertEquals(TapPhase.BOSS,back.tapRun.phase)
        assertEquals(back,JourneyRules.pressLeft(back))
    }

    @Test fun everyVolleyLeavesAtLeastOneRealSafePosition() {
        val launched=BossBarrageRules.tick(arena(),100,Random(3))
        assertTrue(launched.tapRun.bossBullets.size in 2..3)
        assertTrue(launched.tapRun.bossBullets.all {
            it.lane in 0..4 && it.flightMs>=1100
        })
        val safe=(0..4).filter { lane ->
            launched.tapRun.bossBullets.none{it.lane==lane}
        }
        assertTrue(safe.isNotEmpty())
        assertEquals(1,launched.tapRun.bossWaveIndex)
        assertEquals(0,launched.tapRun.bossWaveCooldownMs)
    }

    @Test fun landingOutsideThePlayerPositionGrantsOnePerfectDodge() {
        var state=BossBarrageRules.tick(arena(),100,Random(9))
        val safe=(0..4).first{ lane->
            state.tapRun.bossBullets.none {it.lane==lane}
        }
        state=state.copy(tapRun=state.tapRun.copy(bossArenaLane=safe))
        val originalHp=state.playerHp
        repeat(20){state=JourneyRules.tick(state,0,100,Random(11))}
        assertEquals(originalHp,state.playerHp)
        assertTrue(state.tapRun.bossBullets.isEmpty())
        assertEquals(1,state.tapRun.bossVolleysDodged)
        assertEquals(3,state.tapRun.weakpointTaps)
        assertEquals(30,state.journey.burstGauge)
        val idle=JourneyRules.tick(state,0,100,Random(10))
        assertEquals(30,idle.journey.burstGauge)
        assertEquals(1,idle.tapRun.bossVolleysDodged)
    }

    @Test fun directHitOnlyDamagesOncePerVolleyEvenWhenShotsAreStaggered() {
        val volley=listOf(
            BossBarrageBullet(2,ageMs=1350,flightMs=1400,style=1),
            BossBarrageBullet(2,ageMs=1230,flightMs=1400,style=1),
            BossBarrageBullet(4,ageMs=1100,flightMs=1400,style=1))
        var state=arena().copy(tapRun=arena().tapRun.copy(
            bossArenaLane=2,bossBullets=volley))
        val initialHp=state.playerHp
        state=JourneyRules.tick(state,0,100,Random(5))
        assertTrue(state.playerHp<initialHp)
        val afterFirst=state.playerHp
        repeat(5){state=JourneyRules.tick(state,0,100,Random(5))}
        assertEquals(afterFirst,state.playerHp)
        assertEquals(0,state.tapRun.bossVolleysDodged)
        assertEquals(0,state.journey.burstGauge)
    }

    @Test fun enragedBossShootsFourButNeverAllFiveLanes() {
        val start=arena().copy(tapRun=arena().tapRun.copy(
            enemyHp=200,bossWaveCooldownMs=0))
        val launched=BossBarrageRules.tick(start,100,Random(8))
        assertEquals(4,launched.tapRun.bossBullets.size)
        assertEquals(4,launched.tapRun.bossBullets.map{it.lane}.distinct().size)
        assertTrue(launched.tapRun.bossBullets.all{it.flightMs==1100})
    }

    @Test fun lethalProjectileUsesExistingFirstFloorDeathAndKeepsCompanion() {
        val near=arena().copy(playerHp=1,
            journey=JourneyState(chain=8,burstGauge=100),
            tapRun=arena().tapRun.copy(
                bossArenaLane=3,
                bossBullets=listOf(BossBarrageBullet(3,1390,1400))))
        val result=JourneyRules.tick(near,0,100,Random(7))
        assertEquals(TapPhase.WALK,result.tapRun.phase)
        assertEquals(1,result.tapRun.floor)
        assertEquals(1,result.tapRun.deaths)
        assertEquals(0,result.journey.burstGauge)
        assertEquals(0,result.journey.chain)
        assertEquals(near.companion,result.companion)
        assertTrue(result.playerHp>0)
    }

    @Test fun holdingDirectionMovesThroughPositionsWithoutSkippingAttackRange() {
        var state=arena()
        state=JourneyRules.tick(state,1,160,Random(2))
        assertEquals(1,state.tapRun.bossArenaLane)
        state=JourneyRules.tick(state,1,100,Random(2))
        assertEquals(2,state.tapRun.bossArenaLane)
        repeat(5){state=JourneyRules.tick(state,1,160,Random(2))}
        assertEquals(4,state.tapRun.bossArenaLane)
        assertTrue(state.tapRun.enemyHp<450)
        state=JourneyRules.tick(state,-1,160,Random(2))
        state=JourneyRules.tick(state,-1,160,Random(2))
        assertTrue(state.tapRun.bossArenaLane<4)
    }
}
