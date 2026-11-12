package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterDesign

/** Three-stage progression. Evolving retains individual ID and genetic design tags. */
object EvolutionRules {
    fun requiredVictories(stage:Int):Int = when(stage) {
        1 -> 2
        2 -> 4
        else -> Int.MAX_VALUE
    }

    fun canEvolve(state:QuestState, id:String):Boolean {
        if(state.inBattle) return false
        val monster=state.captures.find { it.id==id } ?: return false
        return !monster.isBoss && monster.stage in 1..2 &&
            state.defeated>=requiredVictories(monster.stage)
    }

    fun evolve(state:QuestState, id:String):QuestState {
        require(canEvolve(state,id)) { "この冒険での撃破数または進化段階が条件を満たしていません" }
        val old=state.captures.first { it.id==id }
        val stage=old.stage+1
        val next=old.copy(
            stage=stage,
            // Fixed growth rules, never selected by AI input.
            maxHp=(old.maxHp+12*stage).coerceIn(1,180),
            attack=(old.attack+3*stage).coerceIn(1,40)
        ).let { it.copy(pixels=GlyphProjector.project(MonsterDesign.sprite(it))) }
        return state.copy(
            captures=state.captures.map { if(it.id==id) next else it },
            message="${old.species}が進化して段階$stageになった！"
        )
    }
}
