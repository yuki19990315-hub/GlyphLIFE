package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class JourneyRulesTest {
    private val lucky=object:Random(){
        override fun nextBits(bitCount:Int):Int=0
    }
    private val unlucky=object:Random(){
        override fun nextBits(bitCount:Int):Int =
            if(bitCount==32)199 else if(bitCount>=7)99
            else (1 shl bitCount)-1
    }
    private fun foe(id:String="foe",hp:Int=90):WildMonster =
        WildMonster(id=id,species="モコ",element="草",
            maxHp=hp,attack=5,bodyType="BEAST")
    private fun fresh():QuestState=QuestState(
        companion=Companion(id="hero",element="火",level=8),
        tapRun=TapRun(),captureOrbs=3,potions=2
    )

    @Test fun heldRightAdvancesInAutoIntervalsAndTriggersEncounter() {
        var s=fresh()
        val no=JourneyRules.tick(s,1,180,lucky)
        assertEquals(0,no.tapRun.steps)
        assertEquals(TapPhase.WALK,no.tapRun.phase)
        s=JourneyRules.tick(no,1,150,lucky)
        assertEquals(1,s.tapRun.steps)
        assertEquals(1,s.tapRun.distance)
        s=JourneyRules.tick(s,1,160,lucky)
        s=JourneyRules.tick(s,1,160,lucky)
        assertEquals(TapPhase.FIGHT,s.tapRun.phase)
        assertNotNull(s.tapRun.enemy)
        assertEquals(2,s.tapRun.distance)
        assertEquals(1,s.journey.totalEncounters)
    }

    @Test fun idleStillTicksEnemyAndHoldAttacksWithCooldown() {
        val f=foe(hp=500)
        var s=fresh().copy(tapRun=TapRun(phase=TapPhase.FIGHT,
            enemy=f,enemyHp=500,attackTimerMs=2700))
        s=JourneyRules.tick(s,1,160,lucky)
        assertEquals(500,s.tapRun.enemyHp)
        s=JourneyRules.tick(s,1,160,lucky)
        assertEquals(500,s.tapRun.enemyHp)
        s=JourneyRules.tick(s,1,160,lucky)
        assertTrue(s.tapRun.enemyHp<500)
        val hp=s.playerHp
        repeat(17){s=JourneyRules.tick(s,0,160,lucky)}
        assertTrue("enemy must still retaliate even while idle",s.playerHp<hp)
    }

    @Test fun tapLeftBacksOffOrRetreatsButCannotSkipBoss() {
        val walk=fresh().copy(tapRun=TapRun(floor=12,steps=4,distance=200))
        val back=JourneyRules.pressLeft(walk)
        assertEquals(3,back.tapRun.steps)
        assertEquals(12,back.tapRun.floor)
        assertEquals(200,back.tapRun.distance)

        val fighting=fresh().copy(tapRun=TapRun(floor=2,steps=6,
            phase=TapPhase.FIGHT,enemy=foe(),enemyHp=30),
            journey=JourneyState(chain=4,bestChain=9))
        val fled=JourneyRules.pressLeft(fighting)
        assertEquals(TapPhase.WALK,fled.tapRun.phase)
        assertEquals(4,fled.tapRun.steps)
        assertEquals(0,fled.journey.chain)
        assertNull(fled.tapRun.enemy)
        assertEquals(fighting.captures,fled.captures)

        val boss=fighting.copy(tapRun=TapRun(floor=5,
            phase=TapPhase.BOSS,enemy=foe(),enemyHp=30))
        val dodge=JourneyRules.pressLeft(boss)
        assertEquals(TapPhase.BOSS,dodge.tapRun.phase)
        assertEquals(0,dodge.tapRun.bossArenaLane)
    }

    @Test fun fiveVictoriesGrantOrbGoldAndTenGrantCrystalOnce() {
        var s=fresh()
        repeat(10){index->
            s=s.copy(tapRun=TapRun(
                floor=2,phase=TapPhase.FIGHT,
                enemy=foe("victim-$index",hp=80),
                enemyHp=1))
            s=JourneyRules.pressRight(s,unlucky)
            assertEquals(TapPhase.WALK,s.tapRun.phase)
            assertEquals(index+1,s.journey.chain)
        }
        assertEquals(5,s.captureOrbs)
        assertEquals(1,s.summonStones)
        assertTrue(s.tapRun.gold>0)
        assertEquals(10,s.journey.bestChain)
        val same=JourneyRules.record(s,s)
        assertEquals(s,same)
    }

    @Test fun rareEncounterCanBeCapturedAndKeepsRareLineage() {
        var s=fresh().copy(tapRun=TapRun(steps=1))
        s=TapAdventure.tap(s,lucky)
        assertEquals(TapPhase.FIGHT,s.tapRun.phase)
        assertEquals("rare",s.tapRun.enemy!!.lineage)
        assertTrue(s.tapRun.enemy!!.description.contains("希少個体"))
        s=JourneyRules.capture(s.copy(tapRun=
            s.tapRun.copy(enemyHp=1)),lucky)
        assertEquals(1,s.captures.size)
        assertEquals("rare",s.captures.single().lineage)
        assertTrue(s.captures.single().attack>=3)
        assertEquals(1,s.journey.totalCaptures)
    }

    @Test fun autoCaptureIsOptInAndOnlyAttemptsOncePerFoe() {
        val monster=foe("auto",100)
        var s=fresh().copy(tapRun=TapRun(
            phase=TapPhase.FIGHT,enemy=monster,enemyHp=34),
            journey=JourneyState(autoCapture=true))
        s=JourneyRules.tick(s,0,100,lucky)
        assertEquals(1,s.captures.size)
        assertEquals(2,s.captureOrbs)
        assertEquals("auto",s.journey.lastAutoCaptureId)
        val again=JourneyRules.tick(s,0,100,lucky)
        assertEquals(1,again.captures.size)
        assertEquals(2,again.captureOrbs)

        val failed=fresh().copy(tapRun=TapRun(
            phase=TapPhase.FIGHT,enemy=monster,enemyHp=34),
            journey=JourneyState(autoCapture=true))
        val miss=JourneyRules.tick(failed,0,100,unlucky)
        assertEquals(2,miss.captureOrbs)
        assertTrue(miss.captures.isEmpty())
        assertEquals("auto",miss.journey.lastAutoCaptureId)
        val next=JourneyRules.tick(miss,0,100,unlucky)
        assertEquals(2,next.captureOrbs)
    }

    @Test fun switchingToClassicPreventsArcadeTimerFromUpdatingState() {
        val s=fresh().copy(journey=JourneyState(classic=true))
        assertEquals(s,JourneyRules.tick(s,1,160,lucky))
    }

    @Test fun noRaidOrBossRewritesWhenUsingArcadeControls() {
        val monster=foe("boss",500).copy(isBoss=true)
        val s=fresh().copy(tapRun=TapRun(floor=5,steps=7,
            phase=TapPhase.BOSS,enemy=monster,enemyHp=500))
        val approached=JourneyRules.pressRight(s,lucky)
        assertEquals(2,approached.tapRun.bossArenaLane)
        assertEquals(500,approached.tapRun.enemyHp)
        val attacked=JourneyRules.pressRight(
            JourneyRules.pressRight(
                JourneyRules.pressRight(approached,lucky),lucky),lucky)
        assertEquals(TapPhase.BOSS,attacked.tapRun.phase)
        assertEquals(s.tapRun.floor,attacked.tapRun.floor)
        assertTrue(attacked.tapRun.enemyHp<500)
        assertEquals(s.bossRelics,attacked.bossRelics)
    }

    @Test fun catchRateAndElementMatchupsAreExplicitAndConsistent() {
        val monster=foe("water",100).copy(element="水")
        val s=fresh().copy(tapRun=TapRun(phase=TapPhase.FIGHT,
            enemy=monster,enemyHp=100))
        assertEquals(18,TapAdventure.captureChance(s))
        assertEquals(83,TapAdventure.captureChance(
            s.copy(tapRun=s.tapRun.copy(enemyHp=0))))
        assertEquals(125,ElementalRules.percentage("水","火"))
        assertEquals(90,ElementalRules.percentage("火","水"))
        assertEquals(100,ElementalRules.percentage("闇","雷"))
        assertEquals("有利 +25%",ElementalRules.label("光","闇"))
    }

    @Test fun shrineStopsAutoTravelUntilChosenAndNeverRepeatsOnSameFloor() {
        val start=fresh().copy(playerHp=35,
            tapRun=TapRun(floor=2,steps=3,distance=9))
        val discovered=JourneyRules.pressRight(start,unlucky)
        assertTrue(discovered.journey.shrinePending)
        assertEquals(3,discovered.tapRun.steps)
        assertEquals(discovered,JourneyRules.tick(discovered,1,160,lucky))
        assertEquals(discovered,JourneyRules.pressRight(discovered,lucky))
        assertEquals(discovered,JourneyRules.chooseShrine(discovered,-1))

        val healed=JourneyRules.chooseShrine(discovered,0)
        assertEquals(87,healed.playerHp)
        assertEquals(1,healed.journey.shrinesVisited)
        assertEquals(2,healed.journey.lastShrineFloor)
        assertFalse(healed.journey.shrinePending)
        assertFalse(JourneyRules.pressRight(healed,unlucky).journey.shrinePending)
    }

    @Test fun shrineRiskAndSuppliesAreBoundedAndDoNotDuplicate() {
        val start=fresh().copy(playerHp=11,captureOrbs=98,
            tapRun=TapRun(floor=6,steps=4),
            journey=JourneyState(shrinePending=true))
        val orbs=JourneyRules.chooseShrine(start,1)
        assertEquals(99,orbs.captureOrbs)
        assertEquals(98,start.captureOrbs)
        assertEquals(orbs,JourneyRules.chooseShrine(orbs,1))

        val risk=JourneyRules.chooseShrine(start,2)
        assertEquals(1,risk.playerHp)
        assertEquals(2,risk.summonStones)
        assertEquals(6,risk.journey.lastShrineFloor)
    }

    @Test fun normalHitsChargeFinisherAndFinisherKeepsVictoryRewards() {
        val monster=foe("burst",500)
        var s=fresh().copy(tapRun=TapRun(phase=TapPhase.FIGHT,
            enemy=monster,enemyHp=500))
        assertEquals(s,JourneyRules.burst(s,lucky))
        repeat(7){s=JourneyRules.pressRight(s,unlucky)}
        assertEquals(100,s.journey.burstGauge)
        assertTrue(s.tapRun.enemyHp<500)
        val used=JourneyRules.burst(s,unlucky)
        assertEquals(0,used.journey.burstGauge)
        assertEquals(1,used.journey.burstUses)
        assertTrue(used.tapRun.enemyHp<s.tapRun.enemyHp)
        assertEquals(used,JourneyRules.burst(used,lucky))

        val almost=used.copy(tapRun=used.tapRun.copy(enemyHp=1),
            journey=used.journey.copy(burstGauge=100))
        val victory=JourneyRules.burst(almost,unlucky)
        assertEquals(TapPhase.WALK,victory.tapRun.phase)
        assertEquals(1,victory.defeated)
        assertEquals(1,victory.journey.chain)
        assertEquals(2,victory.journey.burstUses)
        assertEquals(0,victory.journey.burstGauge)
        assertEquals(victory,JourneyRules.burst(victory,lucky))
    }

    @Test fun bossPerfectDodgeChargesBurstAndDefeatResetsIt() {
        val monster=foe("guardian",200).copy(isBoss=true,attack=20)
        val boss=fresh().copy(tapRun=TapRun(floor=5,
            phase=TapPhase.BOSS,enemy=monster,enemyHp=200,
            bossArenaLane=1,bossBullets=listOf(
                BossBarrageBullet(lane=0,ageMs=1380,flightMs=1400))),
            journey=JourneyState(burstGauge=50))
        val avoided=JourneyRules.tick(boss,0,100,unlucky)
        assertEquals(80,avoided.journey.burstGauge)
        assertEquals(3,avoided.tapRun.weakpointTaps)
        assertEquals(boss.playerHp,avoided.playerHp)

        val danger=boss.copy(playerHp=1,
            tapRun=boss.tapRun.copy(bossArenaLane=0),
            journey=JourneyState(burstGauge=100))
        val dead=JourneyRules.tick(danger,0,100,unlucky)
        assertEquals(1,dead.tapRun.floor)
        assertEquals(1,dead.tapRun.deaths)
        assertEquals(0,dead.journey.burstGauge)
    }
}
