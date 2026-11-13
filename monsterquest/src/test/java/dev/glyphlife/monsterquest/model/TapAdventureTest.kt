package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TapAdventureTest {
    private val lucky=object:Random() {
        override fun nextBits(bitCount:Int):Int=0
    }
    private fun start()=QuestState(companion=Companion(level=6))
    private fun enemy(boss:Boolean=false)=WildMonster(
        id="encounter-1",species=if(boss)"月蝕の竜" else "ライゼル",
        element=if(boss)"闇" else "雷",maxHp=if(boss)210 else 30,
        attack=if(boss)25 else 6,isBoss=boss
    )

    @Test fun tapMovesForwardThenTurnsIntoComboBattle() {
        var s=start()
        s=TapAdventure.tap(s,lucky)
        assertEquals(1,s.tapRun.steps)
        assertEquals(TapPhase.WALK,s.tapRun.phase)
        s=TapAdventure.tap(s,lucky)
        assertEquals(TapPhase.FIGHT,s.tapRun.phase)
        assertNotNull(s.tapRun.enemy)
        assertEquals(2,s.tapRun.steps)
        val hp=s.tapRun.enemyHp
        s=TapAdventure.tap(s,lucky)
        assertTrue(s.tapRun.enemyHp<hp)
        assertEquals(1,s.tapRun.combo)
        assertTrue(s.catalog.isNotEmpty())
    }

    @Test fun bossEveryFifthFloorHasDifferentDodgeMechanicAndReward() {
        var s=start().copy(tapRun=TapRun(floor=5,steps=6))
        s=TapAdventure.tap(s,lucky)
        assertEquals(TapPhase.BOSS,s.tapRun.phase)
        assertTrue(s.tapRun.enemy!!.isBoss)
        assertTrue(s.tapRun.enemyHp>=200)
        val target=s.tapRun.bossTarget
        s=TapAdventure.dodge(s,-target)
        repeat(3){s=TapAdventure.tap(s,lucky)}
        assertTrue(s.tapRun.bossCountdown in 2..3)
        assertTrue(s.tapRun.weakpointTaps>0)
        assertEquals(100,s.playerHp)
        val max=s.tapRun.enemyHp
        s=TapAdventure.tap(s,lucky)
        assertTrue(s.tapRun.enemyHp<max)
        // Force low boss HP, with the same boss/floor, to verify guarantee.
        s=s.copy(tapRun=s.tapRun.copy(enemyHp=1))
        s=TapAdventure.tap(s,lucky)
        assertEquals(TapPhase.WALK,s.tapRun.phase)
        assertEquals(6,s.tapRun.floor)
        assertEquals(2,s.summonStones)
    }

    @Test fun defeatRestartsAtOneWithoutLosingInventoryOrMonsters() {
        val owned=enemy().copy(id="my-pet")
        val ai=owned.copy(id="ai-pet",species="新しい竜",lineage="ai-summoned")
        val state=start().copy(playerHp=1,potions=0,summonStones=4,
            captureOrbs=7,captures=listOf(owned),catalog=listOf(ai),
            partyIds=listOf(owned.id),
            tapRun=TapRun(floor=10,bestFloor=10,phase=TapPhase.BOSS,
                enemy=enemy(true),enemyHp=190,bossCountdown=1,lane=0))
        val after=TapAdventure.tap(state,lucky)
        assertEquals(1,after.tapRun.floor)
        assertEquals(TapPhase.WALK,after.tapRun.phase)
        assertEquals(1,after.tapRun.deaths)
        assertEquals(10,after.tapRun.bestFloor)
        assertEquals(100,after.playerHp)
        assertEquals(4,after.summonStones)
        assertEquals(7,after.captureOrbs)
        assertEquals(state.captures,after.captures)
        assertEquals(state.catalog,after.catalog)
        assertEquals(state.partyIds,after.partyIds)
    }

    @Test fun captureConsumesBallAndRegistersMember() {
        val wild=enemy()
        val s=start().copy(captureOrbs=2,tapRun=TapRun(
            phase=TapPhase.FIGHT,enemy=wild,enemyHp=1))
        val a=TapAdventure.capture(s,lucky)
        assertEquals(1,a.captureOrbs)
        assertEquals(1,a.captures.size)
        assertEquals(TapPhase.WALK,a.tapRun.phase)
        assertEquals(1,a.partyIds.size)
        assertEquals(0,a.tapRun.enemyHp)
    }

    @Test fun branchHasMultipleChoicesAndSendsPlayerToNextFloor() {
        val s=start().copy(playerHp=45,summonStones=0,captureOrbs=2,
            tapRun=TapRun(floor=3,steps=7,phase=TapPhase.BRANCH))
        val rest=TapAdventure.branch(s,0,lucky)
        assertEquals(73,rest.playerHp)
        assertEquals(4,rest.tapRun.floor)
        val treasure=TapAdventure.branch(s,1,lucky)
        assertEquals(4,treasure.tapRun.floor)
        assertEquals(1,treasure.summonStones)
        assertEquals(3,treasure.captureOrbs)
        assertTrue(treasure.tapRun.gold>0)
        assertEquals(s,TapAdventure.branch(s,3,lucky))
    }

    @Test fun offlineAndAiSummonsConsumeOnlyOwnedStones() {
        val fresh=start()
        assertEquals(fresh,TapAdventure.offlineSummon(fresh,lucky))
        val funded=fresh.copy(summonStones=2)
        val first=TapAdventure.offlineSummon(funded,lucky)
        assertEquals(1,first.summonStones)
        assertEquals(1,first.captures.size)
        val ai=enemy().copy(species="オリジナル氷竜",element="水")
        val second=TapAdventure.registerSummon(first,ai,generatedByAi=true)
        assertEquals(0,second.summonStones)
        assertEquals(2,second.captures.size)
        assertTrue(second.catalog.any {
            it.species=="オリジナル氷竜" && it.lineage=="ai-summoned"
        })
        assertEquals(second,TapAdventure.offlineSummon(second,lucky))
    }

    @Test fun aiRegisteredSpeciesCanAppearAsEnemies() {
        val ai=enemy().copy(species="オリジナル氷竜",element="水",
            lineage="ai-summoned")
        var s=start().copy(catalog=listOf(ai),
            tapRun=TapRun(floor=1,steps=1))
        s=TapAdventure.tap(s,lucky)
        assertEquals(TapPhase.FIGHT,s.tapRun.phase)
        assertEquals("オリジナル氷竜",s.tapRun.enemy!!.species)
    }

    @Test fun partySelectionIsLimitedToThreeAndSupportsDeselection() {
        val members=(1..4).map { enemy().copy(id="pet"+it) }
        var s=start().copy(captures=members)
        members.forEach { s=TapAdventure.selectParty(s,it.id) }
        assertEquals(3,s.partyIds.size)
        assertFalse("pet1" in s.partyIds)
        s=TapAdventure.selectParty(s,"pet2")
        assertEquals(2,s.partyIds.size)
        assertEquals(s,TapAdventure.selectParty(s,"not-owned"))
    }
}
