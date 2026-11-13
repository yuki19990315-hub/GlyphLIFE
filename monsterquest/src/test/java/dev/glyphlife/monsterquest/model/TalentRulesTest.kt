package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TalentRulesTest {
    private fun monster(id:String,level:Int=1)=WildMonster(
        id=id,species="雷獣",element="雷",bodyType="BEAST",
        maxHp=110,attack=18,level=level
    )
    private val target=WildMonster(
        id="enemy",species="古代の歯車王",element="雷",
        maxHp=900,attack=35,isBoss=true)

    @Test fun extraSkillIsIndependentOfSpeciesAndStableForSameId() {
        val a=TalentRules.awaken(monster("individual-a"))
        val repeat=TalentRules.awaken(monster("individual-a"))
        assertEquals(a.extraSkillId,repeat.extraSkillId)
        assertEquals(a,repeat)
        assertNotEquals(SkillRules.forMonster(a).id,a.extraSkillId)
        assertTrue(a.extraSkillId in TalentRules.skillPool)
        val distinct=(1..50).map {
            TalentRules.awaken(monster("random-"+
                it)).extraSkillId
        }.toSet()
        assertTrue("individual pool has variety",distinct.size>=5)
    }

    @Test fun threePermanentStatSlotsUnlockOnlyAtFiveFifteenThirty() {
        val id="my-unique-captured-wolf"
        assertEquals(0,TalentRules.awaken(monster(id,4)).statSlots.size)
        val first=TalentRules.awaken(monster(id,5))
        assertEquals(1,first.statSlots.size)
        assertEquals(1,TalentRules.awaken(first.copy(level=14)).statSlots.size)
        val two=TalentRules.awaken(first.copy(level=15))
        assertEquals(2,two.statSlots.size)
        assertEquals(first.statSlots.first(),two.statSlots.first())
        val three=TalentRules.awaken(two.copy(level=30))
        assertEquals(3,three.statSlots.size)
        assertEquals(two.statSlots,three.statSlots.take(2))
        assertTrue(three.statSlots.all {
            it.kind in TalentRules.statKinds && it.value>0 && it.rarity in 1..3
        })
        assertEquals(three.statSlots,
            TalentRules.awaken(three.copy(stage=3)).statSlots)
    }

    @Test fun repeatedStatKindsAreAllowedInAllThreeSlots() {
        // Every slot independently samples all four kinds WITH replacement.
        // This also ensures future refactors don't introduce de-duplication.
        val tripleAttack=(1..2000).asSequence().map { index ->
            TalentRules.awaken(monster("triple-attack-"+index,30))
        }.firstOrNull { result ->
            result.statSlots.size==3 &&
                result.statSlots.all { slot -> slot.kind=="attack" }
        }
        assertNotNull("All three Attack slots must be obtainable",tripleAttack)
        val rolled=tripleAttack!!
        val attackSum=rolled.statSlots.sumOf { it.value }
        assertEquals(attackSum,TalentRules.ownStats(rolled.statSlots).attack)
        assertEquals(0,TalentRules.ownStats(rolled.statSlots).defense)
        // Reload and evolution must not reroll duplicate slots.
        val evolved=TalentRules.awaken(rolled.copy(stage=3))
        assertEquals(rolled.statSlots,evolved.statSlots)
    }

    @Test fun threeIdenticalStatsStackWithoutClampingAtIndividualLevel() {
        val tripleCritical=listOf(
            StatSlot("critical",3,1),
            StatSlot("critical",6,2),
            StatSlot("critical",9,3)
        )
        assertEquals(18,TalentRules.ownStats(tripleCritical).critical)
        val tripleHp=listOf(
            StatSlot("hp",8,1),StatSlot("hp",16,2),StatSlot("hp",24,3)
        )
        assertEquals(48,TalentRules.ownStats(tripleHp).hp)
        val single=monster("stacked",30).copy(statSlots=tripleHp)
        val team=QuestState(
            companion=Companion(id="pet",level=30),
            captures=listOf(single),partyIds=listOf(single.id))
        assertEquals(148,TalentRules.maxHp(team))
    }

    @Test fun evolutionAndReloadDoNotRerollAnyExistingBonuses() {
        val grown=TalentRules.awaken(monster("same-id",30))
        val evolved=TalentRules.awaken(grown.copy(stage=3))
        assertEquals(grown.extraSkillId,evolved.extraSkillId)
        assertEquals(grown.statSlots,evolved.statSlots)
    }

    @Test fun growthUnlocksStatsOnIndividualWithoutChangingReserve() {
        val active=TalentRules.awaken(monster("active",4))
        val reserve=TalentRules.awaken(monster("reserve",4))
        val s=QuestState(
            companion=Companion(id="pet",level=4),
            captures=listOf(active,reserve),
            partyIds=listOf(active.id))
        val grown=GrowthRules.rewardBattle(s,boss=true,floor=20)
        assertTrue(grown.captures.first().level>=5)
        assertEquals(1,grown.captures.first().statSlots.size)
        assertEquals(reserve,grown.captures.last())
        assertEquals(1,grown.companion.statSlots.size)
    }

    @Test fun equippedTeamRollsActuallyAffectDamageAndMaxHp() {
        val a=monster("strong",30).copy(
            statSlots=listOf(StatSlot("attack",8,2),
                StatSlot("defense",5,2),StatSlot("hp",15,3)))
        val s=QuestState(
            companion=Companion(id="hero",level=10),
            captures=listOf(a),partyIds=listOf(a.id),
            tapRun=TapRun(floor=5,phase=TapPhase.BOSS,
                enemy=target,enemyHp=900,attackTimerMs=3500),
            playerHp=100)
        assertEquals(115,TalentRules.maxHp(s))
        val vanilla=s.copy(captures=listOf(a.copy(statSlots=emptyList())))
        val stronger=TapAdventure.tap(s,Random(1234))
        val weaker=TapAdventure.tap(vanilla,Random(1234))
        assertTrue(stronger.tapRun.enemyHp<weaker.tapRun.enemyHp)
        assertTrue(TalentRules.teamStats(s).defense>0)
    }

    @Test fun randomizedExtraSkillHasSeparateCooldownAndCanBeActivated() {
        val m=TalentRules.awaken(monster("skill-owner"))
        val state=QuestState(
            captures=listOf(m),partyIds=listOf(m.id),
            tapRun=TapRun(floor=5,phase=TapPhase.FIGHT,
                enemy=target.copy(isBoss=false),enemyHp=900),
            playerHp=55)
        val next=TapAdventure.useSkill(state,m.id,bonus=true)
        assertTrue(next.tapRun.skillCooldowns.containsKey(m.id+":extra"))
        assertFalse(next.tapRun.skillCooldowns.containsKey(m.id))
        assertEquals(next,TapAdventure.useSkill(next,m.id,bonus=true))
        val innate=TapAdventure.useSkill(next,m.id)
        assertTrue(innate.tapRun.skillCooldowns.containsKey(m.id))
        assertTrue(innate.tapRun.skillCooldowns.containsKey(m.id+":extra"))
    }
}
