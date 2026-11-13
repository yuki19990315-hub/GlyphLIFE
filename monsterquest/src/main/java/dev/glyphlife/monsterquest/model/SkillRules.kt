package dev.glyphlife.monsterquest.model

/** Every member has one deterministic ability selected from its species and
 * body type. It requires deliberate button presses and has its OWN cooldown
 * measured in subsequent normal attacks (not in wall-clock time).
 */
data class UniqueSkill(
    val id:String,
    val name:String,
    val detail:String,
    val cooldown:Int
)

object SkillRules {
    private val skills=listOf(
        UniqueSkill("moonlight","月光の祈り","HPを回復",8),
        UniqueSkill("thunderfang","迅雷の双牙","連撃＋敵攻撃を遅延",7),
        UniqueSkill("claw","双牙連撃","強力な連続斬撃",6),
        UniqueSkill("flame","竜炎ブレス","高威力の炎攻撃",9),
        UniqueSkill("golem","結晶障壁","次の敵の攻撃を70%軽減",9),
        UniqueSkill("heal","精霊の癒し","HPを回復",8),
        UniqueSkill("shadow","影吸収","攻撃しながらHPを吸収",8),
        UniqueSkill("overload","雷機砲","攻撃＋次の敵攻撃を遅延",9),
        UniqueSkill("tide","渦潮弾","攻撃＋敵攻撃を遅延",7),
        UniqueSkill("gale","疾風斬","素早い大ダメージ",6),
        UniqueSkill("venom","猛毒針","2回の追加毒ダメージ",9),
        UniqueSkill("ooze","粘液の鎧","次の攻撃を70%軽減",7),
        UniqueSkill("focus","星光の矢","精密な必殺攻撃",7)
    )
    private fun skill(id:String):UniqueSkill=skills.first {it.id==id}
    fun extraFor(monster:WildMonster):UniqueSkill {
        val native=forMonster(monster).id
        val id=monster.extraSkillId.takeIf {
            candidate->skills.any{it.id==candidate} && candidate!=native
        } ?: TalentRules.extraSkill(monster.id,native)
        return skill(id)
    }
    fun cooldownKey(ownerId:String,bonus:Boolean):String =
        if(bonus) ownerId+":extra" else ownerId
    fun forMonster(monster:WildMonster):UniqueSkill {
        val body=monster.bodyType.uppercase()
        return when {
            monster.species.contains("スライム") -> skill("ooze")
            monster.species.contains("ライゼル") -> skill("thunderfang")
            monster.species.contains("ルナ") &&
                monster.element=="光" -> skill("moonlight")
            body=="GOLEM" -> skill("golem")
            body=="MECHA" -> skill("overload")
            body=="INSECTOID" -> skill("venom")
            body=="AVIAN" -> skill("gale")
            body=="AQUATIC" -> skill("tide")
            body=="DRAGON" -> skill("flame")
            body=="BEAST" -> skill("claw")
            body=="SPIRIT" && monster.element=="闇" -> skill("shadow")
            body=="SPIRIT" || monster.element=="光" -> skill("heal")
            monster.element=="火" -> skill("flame")
            monster.element=="雷" -> skill("thunderfang")
            monster.element=="水" -> skill("tide")
            monster.element=="草" -> skill("venom")
            monster.element=="闇" -> skill("shadow")
            else -> skill("focus")
        }
    }

    fun party(state:QuestState):List<WildMonster> {
        val hero=WildMonster(
            id=state.companion.id,species=state.companion.name,
            element=state.companion.element,maxHp=100,attack=10+
                state.companion.level,level=state.companion.level,
            bodyType=if(state.companion.element=="光")"SPIRIT" else "",
            stage=if(state.companion.level>=16)3 else if(state.companion.level>=7)2 else 1,
            extraSkillId=state.companion.extraSkillId,
            statSlots=state.companion.statSlots
        )
        return listOf(hero)+state.partyIds.distinct().mapNotNull { id ->
            state.captures.firstOrNull{it.id==id}
        }
    }

    fun countdown(current:Map<String,Int>,bonus:Int):Map<String,Int> {
        val delta=1+bonus.coerceIn(0,3)
        return current.mapValues { (_,left)->(left-delta).coerceAtLeast(0) }
            .filterValues{it>0}
    }

    private data class Effect(
        val damage:Int=0,val healing:Int=0,
        val shield:Int=0,val poison:Int=0,val delay:Int=0
    )
    private fun effect(
        skill:UniqueSkill,monster:WildMonster,bossStats:RelicStats
    ):Effect {
        val lv=monster.level.coerceIn(1,50)
        val stage=monster.stage.coerceIn(1,3)
        val bonus=bossStats.attack/3+
            TalentRules.ownStats(monster.statSlots).attack/2
        fun dmg(v:Int)=v+lv*2+stage*3+bonus
        return when(skill.id) {
            "moonlight","heal" -> Effect(healing=20+lv/2)
            "thunderfang" -> Effect(damage=dmg(19),delay=700)
            "claw" -> Effect(damage=dmg(16))
            "flame" -> Effect(damage=dmg(25))
            "golem","ooze" -> Effect(shield=1)
            "shadow" -> Effect(damage=dmg(15),healing=8+lv/5)
            "overload" -> Effect(damage=dmg(23),delay=1000)
            "tide" -> Effect(damage=dmg(15),delay=1300)
            "gale" -> Effect(damage=dmg(19))
            "venom" -> Effect(damage=dmg(9),poison=2)
            else -> Effect(damage=dmg(17))
        }
    }

    fun useExplore(state:QuestState,ownerId:String,bonus:Boolean=false):QuestState {
        val run=state.tapRun
        if(run.phase !in listOf(TapPhase.FIGHT,TapPhase.BOSS) ||
            run.enemy==null)return state
        val creature=party(state).firstOrNull{it.id==ownerId} ?: return state
        val key=cooldownKey(ownerId,bonus)
        if((run.skillCooldowns[key] ?: 0)>0)return state
        val skill=if(bonus)extraFor(creature) else forMonster(creature)
        val act=effect(skill,creature,RelicRules.stats(state))
        val hp=(run.enemyHp-act.damage).coerceAtLeast(0)
        return state.copy(
            playerHp=(state.playerHp+act.healing)
                .coerceAtMost(TalentRules.maxHp(state)),
            tapRun=run.copy(
                enemyHp=hp,
                guardHits=(run.guardHits+act.shield).coerceAtMost(1),
                venomCharges=(run.venomCharges+act.poison).coerceAtMost(2),
                attackTimerMs=(run.attackTimerMs+act.delay).coerceAtMost(5500),
                skillCooldowns=run.skillCooldowns+(key to skill.cooldown),
                message=creature.species+"の【"+skill.name+"】！"+
                    (if(act.damage>0)" "+act.damage+"ダメージ" else "")+
                    (if(act.healing>0)" HP+"+act.healing else "")+
                    (if(act.shield>0)" 次の敵攻撃を軽減" else "")+
                    (if(act.poison>0)" 毒を付与" else "")
            )
        )
    }

    fun useChallenge(state:QuestState,ownerId:String,bonus:Boolean=false):QuestState {
        val run=state.challenge
        if(run.mode==ChallengeMode.NONE || run.opponent==null)return state
        val creature=party(state).firstOrNull{it.id==ownerId} ?: return state
        val key=cooldownKey(ownerId,bonus)
        if((run.skillCooldowns[key] ?: 0)>0)return state
        val skill=if(bonus)extraFor(creature) else forMonster(creature)
        val act=effect(skill,creature,RelicRules.stats(state))
        return state.copy(challenge=run.copy(
            playerHp=(run.playerHp+act.healing)
                .coerceAtMost(TalentRules.maxHp(state)),
            opponentHp=(run.opponentHp-act.damage).coerceAtLeast(0),
            guardHits=(run.guardHits+act.shield).coerceAtMost(1),
            venomCharges=(run.venomCharges+act.poison).coerceAtMost(2),
            attackTimerMs=(run.attackTimerMs+act.delay).coerceAtMost(5500),
            skillCooldowns=run.skillCooldowns+(key to skill.cooldown),
            message=creature.species+"の【"+skill.name+"】！"+
                (if(act.damage>0)" "+act.damage+"ダメージ" else "")+
                (if(act.healing>0)" HP+"+act.healing else "")+
                (if(act.shield>0)" 次の敵攻撃を軽減" else "")+
                (if(act.poison>0)" 毒を付与" else "")
        ))
    }
}
