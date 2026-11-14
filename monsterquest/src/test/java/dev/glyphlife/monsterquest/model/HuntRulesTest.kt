package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HuntRulesTest {
    private val lucky=object:Random(){
        override fun nextBits(bitCount:Int):Int=0
    }
    private fun target(id:String,species:String="モコ",element:String="草") =
        WildMonster(id=id,species=species,element=element,
            bodyType="BEAST",maxHp=60,attack=8)

    private fun sample():QuestState = QuestState(
        companion=Companion(id="hero",level=8),
        catalog=listOf(target("moco"),target("gear","ギア","雷"),
            target("boss","月蝕の竜","闇").copy(isBoss=true))
    )

    @Test fun canOnlyFocusKnownNonBossSpeciesAndCanClearFocus() {
        val s=sample()
        assertEquals(s,HuntRules.choose(s,"unknown"))
        assertEquals(s,HuntRules.choose(s,"boss"))
        val chosen=HuntRules.choose(s,"moco")
        assertEquals("moco",chosen.huntTargetId)
        assertEquals("モコ",HuntRules.selected(chosen)!!.species)
        assertNull(HuntRules.selected(HuntRules.choose(chosen,"")))
        assertTrue(HuntRules.choose(chosen,"").huntTargetId.isEmpty())
    }

    @Test fun targetSelectionKeepsAllOtherRpgProgressUntouched() {
        val existing=sample().copy(
            captures=listOf(target("owned")),
            summonStones=7,captureOrbs=9,partyIds=listOf("owned"),
            tapRun=TapRun(floor=13,bestFloor=18,gold=750,
                phase=TapPhase.WALK),bossWins=3)
        val chosen=HuntRules.choose(existing,"moco")
        assertEquals(existing.copy(huntTargetId="moco"),chosen)
        assertEquals(existing.captures,chosen.captures)
        assertEquals(existing.tapRun,chosen.tapRun)
        assertEquals(9,chosen.captureOrbs)
    }

    @Test fun trackingNeverAllowsUndiscoveredMonstersToBeForced() {
        val invalid=sample().copy(huntTargetId="not-in-catalog")
        assertNull(HuntRules.selected(invalid))
        assertNull(HuntRules.draw(invalid,lucky))
    }

    @Test fun sameSpeciesCanBeDrawnMultipleTimesWithoutDuplicateId() {
        val s=HuntRules.choose(sample(),"moco")
        assertEquals("モコ",HuntRules.draw(s,lucky)?.species)
        // Each encounter creates a new monster UUID, then captured specimens
        // independently roll a bonus skill and three unlockable stat slots.
        val first=TapAdventure.tap(s.copy(tapRun=TapRun(steps=1)),lucky)
        val second=TapAdventure.tap(s.copy(tapRun=TapRun(steps=1)),lucky)
        assertEquals(TapPhase.FIGHT,first.tapRun.phase)
        assertEquals("モコ",first.tapRun.enemy!!.species)
        assertEquals("モコ",second.tapRun.enemy!!.species)
        assertNotEquals(first.tapRun.enemy!!.id,second.tapRun.enemy!!.id)
        val a=TapAdventure.capture(first.copy(tapRun=first.tapRun.copy(enemyHp=1)),lucky)
        val b=TapAdventure.capture(second.copy(tapRun=second.tapRun.copy(enemyHp=1)),lucky)
        assertEquals(1,a.captures.size)
        assertEquals(1,b.captures.size)
        assertNotEquals(a.captures.single().id,b.captures.single().id)
        assertTrue(a.captures.single().extraSkillId.isNotBlank())
        assertTrue(b.captures.single().extraSkillId.isNotBlank())
    }

    @Test fun focusingSpeciesIsProbabilityNotGuaranteeAndNeverChangesBoss() {
        val s=HuntRules.choose(sample(),"moco")
        val rng=Random(735)
        var targeted=0
        var random=0
        repeat(1000) {
            if(HuntRules.draw(s,rng)==null)random++ else targeted++
        }
        assertTrue("targeted results must occur",targeted>300)
        assertTrue("other wild species must still occur",random>300)
        val bossState=s.copy(tapRun=TapRun(floor=5,steps=6))
        val boss=TapAdventure.tap(bossState,lucky)
        assertEquals(TapPhase.BOSS,boss.tapRun.phase)
        assertTrue(boss.tapRun.enemy!!.isBoss)
        assertNotEquals("モコ",boss.tapRun.enemy!!.species)
    }

    @Test fun cannotChangeTargetWhileFighting() {
        val s=sample().copy(inBattle=true)
        assertEquals(s,HuntRules.choose(s,"moco"))
    }

    @Test fun aiSummonedSpeciesCanBeFocusedWithoutLosingArtSource() {
        val owned=target("ai-catalog","新種の光獣","光").copy(
            lineage="ai-summoned",artSourceId="source-uuid")
        val s=sample().copy(catalog=listOf(owned),huntTargetId=owned.id,
            tapRun=TapRun(steps=1))
        val encountered=TapAdventure.tap(s,lucky)
        assertEquals("新種の光獣",encountered.tapRun.enemy!!.species)
        assertEquals("source-uuid",encountered.tapRun.enemy!!.artSourceId)
    }
}
