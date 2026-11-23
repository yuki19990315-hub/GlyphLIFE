package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test

class SummonAccessTest {
    @Test fun aNewPlayerCanReceiveOneWelcomeCrystal() {
        val start=QuestState()
        assertTrue(StarterSummonGift.eligible(start))
        val granted=start.copy(summonStones=1)
        assertNull(SummonAccess.commonBlock(granted,false))
        assertFalse(StarterSummonGift.eligible(granted))
    }

    @Test fun theExistingRosterAndCurrencyAreNotOverwritten() {
        val member=WildMonster("pet","竜","火",20,4)
        assertFalse(StarterSummonGift.eligible(QuestState(captures=listOf(member))))
        assertFalse(StarterSummonGift.eligible(QuestState(summonStones=5)))
    }

    @Test fun missingApiKeyBlocksOnlyAiSummoning() {
        val state=QuestState(summonStones=1)
        assertNull(SummonAccess.commonBlock(state,false))
        assertTrue(SummonAccess.aiBlock(state,false,"")!!.contains("APIキー"))
        assertNull(SummonAccess.aiBlock(state,false,"key"))
    }

    @Test fun noCurrencyOrBattlesExplainWhySummonIsUnavailable() {
        assertTrue(SummonAccess.commonBlock(QuestState(),false)!!.contains("0個"))
        val battling=QuestState(summonStones=2,tapRun=TapRun(phase=TapPhase.FIGHT))
        assertTrue(SummonAccess.commonBlock(battling,false)!!.contains("戦闘"))
        assertTrue(SummonAccess.commonBlock(QuestState(summonStones=3),true)!!
            .contains("処理中"))
    }

    @Test fun fullCaptureRosterBlocksSummon() {
        val hundred=(0 until 100).map{WildMonster("id$it","種$it","水",20,4)}
        assertTrue(SummonAccess.commonBlock(QuestState(
            summonStones=5,captures=hundred),false)!!.contains("100体"))
    }
}
