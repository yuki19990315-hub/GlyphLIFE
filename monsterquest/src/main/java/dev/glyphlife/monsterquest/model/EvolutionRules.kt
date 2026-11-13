package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterDesign

/** Evolution is individual training, not total victories by unrelated monsters. */
object EvolutionRules {
    fun requiredVictories(stage:Int):Int=GrowthRules.requiredVictories(stage)
    fun requiredLevel(stage:Int):Int=GrowthRules.requiredLevel(stage)

    fun canEvolve(state:QuestState,id:String):Boolean {
        if(state.inBattle)return false
        val monster=state.captures.find{it.id==id} ?: return false
        return !monster.isBoss && monster.stage in 1..2 &&
            monster.level>=requiredLevel(monster.stage) &&
            monster.victories>=requiredVictories(monster.stage)
    }

    fun evolve(state:QuestState,id:String):QuestState {
        require(canEvolve(state,id)) {
            "この仲間自身のレベル・出撃勝利数を満たしていません"
        }
        val old=state.captures.first{it.id==id}
        val stage=old.stage+1
        val next=old.copy(
            stage=stage,
            maxHp=(old.maxHp+12*stage).coerceIn(1,100000),
            attack=(old.attack+3*stage).coerceIn(1,10000)
        ).let { it.copy(pixels=GlyphProjector.projectFor(it,MonsterDesign.sprite(it))) }
        return state.copy(
            captures=state.captures.map{if(it.id==id)next else it},
            catalog=state.catalog.map{
                if(it.species==next.species && it.element==next.element &&
                    it.stage<stage)it.copy(stage=stage) else it
            },
            tapRun=state.tapRun.copy(message=old.species+"が進化"+stage+"へ！"),
            message=old.species+"が進化して段階"+stage+"になった！"
        )
    }
}
