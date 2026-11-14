package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test

class RosterRulesTest {
    private fun monster(id:String,lv:Int,rare:Boolean=false,
                        slots:List<StatSlot> = emptyList()) =
        WildMonster(id=id,species="モコ",element="草",
            maxHp=80,attack=8,level=lv,
            lineage=if(rare)"rare" else "",
            statSlots=slots)
    private val low=monster("normal",3)
    private val high=monster("high",25,
        slots=listOf(StatSlot("attack",10,3)))
    private val rare=monster("rare",10,rare=true,
        slots=listOf(StatSlot("hp",15,3)))
    private fun sample():QuestState=QuestState(captures=listOf(low,high,rare),
        partyIds=listOf("high"))

    @Test fun bookmarkingOnlyKnownOwnedCreaturePreservesPartyAndPotential() {
        val s=sample()
        assertEquals(s,RosterRules.toggleFavorite(s,"not-owned"))
        val a=RosterRules.toggleFavorite(s,high.id)
        assertEquals(setOf(high.id),a.favoriteMonsterIds)
        assertEquals(s.captures,a.captures)
        assertEquals(s.partyIds,a.partyIds)
        assertEquals(high.statSlots,a.captures[1].statSlots)
        val b=RosterRules.toggleFavorite(a,high.id)
        assertTrue(b.favoriteMonsterIds.isEmpty())
    }

    @Test fun defaultSortingIsNewestFirstButFiltersFavoritesSafely() {
        val s=sample()
        assertEquals(listOf("rare","high","normal"),
            RosterRules.ordered(s).map{it.id})
        val fav=RosterRules.toggleFavorite(
            RosterRules.toggleFavorite(s,"high"),"normal")
        assertEquals(listOf("high","normal"),
            RosterRules.ordered(fav,onlyFavorites=true).map{it.id})
    }

    @Test fun levelPotentialAndRareSortingKeepAllMonstersWithoutMutation() {
        val s=sample()
        val input=s.captures.toList()
        assertEquals(listOf("high","rare","normal"),
            RosterRules.ordered(s,RosterOrder.LEVEL).map{it.id})
        assertEquals(listOf("rare","high","normal"),
            RosterRules.ordered(s,RosterOrder.POTENTIAL).map{it.id})
        assertEquals("rare",RosterRules.ordered(
            s,RosterOrder.RARE).first().id)
        assertEquals(input,s.captures)
    }

    @Test fun maximumThreeIdenticalPotentialStatsStillSumInRanking() {
        val triple=monster("triple",30,
            slots=listOf(StatSlot("attack",4,1),
                StatSlot("attack",10,2),
                StatSlot("attack",12,3)))
        val s=sample().copy(captures=listOf(low,high,rare,triple))
        assertEquals(triple.id,RosterRules.ordered(s,
            RosterOrder.POTENTIAL).first().id)
        assertEquals(26,TalentRules.ownStats(triple.statSlots).attack)
    }
}
