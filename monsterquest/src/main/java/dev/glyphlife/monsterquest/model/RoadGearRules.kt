package dev.glyphlife.monsterquest.model

/** Original Monster Quest equipment. No reference-game names, art or item data are used. */
enum class RoadGearSlot { WEAPON, ARMOR }

data class RoadGear(
    val id:String,
    val name:String,
    val slot:RoadGearSlot,
    val price:Int,
    val bonus:RelicStats
)

/** A set can activate several independent passive skills at the same time. */
data class RoadGearSkill(
    val name:String,
    val detail:String,
    val bonus:RelicStats
)

data class RoadGearSet(
    val weapon:String,
    val armor:String,
    val skills:List<RoadGearSkill>
)

object RoadGearRules {
    const val STARTER_WEAPON="twig_blade"
    const val STARTER_ARMOR="traveler_coat"
    val starterOwned=setOf(STARTER_WEAPON,STARTER_ARMOR)

    val catalog=listOf(
        RoadGear(STARTER_WEAPON,"旅立ちの短剣",RoadGearSlot.WEAPON,0,RelicStats()),
        RoadGear("steel_blade","鋼の長剣",RoadGearSlot.WEAPON,100,
            RelicStats(attack=5)),
        RoadGear("storm_saber","蒼雷の剣",RoadGearSlot.WEAPON,230,
            RelicStats(attack=7,critical=4)),
        RoadGear("moon_staff","月晶の杖",RoadGearSlot.WEAPON,270,
            RelicStats(attack=4,heal=3)),
        RoadGear("ember_blade","紅蓮の刃",RoadGearSlot.WEAPON,340,
            RelicStats(attack=9)),
        RoadGear(STARTER_ARMOR,"旅人の上着",RoadGearSlot.ARMOR,0,RelicStats()),
        RoadGear("steel_guard","鋼鉄の鎧",RoadGearSlot.ARMOR,100,
            RelicStats(defense=4)),
        RoadGear("storm_coat","蒼雷の外套",RoadGearSlot.ARMOR,230,
            RelicStats(defense=3,critical=4)),
        RoadGear("moon_cloak","月光のローブ",RoadGearSlot.ARMOR,270,
            RelicStats(defense=3,heal=3)),
        RoadGear("ember_armor","紅蓮の鎧",RoadGearSlot.ARMOR,340,
            RelicStats(defense=5))
    )
    val sets=listOf(
        RoadGearSet("steel_blade","steel_guard",listOf(
            RoadGearSkill("鉄壁の構え","被ダメージをさらに軽減",RelicStats(defense=3)),
            RoadGearSkill("鋼の連撃","通常攻撃を強化",RelicStats(attack=3))
        )),
        RoadGearSet("storm_saber","storm_coat",listOf(
            RoadGearSkill("雷光加速","通常攻撃で技の再使用を短縮",RelicStats(charge=1)),
            RoadGearSkill("雷刃の極意","会心率を強化",RelicStats(critical=7))
        )),
        RoadGearSet("moon_staff","moon_cloak",listOf(
            RoadGearSkill("生命循環","敵を倒した際のHP回復量を増加",RelicStats(heal=8)),
            RoadGearSkill("月の加護","被ダメージを軽減",RelicStats(defense=3))
        )),
        RoadGearSet("ember_blade","ember_armor",listOf(
            RoadGearSkill("紅蓮追撃","通常攻撃を強化",RelicStats(attack=7)),
            RoadGearSkill("不屈の炎","被ダメージを軽減",RelicStats(defense=3))
        )),
        RoadGearSet("storm_saber","steel_guard",listOf(
            RoadGearSkill("雷鋼の連鎖","通常攻撃を強化",RelicStats(attack=2)),
            RoadGearSkill("剣速向上","技の再使用を短縮",RelicStats(charge=1))
        ))
    )

    fun spec(id:String):RoadGear?=catalog.firstOrNull{it.id==id}

    /** Reject spoofed ownership, unknown IDs and mismatched slots. */
    fun worn(state:QuestState,slot:RoadGearSlot):RoadGear? {
        val id=if(slot==RoadGearSlot.WEAPON)state.roadWeapon else state.roadArmor
        return spec(id)?.takeIf { it.slot==slot && id in state.roadGearOwned }
    }

    fun activeSkills(state:QuestState):List<RoadGearSkill> {
        val weapon=worn(state,RoadGearSlot.WEAPON)?.id ?: return emptyList()
        val armor=worn(state,RoadGearSlot.ARMOR)?.id ?: return emptyList()
        return sets.firstOrNull{it.weapon==weapon && it.armor==armor}
            ?.skills.orEmpty()
    }

    fun stats(state:QuestState):RelicStats {
        val parts=listOfNotNull(
            worn(state,RoadGearSlot.WEAPON)?.bonus,
            worn(state,RoadGearSlot.ARMOR)?.bonus
        )+activeSkills(state).map{it.bonus}
        return RelicStats(
            attack=parts.sumOf{it.attack},defense=parts.sumOf{it.defense},
            critical=parts.sumOf{it.critical},heal=parts.sumOf{it.heal},
            charge=parts.sumOf{it.charge}
        )
    }

    fun buy(state:QuestState,id:String):QuestState {
        if(RelicRules.isBattle(state))return state
        val gear=spec(id) ?: return state
        if(id in state.roadGearOwned)return state
        val gold=state.tapRun.gold
        if(gold<gear.price)return state.copy(tapRun=state.tapRun.copy(
            message="${gear.name}の購入には${gear.price}G必要"))
        return state.copy(roadGearOwned=state.roadGearOwned+id,
            tapRun=state.tapRun.copy(gold=gold-gear.price,
                message="${gear.name}を購入！ 装備画面から装着しよう"))
    }

    fun equip(state:QuestState,id:String):QuestState {
        if(RelicRules.isBattle(state))return state
        val gear=spec(id) ?: return state
        if(id !in state.roadGearOwned)return state
        val updated=if(gear.slot==RoadGearSlot.WEAPON)
            state.copy(roadWeapon=id) else state.copy(roadArmor=id)
        val activated=activeSkills(updated)
        return updated.copy(tapRun=updated.tapRun.copy(message=
            gear.name+"を装備"+
                (if(activated.isEmpty())"" else " / セットスキル："+
                    activated.joinToString("・"){it.name})))
    }
}
