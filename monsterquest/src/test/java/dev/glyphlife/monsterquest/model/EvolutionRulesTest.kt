package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import org.junit.Assert.*
import org.junit.Test

class EvolutionRulesTest {
    private val one=WildMonster(
        id="f537aaec-3e3e-40c1-981f-428662b39740",species="獣の精霊",
        element="草",maxHp=32,attack=5,bodyType="BEAST",
        motifPrimary="wolf snout",motifSecondary="leaf armor"
    )

    @Test fun evolveRequiresVictoriesAndPreservesIdentity() {
        var state=QuestState(captures=listOf(one),defeated=0)
        assertFalse(EvolutionRules.canEvolve(state,one.id))
        state=state.copy(defeated=2)
        val evolved=EvolutionRules.evolve(state,one.id).captures.single()
        assertEquals(one.id,evolved.id)
        assertEquals("BEAST",evolved.bodyType)
        assertEquals(one.motifSecondary,evolved.motifSecondary)
        assertEquals(2,evolved.stage)
        assertTrue(evolved.maxHp>one.maxHp)
        assertTrue(evolved.attack>one.attack)
        assertEquals(169,evolved.pixels.length)
        assertEquals(GlyphProjector.project(MonsterDesign.sprite(evolved)),evolved.pixels)
    }

    @Test fun threeStagesEndAtThirdAndStayDeterministic() {
        val middle=EvolutionRules.evolve(QuestState(captures=listOf(one),defeated=4),one.id)
        val final=EvolutionRules.evolve(middle,one.id)
        assertEquals(3,final.captures.single().stage)
        assertFalse(EvolutionRules.canEvolve(final,one.id))
        assertNotEquals(middle.captures.single().pixels,final.captures.single().pixels)
    }

    @Test fun cannotEvolveWhileFightingOrMissing() {
        val state=QuestState(inBattle=true,defeated=100,captures=listOf(one))
        assertFalse(EvolutionRules.canEvolve(state,one.id))
        assertFalse(EvolutionRules.canEvolve(state.copy(inBattle=false),"missing"))
    }
}
