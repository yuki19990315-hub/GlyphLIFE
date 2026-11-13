package dev.glyphlife.monsterquest.model

/**
 * Permanent progression, separate from the reset-on-death exploration floor.
 * All XP/upgrade operations are deterministic and capped.
 */
object GrowthRules {
    const val LEVEL_CAP=50
    const val UPGRADE_CAP=12

    fun xpNeeded(level:Int):Int = if(level>=LEVEL_CAP) 0 else
        25+level.coerceIn(1,LEVEL_CAP)*12

    fun requiredLevel(stage:Int):Int=when(stage) {
        1->7
        2->16
        else->LEVEL_CAP
    }

    fun requiredVictories(stage:Int):Int=when(stage) {
        1->3
        2->12
        else->Int.MAX_VALUE
    }

    private fun advance(level:Int,experience:Int,amount:Int):Pair<Int,Int> {
        var lv=level.coerceIn(1,LEVEL_CAP)
        var xp=experience.coerceAtLeast(0)+amount.coerceAtLeast(0)
        while(lv<LEVEL_CAP && xp>=xpNeeded(lv)) {
            xp-=xpNeeded(lv)
            lv++
        }
        return lv to if(lv>=LEVEL_CAP)0 else xp
    }

    fun rewardBattle(state:QuestState,boss:Boolean,floor:Int):QuestState {
        val earned=if(boss) 110+floor.coerceAtMost(100)*4
            else 20+floor.coerceAtMost(100)*2
        val (mainLevel,mainXp)=advance(state.companion.level,state.companionXp,earned)
        val roster=state.captures.map { monster ->
            if(monster.id !in state.partyIds) monster else {
                val (level,xp)=advance(monster.level,monster.xp,earned*3/4)
                val gained=(level-monster.level).coerceAtLeast(0)
                TalentRules.awaken(monster.copy(level=level,xp=xp,
                    victories=(monster.victories+1).coerceAtMost(100000),
                    maxHp=(monster.maxHp+gained*3).coerceAtMost(100000),
                    attack=(monster.attack+gained).coerceAtMost(10000)))
            }
        }
        return state.copy(
            companion=TalentRules.awaken(state.companion.copy(level=mainLevel)),
            companionXp=mainXp,captures=roster,
            bossWins=(state.bossWins+(if(boss)1 else 0)).coerceAtMost(999999),
            tapRun=state.tapRun.copy(message=state.tapRun.message+
                "　経験値 +"+earned+"（仲間は75%）"+
                (if(mainLevel>state.companion.level)"　Lv."+mainLevel+"に成長！" else ""))
        )
    }

    fun upgradeCost(level:Int):Int=60+level.coerceIn(0,UPGRADE_CAP)*60

    fun buy(state:QuestState,kind:String):QuestState {
        val current=when(kind) {
            "attack"->state.attackUpgrade
            "defense"->state.defenseUpgrade
            else->return state
        }
        if(current>=UPGRADE_CAP)return state
        val cost=upgradeCost(current)
        if(state.tapRun.gold<cost)return state
        val upgraded=state.copy(
            attackUpgrade=state.attackUpgrade+(if(kind=="attack")1 else 0),
            defenseUpgrade=state.defenseUpgrade+(if(kind=="defense")1 else 0),
            tapRun=state.tapRun.copy(gold=state.tapRun.gold-cost,
                message=(if(kind=="attack")"攻撃修練" else "守護修練")+
                    "を強化！ 次は"+upgradeCost(current+1)+"G"))
        return upgraded
    }

    fun buySupply(state:QuestState,kind:String):QuestState {
        val cost=when(kind) { "potion"->35; "orb"->45; else->return state }
        if(state.tapRun.gold<cost)return state
        if(kind=="potion" && state.potions>=99)return state
        if(kind=="orb" && state.captureOrbs>=99)return state
        return state.copy(
            potions=state.potions+(if(kind=="potion")1 else 0),
            captureOrbs=state.captureOrbs+(if(kind=="orb")1 else 0),
            tapRun=state.tapRun.copy(gold=state.tapRun.gold-cost,
                message=if(kind=="orb")"捕獲玉を購入した" else "回復薬を購入した"))
    }

    data class Achievement(
        val id:String,val title:String,val target:String,val reward:Int,
        val ready:(QuestState)->Boolean
    )
    val achievements=listOf(
        Achievement("kills10","初陣の狩人","敵を10体倒す",1){it.defeated>=10},
        Achievement("kills50","熟練の冒険者","敵を50体倒す",2){it.defeated>=50},
        Achievement("kills200","伝説への道","敵を200体倒す",5){it.defeated>=200},
        Achievement("floor10","奥地への挑戦","10Fに到達",2){it.tapRun.bestFloor>=10},
        Achievement("floor25","深層の探検家","25Fに到達",4){it.tapRun.bestFloor>=25},
        Achievement("floor50","深淵の踏破者","50Fに到達",8){it.tapRun.bestFloor>=50},
        Achievement("boss3","守護者を破る","ボスを3体倒す",3){it.bossWins>=3},
        Achievement("boss10","王者の証","ボスを10体倒す",5){it.bossWins>=10},
        Achievement("capture5","仲間の輪","5匹捕獲する",2){it.captures.size>=5},
        Achievement("level15","努力の証","相棒Lv15に到達",2){it.companion.level>=15},
        Achievement("evolve2","進化の兆し","仲間を第2形態へ進化",2){s->
            s.captures.any{it.stage>=2}},
        Achievement("evolve3","究極の姿","仲間を第3形態へ進化",4){s->
            s.captures.any{it.stage>=3}},
        Achievement("tower5","塔の第一関門","試練の塔5Fを突破",1){
            it.challenge.towerBest>=5},
        Achievement("tower10","塔の踏破者","試練の塔10Fを突破",2){
            it.challenge.towerBest>=10},
        Achievement("tower20","頂を極めし者","試練の塔20Fを完全踏破",5){
            it.challenge.towerBest>=20},
        Achievement("arena3","アリーナの新星","模擬アリーナで3勝",2){
            it.challenge.arenaWins>=3},
        Achievement("arena10","歴戦の闘士","模擬アリーナで10勝",3){
            it.challenge.arenaWins>=10},
        Achievement("arenaGold","黄金の闘士","アリーナでゴールドに昇格",4){
            it.challenge.arenaBestRating>=1450}
    )

    fun claim(state:QuestState,id:String):QuestState {
        val reward=achievements.firstOrNull{it.id==id} ?: return state
        if(id in state.achievementsClaimed || !reward.ready(state))return state
        return state.copy(
            summonStones=(state.summonStones+reward.reward).coerceAtMost(999),
            achievementsClaimed=state.achievementsClaimed+id,
            tapRun=state.tapRun.copy(message="実績「"+reward.title+
                "」達成！ 召喚結晶+"+reward.reward))
    }
}
