package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RelicRulesTest {
    private val neverDrop=object:Random() {
        override fun nextBits(bitCount:Int):Int =
            if(bitCount>=7)90 else 2
    }
    private val alwaysDrop=object:Random() {
        override fun nextBits(bitCount:Int):Int=0
    }
    private val boss=WildMonster(id="boss-1",species="月蝕の竜",
        element="闇",maxHp=170,attack=28,isBoss=true)
    private val normal=boss.copy(isBoss=false)
    private fun newState()=QuestState(
        companion=Companion(id="hero",name="ルナ",level=10),
        captures=listOf(
            WildMonster(id="ally",species="ライゼル",element="雷",
                maxHp=50,attack=15)
        ),
        partyIds=listOf("ally")
    )

    @Test fun bossOnlyAndFourthDryVictoryGuaranteesRelic() {
        var state=newState()
        repeat(9) {
            state=RelicRules.reward(state,normal,5,alwaysDrop)
        }
        assertEquals(0,state.relicDryBosses)
        assertTrue(state.bossRelics.isEmpty())
        repeat(3) {
            state=RelicRules.reward(state,boss,5,neverDrop)
            assertTrue(state.bossRelics.isEmpty())
        }
        assertEquals(3,state.relicDryBosses)
        state=RelicRules.reward(state,boss,5,neverDrop)
        assertEquals(1,state.bossRelics.size)
        assertEquals("月蝕の竜",state.bossRelics.first().source)
        assertEquals(0,state.relicDryBosses)
    }

    @Test fun equipmentHasRealBonusesOnlyForActiveUniqueOwners() {
        val first=BossRelic("relic1","moonfang","月蝕の竜",5)
        val second=BossRelic("relic2","starseal","星光の守護者",10)
        var s=newState().copy(bossRelics=listOf(first,second))
        s=RelicRules.equip(s,"hero",first.id)
        s=RelicRules.equip(s,"ally",second.id)
        assertEquals(6,RelicRules.stats(s).attack)
        assertEquals(4,RelicRules.stats(s).defense)
        // An unselected ally's artifact does not buff the team.
        s=s.copy(partyIds=emptyList())
        assertEquals(0,RelicRules.stats(s).defense)
        s=RelicRules.equip(s,"ally",first.id)
        assertFalse("hero" in s.relicEquipment)
        assertEquals(0,RelicRules.stats(s).attack)
        assertEquals(first.id,s.relicEquipment["ally"])
        // Same artifact is never counted more than once.
        s=s.copy(relicEquipment=mapOf("hero" to first.id,"ally" to first.id))
        assertEquals(6,RelicRules.stats(s).attack)
        val busy=s.copy(inBattle=true)
        assertEquals(busy,RelicRules.equip(busy,"hero",second.id))
    }

    @Test fun capAndNegativeOperationsDoNotDuplicateRelics() {
        val full=(1..RelicRules.INVENTORY_LIMIT).map {
            BossRelic("instance"+it,"moonfang","boss",5)
        }
        val s=newState().copy(bossRelics=full,relicDryBosses=2)
        assertEquals(s,RelicRules.reward(s,boss,5,alwaysDrop))
        assertEquals(s,RelicRules.equip(s,"unknown","instance1"))
        assertEquals(s,RelicRules.equip(s,"hero","not-owned"))
        assertEquals(s,RelicRules.unequip(s,"hero"))
    }

    @Test fun recoveryAndFasterSkillCooldownComeFromEquippedGear() {
        val s=newState().copy(
            bossRelics=listOf(
                BossRelic("life","lifepearl","boss",5),
                BossRelic("clock","spiritclock","boss",10)
            ),
            relicEquipment=mapOf("hero" to "life","ally" to "clock"),
            playerHp=79
        )
        assertEquals(8,RelicRules.stats(s).heal)
        assertEquals(1,RelicRules.stats(s).charge)
        assertEquals(87,RelicRules.afterVictory(s,false).playerHp)
        val tick=SkillRules.countdown(mapOf("hero" to 5),1)
        assertEquals(3,tick["hero"])
    }

    @Test fun towerBossRewardDisplaysInChallengeLog() {
        val s=newState().copy(challenge=ChallengeState(message="塔10F突破"))
        val reward=RelicRules.reward(s,boss,10,alwaysDrop,challenge=true)
        assertEquals(1,reward.bossRelics.size)
        assertTrue(reward.challenge.message.contains("ボス限定装備"))
        assertEquals(s.tapRun,reward.tapRun)
    }
}
