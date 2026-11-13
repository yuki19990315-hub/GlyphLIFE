package dev.glyphlife.monsterquest.model

import kotlin.random.Random

/** One permanent random roll belonging to a single creature, not its species. */
data class StatSlot(val kind:String,val value:Int,val rarity:Int)

data class TalentStats(
    val attack:Int=0,val defense:Int=0,val hp:Int=0,val critical:Int=0
)

/** Stable per-UUID generation: reloading the same creature cannot reroll it.
 * A different captured individual has a different UUID and therefore different rolls.
 * This also safely migrates previously owned creatures at any level.
 */
object TalentRules {
    val levels=listOf(5,15,30)
    val skillPool=listOf("moonlight","thunderfang","claw","flame","golem",
        "heal","shadow","overload","tide","gale","venom","ooze","focus")
    val statKinds=listOf("attack","defense","hp","critical")
    private fun rng(id:String,salt:Int):Random =
        Random(id.hashCode() xor (salt*0x45d9f3b))

    fun extraSkill(id:String,innateId:String):String {
        val available=skillPool.filter{it!=innateId}
        return available[rng(id,9001).nextInt(available.size)]
    }

    private fun roll(id:String,index:Int):StatSlot {
        val r=rng(id,100+index)
        // Draw WITH replacement: attack/attack/attack is a valid perfect roll.
        // Never exclude an attribute chosen by an earlier slot.
        val kind=statKinds[r.nextInt(statKinds.size)]
        val percent=r.nextInt(100)
        val rarity=if(percent<10)3 else if(percent<37)2 else 1
        val base=when(kind) {
            "attack"->2+r.nextInt(3) // 2..4
            "defense"->1+r.nextInt(2)
            "hp"->4+r.nextInt(5)
            else->1+r.nextInt(3)
        }
        return StatSlot(kind,base*rarity,rarity)
    }

    private fun unlocked(id:String,level:Int,previous:List<StatSlot>):List<StatSlot> {
        val size=levels.count{level>=it}
        return (0 until size).map { index ->
            val saved=previous.getOrNull(index)
            if(saved!=null && saved.kind in statKinds && saved.value in 1..60 &&
                saved.rarity in 1..3)saved else roll(id,index)
        }
    }

    fun awaken(m:WildMonster):WildMonster {
        val innate=SkillRules.forMonster(m).id
        return m.copy(
            extraSkillId=m.extraSkillId.takeIf {
                it in skillPool && it!=innate
            } ?: extraSkill(m.id,innate),
            statSlots=unlocked(m.id,m.level,m.statSlots)
        )
    }

    fun awaken(c:Companion):Companion {
        val sample=WildMonster(c.id,c.name,c.element,100,10+c.level,
            level=c.level,bodyType=if(c.element=="光")"SPIRIT" else "")
        val innate=SkillRules.forMonster(sample).id
        return c.copy(
            extraSkillId=c.extraSkillId.takeIf {
                it in skillPool && it!=innate
            } ?: extraSkill(c.id,innate),
            statSlots=unlocked(c.id,c.level,c.statSlots)
        )
    }

    fun ownStats(slots:List<StatSlot>):TalentStats =
        TalentStats(
            attack=slots.filter{it.kind=="attack"}.sumOf{it.value},
            defense=slots.filter{it.kind=="defense"}.sumOf{it.value},
            hp=slots.filter{it.kind=="hp"}.sumOf{it.value},
            critical=slots.filter{it.kind=="critical"}.sumOf{it.value}
        )

    fun teamStats(s:QuestState):TalentStats {
        val own=ownStats(s.companion.statSlots)
        val allies=s.partyIds.distinct().mapNotNull {id->
            s.captures.firstOrNull{it.id==id}
        }.map{ownStats(it.statSlots)}
        return TalentStats(
            attack=(own.attack+allies.sumOf{it.attack}).coerceAtMost(60),
            defense=(own.defense+allies.sumOf{it.defense}).coerceAtMost(36),
            hp=(own.hp+allies.sumOf{it.hp}).coerceAtMost(100),
            critical=(own.critical+allies.sumOf{it.critical}).coerceAtMost(35)
        )
    }
    fun maxHp(s:QuestState):Int=100+teamStats(s).hp

    fun label(slot:StatSlot):String =
        (when(slot.kind) {
            "attack"->"攻撃"
            "defense"->"防御"
            "hp"->"最大HP"
            else->"会心率"
        })+" +"+slot.value+(if(slot.kind=="critical")"%" else "")+
            " "+(when(slot.rarity) {3->"★★★";2->"★★";else->"★"})
}
