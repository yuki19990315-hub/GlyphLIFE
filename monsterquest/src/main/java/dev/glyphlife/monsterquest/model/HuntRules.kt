package dev.glyphlife.monsterquest.model

import kotlin.random.Random

/**
 * A focused encounter search, not a guaranteed encounter or a random reroll.
 * Players must discover a non-boss species in their Pokédex first.
 * The target is an ID from their existing catalog so name collisions across
 * elements or AI-created species don't silently select the wrong monster.
 */
object HuntRules {
    const val TARGET_RATE_PERCENT=45

    fun targets(state:QuestState):List<WildMonster> =
        state.catalog.filter{!it.isBoss && it.species.isNotBlank()}
            .distinctBy{it.species to it.element}

    fun selected(state:QuestState):WildMonster? =
        targets(state).firstOrNull {it.id==state.huntTargetId}

    fun choose(state:QuestState,targetId:String):QuestState {
        if(state.inBattle || ChallengeRules.active(state))return state
        if(targetId.isBlank())return state.copy(huntTargetId="")
        if(targets(state).none{it.id==targetId})return state
        return state.copy(huntTargetId=targetId)
    }

    /** A valid target replaces the normally generated non-boss encounter
     * with 45% probability. All other encounters keep the original random
     * distribution. Boss scheduling is never altered. */
    fun draw(state:QuestState,random:Random=Random.Default):WildMonster? {
        val target=selected(state)?:return null
        return if(random.nextInt(100)<TARGET_RATE_PERCENT)target else null
    }
}
