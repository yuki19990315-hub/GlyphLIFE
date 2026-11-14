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
            if(bitCount>=7)99 else (1 shl bitCount)-1
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
        assertEquals(-1,dodge.tapRun.lane)
    }

    @Test fun fiveVictoriesGrantOrbGoldAndTenGrantCrystalOnce() {
        var s=fresh()
        repeat(10){index->
            s=s.copy(tapRun=TapRun(
                floor=2,phase=TapPhase.FIGHT,
                enemy=foe("victim-$index",hp=80),
                enemyHp=1))
            s=JourneyRules.pressRight(s,lucky)
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
        val attacked=JourneyRules.pressRight(s,lucky)
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
}
