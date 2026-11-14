package dev.glyphlife.monsterquest.model

/** A stable search/sort for up to 100 individually generated creatures.
 * It never modifies individual UUIDs, skill rolls, potentials or battle data.
 */
enum class RosterOrder { NEWEST, LEVEL, POTENTIAL, RARE }

object RosterRules {
    fun toggleFavorite(state:QuestState,id:String):QuestState {
        if(state.captures.none{it.id==id})return state
        val ids=state.favoriteMonsterIds
        return state.copy(favoriteMonsterIds=
            if(id in ids)ids-id else ids+id)
    }

    fun potentialScore(monster:WildMonster):Int =
        monster.statSlots.sumOf{slot ->
            val amount=slot.value.coerceIn(0,60)
            amount+(slot.rarity.coerceIn(1,3)-1)*3
        }

    fun ordered(state:QuestState,order:RosterOrder=RosterOrder.NEWEST,
                onlyFavorites:Boolean=false):List<WildMonster> {
        val found=state.captures.withIndex().filter{
            !onlyFavorites || it.value.id in state.favoriteMonsterIds
        }
        val comparator=when(order) {
            RosterOrder.NEWEST -> compareByDescending<IndexedValue<WildMonster>>{it.index}
            RosterOrder.LEVEL -> compareByDescending<IndexedValue<WildMonster>>{it.value.level}
                .thenByDescending{it.index}
            RosterOrder.POTENTIAL ->
                compareByDescending<IndexedValue<WildMonster>>{
                    potentialScore(it.value)
                }.thenByDescending{it.index}
            RosterOrder.RARE ->
                compareByDescending<IndexedValue<WildMonster>>{
                    if(it.value.lineage=="rare")1 else 0
                }.thenByDescending{it.index}
        }
        return found.sortedWith(comparator).map{it.value}
    }
}
