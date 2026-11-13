package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test

class GrowthRulesTest {
    private val ally=WildMonster(id="ally1",species="雷獣",element="雷",
        maxHp=30,attack=10)
    private fun roster()=QuestState(
        companion=Companion(level=1),captures=listOf(ally),partyIds=listOf(ally.id),
        tapRun=TapRun(floor=2,gold=800)
    )

    @Test fun battleGrantsXpToActiveCompanionAndOnlySelectedParty() {
        val reserve=ally.copy(id="bench")
        var s=roster().copy(captures=listOf(ally,reserve))
        repeat(4){s=GrowthRules.rewardBattle(s,false,2)}
        assertTrue(s.companion.level>1)
        assertTrue(s.companionXp>=0)
        val grown=s.captures.first()
        assertTrue(grown.level>1)
        assertEquals(4,grown.victories)
        assertTrue(grown.attack>ally.attack)
        assertEquals(1,s.captures.last().level)
        assertEquals(0,s.captures.last().victories)
    }

    @Test fun bossGivesMoreXpAndAddsPermanentBossWin() {
        val a=GrowthRules.rewardBattle(roster(),true,5)
        val b=GrowthRules.rewardBattle(roster(),false,5)
        assertTrue(a.companion.level>=b.companion.level)
        assertEquals(1,a.bossWins)
        assertEquals(0,b.bossWins)
        assertTrue(a.captures.first().xp>=b.captures.first().xp ||
            a.captures.first().level>b.captures.first().level)
    }

    @Test fun trainingIsPermanentAndCannotOverspend() {
        val base=roster().copy(tapRun=TapRun(gold=60))
        val boosted=GrowthRules.buy(base,"attack")
        assertEquals(1,boosted.attackUpgrade)
        assertEquals(0,boosted.tapRun.gold)
        assertEquals(boosted,GrowthRules.buy(boosted,"attack"))
        assertEquals(base,GrowthRules.buy(base,"nonsense"))
        assertEquals(base,GrowthRules.buySupply(base.copy(tapRun=TapRun(gold=0)),"orb"))
    }

    @Test fun shopConsumablesHaveCostsAndCap() {
        val s=roster()
        val potion=GrowthRules.buySupply(s,"potion")
        assertEquals(s.potions+1,potion.potions)
        assertEquals(s.tapRun.gold-35,potion.tapRun.gold)
        val orb=GrowthRules.buySupply(s,"orb")
        assertEquals(s.captureOrbs+1,orb.captureOrbs)
        assertEquals(s.tapRun.gold-45,orb.tapRun.gold)
        assertEquals(s.copy(captureOrbs=99),
            GrowthRules.buySupply(s.copy(captureOrbs=99),"orb"))
    }

    @Test fun achievementCanOnlyBeClaimedOnceAndDoesNotResetOnDeath() {
        val ready=roster().copy(defeated=10)
        val first=GrowthRules.claim(ready,"kills10")
        assertEquals(ready.summonStones+1,first.summonStones)
        assertTrue("kills10" in first.achievementsClaimed)
        assertEquals(first,GrowthRules.claim(first,"kills10"))
        assertEquals(ready,GrowthRules.claim(ready,"boss10"))
    }

    @Test fun personalEvolutionRequiresOwnWinsEvenWithGlobalMilestone() {
        val s=roster().copy(defeated=1000)
        assertFalse(EvolutionRules.canEvolve(s,ally.id))
        assertFalse(EvolutionRules.canEvolve(s.copy(
            captures=listOf(ally.copy(level=7,victories=2))),ally.id))
        assertTrue(EvolutionRules.canEvolve(s.copy(
            captures=listOf(ally.copy(level=7,victories=3))),ally.id))
    }
}
