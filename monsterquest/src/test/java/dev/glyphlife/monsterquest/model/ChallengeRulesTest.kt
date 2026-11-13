package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ChallengeRulesTest {
    private val noCrit=object:Random() {
        override fun nextBits(bitCount:Int):Int=0
    }
    private fun hero()=QuestState(
        companion=Companion(level=10),playerHp=79,
        tapRun=TapRun(floor=8,bestFloor=13,gold=90),
        captures=listOf(
            WildMonster(id="owned-one",species="ライゼル",element="雷",
                maxHp=90,attack=28,level=8,stage=2)
        ),partyIds=listOf("owned-one")
    )

    /** A battle can only finish through actual attack inputs. */
    private fun defeatCurrent(state:QuestState):QuestState {
        var s=state
        val enemy=s.challenge.opponent ?: error("No enemy")
        var tries=0
        while(s.challenge.opponent?.id==enemy.id &&
            s.challenge.mode!=ChallengeMode.NONE && tries++<250) {
            s=ChallengeRules.attack(s,noCrit)
        }
        assertTrue("Enemy not defeated after 250 taps",tries<250)
        return s
    }

    @Test fun modesAreSeparateFromExplorationAndCannotBothStart() {
        val s=hero()
        val tower=ChallengeRules.startTower(s)
        assertEquals(ChallengeMode.TOWER,tower.challenge.mode)
        assertEquals(s.tapRun,tower.tapRun)
        assertEquals(s.playerHp,tower.playerHp)
        assertEquals(tower,ChallengeRules.startArena(tower))
        val arena=ChallengeRules.startArena(s)
        assertEquals(ChallengeMode.ARENA,arena.challenge.mode)
        assertEquals(arena,ChallengeRules.startTower(arena))
        val fight=s.copy(tapRun=s.tapRun.copy(phase=TapPhase.FIGHT))
        assertEquals(fight,ChallengeRules.startTower(fight))
        assertEquals(fight,ChallengeRules.startArena(fight))
    }

    @Test fun towerClearsTwentyFloorsAndAwardsMilestoneOnce() {
        var s=ChallengeRules.startTower(hero())
        val initialId=s.challenge.opponent!!.id
        s=defeatCurrent(s)
        assertNotEquals(initialId,s.challenge.opponent!!.id)
        assertEquals(2,s.challenge.towerFloor)
        assertEquals(1,s.challenge.towerBest)
        assertEquals(ChallengeMode.TOWER,s.challenge.mode)
        while(s.challenge.mode==ChallengeMode.TOWER) {
            s=defeatCurrent(s)
        }
        assertEquals(20,s.challenge.towerBest)
        assertEquals(1,s.challenge.towerClears)
        assertEquals(setOf(5,10,15,20),s.challenge.towerMilestones)
        assertEquals(15,s.summonStones) // 2+3+4+6
        assertEquals(8,s.tapRun.floor)
        assertEquals(79,s.playerHp)
        assertTrue(s.companion.level>hero().companion.level)
        assertTrue(s.tapRun.gold>90)
        val once=s.summonStones
        s=ChallengeRules.startTower(s)
        repeat(5){s=defeatCurrent(s)}
        assertEquals(once,s.summonStones)
        assertEquals(5,s.challenge.towerFloor)
    }

    @Test fun guardAbsorbsTimedStrikeIndependentOfTaps() {
        val first=ChallengeRules.startTower(hero())
        val guarded=ChallengeRules.defend(first)
        assertTrue(guarded.challenge.guardActive)
        assertEquals(ChallengeRules.GUARD_COOLDOWN_MS,
            guarded.challenge.guardCooldownMs)
        val afterTap=ChallengeRules.attack(guarded,noCrit)
        assertEquals(guarded.challenge.attackTimerMs,
            afterTap.challenge.attackTimerMs)
        var withGuard=guarded
        var withoutGuard=first
        repeat(13) {
            withGuard=ChallengeRules.tick(withGuard,250)
            withoutGuard=ChallengeRules.tick(withoutGuard,250)
        }
        assertTrue(withGuard.challenge.playerHp>withoutGuard.challenge.playerHp)
        assertFalse(withGuard.challenge.guardActive)
        assertTrue(withGuard.challenge.guardCooldownMs>0)
        assertEquals(withGuard,ChallengeRules.defend(withGuard))
    }

    @Test fun losingTowerKeepsPermanentAndExplorationProgress() {
        val initial=hero().copy(challenge=ChallengeState(
            mode=ChallengeMode.TOWER,towerFloor=9,towerBest=8,
            towerMilestones=setOf(5),playerHp=1,
            opponent=WildMonster("guard","番人","闇",90,25),
            opponentHp=90,attackTimerMs=100
        ))
        val after=ChallengeRules.tick(initial,100)
        assertEquals(ChallengeMode.NONE,after.challenge.mode)
        assertEquals(8,after.challenge.towerBest)
        assertEquals(setOf(5),after.challenge.towerMilestones)
        assertEquals(initial.tapRun,after.tapRun)
        assertEquals(initial.playerHp,after.playerHp)
        assertEquals(initial.captures,after.captures)
    }

    @Test fun arenaThreeWavesRatingAndTierRewardsOnce() {
        var s=hero()
        // No instant free wins, and the three-wave opposition matters.
        var round=ChallengeRules.startArena(s)
        assertEquals(1,round.challenge.arenaWave)
        round=defeatCurrent(round)
        assertEquals(2,round.challenge.arenaWave)
        round=defeatCurrent(round)
        assertEquals(3,round.challenge.arenaWave)
        round=defeatCurrent(round)
        assertEquals(ChallengeMode.NONE,round.challenge.mode)
        assertEquals(1,round.challenge.arenaWins)
        assertTrue(round.challenge.arenaRating>1000)
        s=round
        var rounds=1
        while(s.challenge.arenaRating<1200 && rounds++<15) {
            s=ChallengeRules.startArena(s)
            repeat(3){s=defeatCurrent(s)}
        }
        assertTrue(s.challenge.arenaRating>=1200)
        assertEquals(1,s.summonStones)
        assertEquals("シルバー",ChallengeRules.tier(s.challenge.arenaRating))
        val awarded=s.summonStones
        // Repeated participation is okay, but crossing Silver twice pays once.
        repeat(5) {
            s=ChallengeRules.startArena(s)
            s=ChallengeRules.retreat(s)
        }
        assertTrue(s.challenge.arenaRating<1200)
        repeat(5) {
            s=ChallengeRules.startArena(s)
            repeat(3) {s=defeatCurrent(s)}
        }
        assertTrue(s.challenge.arenaRating>=1200)
        assertEquals(awarded,s.summonStones)
        assertTrue(s.challenge.arenaBestRating>=1200)
    }

    @Test fun retreatPenaltyOnlyAppliesToArenaAndCanClaimGoalsOnce() {
        var s=ChallengeRules.startArena(hero())
        s=ChallengeRules.retreat(s)
        assertEquals(1,s.challenge.arenaLosses)
        assertEquals(988,s.challenge.arenaRating)
        assertEquals(ChallengeMode.NONE,s.challenge.mode)
        val tower=ChallengeRules.startTower(s)
        val out=ChallengeRules.retreat(tower)
        assertEquals(1,out.challenge.arenaLosses)
        assertEquals(988,out.challenge.arenaRating)
        val qualified=out.copy(challenge=out.challenge.copy(towerBest=10))
        val first=GrowthRules.claim(qualified,"tower5")
        val again=GrowthRules.claim(first,"tower5")
        assertEquals(1,first.summonStones)
        assertEquals(first,again)
    }
}
