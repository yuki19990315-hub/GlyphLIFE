package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.MonsterBodyType
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import java.util.UUID
import kotlin.random.Random

enum class TapPhase { WALK, FIGHT, BRANCH, BOSS }

data class TapRun(
    val floor:Int=1, val steps:Int=0, val phase:TapPhase=TapPhase.WALK,
    val enemy:WildMonster?=null, val enemyHp:Int=0,
    val combo:Int=0, val bossCountdown:Int=3,
    /** Wall-clock combat timing in milliseconds, independent of tapping. */
    val attackTimerMs:Int=2800,
    val skillCooldowns:Map<String,Int> = emptyMap(),
    val guardHits:Int=0,
    val venomCharges:Int=0,
    val bossTarget:Int=-1, val lane:Int=0, val weakpointTaps:Int=0,
    val bestFloor:Int=1, val deaths:Int=0, val distance:Int=0, val gold:Int=0,
    val message:String="画面をタップして冒険を始めよう！"
)

/** One-button dungeon: taps deal damage, periodic enemy attacks use a separate clock. */
object TapAdventure {
    const val BOSS_INTERVAL=5
    const val BRANCH_INTERVAL=3
    const val STEPS_PER_FLOOR=7
    const val MAX_FLOOR=999
    const val NORMAL_ATTACK_INTERVAL_MS=2700
    const val BOSS_ATTACK_INTERVAL_MS=3800
    const val ENRAGED_ATTACK_INTERVAL_MS=2600
    private val mobs=listOf(
        Triple("モコ","草",MonsterBodyType.BEAST),
        Triple("フレア","火",MonsterBodyType.DRAGON),
        Triple("アクア","水",MonsterBodyType.AQUATIC),
        Triple("ギア","雷",MonsterBodyType.MECHA),
        Triple("ルーン","闇",MonsterBodyType.SPIRIT),
        Triple("ライゼル","雷",MonsterBodyType.BEAST),
        Triple("鉱晶ゴーレム","水",MonsterBodyType.GOLEM),
        Triple("影翼コウモリ","闇",MonsterBodyType.AVIAN),
        Triple("深淵の魔眼","闇",MonsterBodyType.SPIRIT),
        Triple("翠刃の虫騎士","草",MonsterBodyType.INSECTOID)
    )
    private val bosses=listOf(
        Triple("月蝕の竜","闇",MonsterBodyType.DRAGON),
        Triple("古代の歯車王","雷",MonsterBodyType.MECHA),
        Triple("星光の守護者","光",MonsterBodyType.SPIRIT)
    )

    fun tap(state:QuestState,random:Random=Random.Default):QuestState = when(state.tapRun.phase) {
        TapPhase.WALK -> walk(state,random)
        TapPhase.FIGHT,TapPhase.BOSS -> attack(state,random)
        TapPhase.BRANCH -> state.copy(tapRun=state.tapRun.copy(message="分岐先を選ぼう"))
    }

    private fun walk(state:QuestState,rng:Random):QuestState {
        val run=state.tapRun
        val step=run.steps+1
        val progress=state.copy(tapRun=run.copy(steps=step,distance=run.distance+1))
        if(run.floor%BOSS_INTERVAL==0 && step>=STEPS_PER_FLOOR)
            return encounter(progress,bosses[rng.nextInt(bosses.size)],true,rng)
        if(step>=2 && rng.nextInt(100)<27) {
            val aiKnown=state.catalog.filter { it.lineage=="ai-summoned" }
            val picked=if(aiKnown.isNotEmpty() && rng.nextInt(100)<22)
                aiKnown[rng.nextInt(aiKnown.size)] else null
            val candidate=if(picked!=null)
                Triple(picked.species,picked.element,MonsterDesign.bodyType(picked))
                else mobs[rng.nextInt(mobs.size)]
            return encounter(progress,candidate,false,rng,
                artSourceId=picked?.artSourceId?.ifBlank { picked.id }.orEmpty())
        }
        if(step>=STEPS_PER_FLOOR)return finishFloor(progress,rng)
        return progress.copy(tapRun=progress.tapRun.copy(
            message="探索中… "+run.floor+"F / "+step+"歩　さらにタップで前進"))
    }

    private fun encounter(
        state:QuestState,form:Triple<String,String,MonsterBodyType>,
        boss:Boolean,rng:Random,artSourceId:String=""
    ):QuestState {
        val run=state.tapRun
        val stage=if(boss)(run.floor/BOSS_INTERVAL).coerceIn(2,3)
                  else (1+run.floor/12).coerceIn(1,3)
        val hp=if(boss)125+run.floor*16 else 19+run.floor*5+rng.nextInt(12)
        val monster=WildMonster(
            id=UUID.randomUUID().toString(),species=form.first,element=form.second,
            bodyType=form.third.name,maxHp=hp,
            attack=if(boss)15+run.floor*2 else 3+run.floor/2,
            stage=stage,isBoss=boss,artSourceId=artSourceId,
            description=if(boss)"予告攻撃を左右に避け、弱点が見えたら連打しよう！" else ""
        )
        return state.copy(
            catalog=addCatalog(state.catalog,monster),
            tapRun=run.copy(
                enemy=monster,enemyHp=hp,combo=0,lane=0,
                phase=if(boss)TapPhase.BOSS else TapPhase.FIGHT,
                bossCountdown=3,
                attackTimerMs=if(boss)BOSS_ATTACK_INTERVAL_MS else NORMAL_ATTACK_INTERVAL_MS,
                bossTarget=if(rng.nextBoolean())1 else -1,
                weakpointTaps=0,
                message=if(boss)"★ BOSS「"+monster.species+"」！ 大技の予告を見て回避"
                        else monster.species+"が出現！ タップ連打で攻撃！"
            )
        )
    }

    private fun attack(state:QuestState,rng:Random):QuestState {
        val run=state.tapRun
        val foe=run.enemy ?: return state.copy(tapRun=run.copy(phase=TapPhase.WALK))
        val support=state.partyIds.mapNotNull { id -> state.captures.find { it.id==id } }
            .sumOf { it.attack.coerceIn(0,80)/12 }.coerceAtMost(15)
        val critical=rng.nextInt(100)<12
        val weak=run.phase==TapPhase.BOSS && run.weakpointTaps>0
        val amount=(6+state.companion.level.coerceIn(1,50)/3+
            state.attackUpgrade*2+support+rng.nextInt(5)) *
            (if(critical)2 else 1) * (if(weak)3 else 1)
        val hp=(run.enemyHp-amount).coerceAtLeast(0)
        val hit=run.copy(enemyHp=hp,combo=run.combo+1,
            weakpointTaps=(run.weakpointTaps-1).coerceAtLeast(0),
            message=(if(critical)"会心！ " else "")+amount+"ダメージ / "+(run.combo+1)+"HIT")
        if(hp==0)return win(state.copy(tapRun=hit),foe,rng)
        // Attacking has NO enemy counterattack. The enemy moves on its own
        // periodic clock, even if the player does not touch the screen.
        return state.copy(tapRun=hit)
    }

    /**
     * Called by the exploration screen's running timer; not by button taps.
     * The UI pauses this clock while outside the Explore tab.
     * Large elapsed intervals are clamped to avoid catch-up damage after sleep.
     */
    fun tick(state:QuestState,elapsedMs:Int, rng:Random=Random.Default):QuestState {
        val run=state.tapRun
        if(run.phase !in listOf(TapPhase.FIGHT,TapPhase.BOSS) || run.enemy==null ||
            elapsedMs<=0) return state
        val remaining=run.attackTimerMs-elapsedMs.coerceIn(1,250)
        if(remaining>0)return state.copy(tapRun=run.copy(attackTimerMs=remaining))
        val foe=run.enemy
        if(run.phase==TapPhase.FIGHT) {
            val harm=(foe.attack+rng.nextInt(3)-state.defenseUpgrade).coerceAtLeast(1)
            val hp=(state.playerHp-harm).coerceAtLeast(0)
            if(hp==0)return death(state.copy(playerHp=0))
            return state.copy(playerHp=hp,tapRun=run.copy(
                attackTimerMs=NORMAL_ATTACK_INTERVAL_MS,
                message="敵が時間経過で攻撃！ HP-"+harm+"。連打で反撃しよう"))
        }
        val avoided=run.lane!=0 && run.lane!=run.bossTarget
        val enraged=run.enemyHp<=foe.maxHp/2
        val harm=if(avoided)0 else (foe.attack+(if(enraged)11 else 0)+
            rng.nextInt(6)-state.defenseUpgrade).coerceAtLeast(1)
        val hp=(state.playerHp-harm).coerceAtLeast(0)
        if(hp==0)return death(state.copy(playerHp=0))
        return state.copy(playerHp=hp,tapRun=run.copy(
            attackTimerMs=if(enraged)ENRAGED_ATTACK_INTERVAL_MS
                else BOSS_ATTACK_INTERVAL_MS,
            bossTarget=if(rng.nextBoolean())1 else -1,
            bossCountdown=if(enraged)2 else 3,
            lane=0,weakpointTaps=if(avoided)3 else 0,
            message=if(avoided)"ジャスト回避！ 弱点露出、次の3タップが3倍！"
                else "ボスの大技！ HP-"+harm+"。次は逆方向へ回避"))
    }

    fun dodge(state:QuestState,side:Int):QuestState {
        val r=state.tapRun
        if(r.phase!=TapPhase.BOSS || side !in listOf(-1,1))return state
        return state.copy(tapRun=r.copy(lane=side,
            message=(if(side<0)"左" else "右")+"へ移動！ 大技が来る方向に注意"))
    }

    fun capture(state:QuestState,rng:Random=Random.Default):QuestState {
        val r=state.tapRun
        val foe=r.enemy ?: return state
        if(r.phase!=TapPhase.FIGHT)return state
        if(state.captureOrbs<=0)return state.copy(tapRun=r.copy(message="捕獲玉がない！"))
        if(state.captures.size>=100)return state.copy(tapRun=r.copy(message="仲間が100匹で満杯"))
        val chance=(18+(foe.maxHp-r.enemyHp)*65/foe.maxHp).coerceIn(18,85)
        val next=state.copy(captureOrbs=state.captureOrbs-1)
        if(rng.nextInt(100)<chance) {
            val member=foe.copy(isBoss=false)
            return next.copy(
                captures=next.captures+member,
                partyIds=if(next.partyIds.size<3)next.partyIds+member.id else next.partyIds,
                tapRun=r.copy(enemy=null,enemyHp=0,phase=TapPhase.WALK,combo=0,
                    message="捕獲成功！ "+foe.species+"が仲間になった")
            )
        }
        val harm=(foe.attack/2).coerceAtLeast(1)
        val after=(state.playerHp-harm).coerceAtLeast(0)
        return if(after==0)death(next.copy(playerHp=0))
        else next.copy(playerHp=after,tapRun=r.copy(
            message="捕獲失敗（成功率"+chance+"%）。HP-"+harm))
    }

    fun heal(state:QuestState):QuestState {
        if(state.potions<=0 || state.playerHp>=100)return state
        return state.copy(potions=state.potions-1,
            playerHp=(state.playerHp+40).coerceAtMost(100),
            tapRun=state.tapRun.copy(message="回復薬でHP+40"))
    }

    fun branch(state:QuestState,route:Int,rng:Random=Random.Default):QuestState {
        val r=state.tapRun
        if(r.phase!=TapPhase.BRANCH || route !in 0..2)return state
        val floor=(r.floor+1).coerceAtMost(MAX_FLOOR)
        val next=r.copy(floor=floor,bestFloor=maxOf(r.bestFloor,floor),
            phase=TapPhase.WALK,steps=0)
        return when(route) {
            0 -> state.copy(playerHp=(state.playerHp+28).coerceAtMost(100),
                tapRun=next.copy(message="精霊の泉を発見！ HP+28"))
            1 -> {
                val crystal=rng.nextInt(100)<40
                state.copy(
                    summonStones=(state.summonStones+(if(crystal)1 else 0)).coerceAtMost(999),
                    captureOrbs=(state.captureOrbs+1).coerceAtMost(99),
                    tapRun=next.copy(gold=r.gold+25+r.floor*4,
                        message=if(crystal)"宝箱！ 召喚結晶＋捕獲玉！" else "宝箱！ 捕獲玉とコイン！")
                )
            }
            else -> if(rng.nextInt(100)<70)
                encounter(state.copy(tapRun=next),mobs[rng.nextInt(mobs.size)],false,rng)
            else state.copy(summonStones=(state.summonStones+1).coerceAtMost(999),
                tapRun=next.copy(message="危険な小道で召喚結晶を入手！"))
        }
    }

    private fun finishFloor(state:QuestState,rng:Random):QuestState {
        val r=state.tapRun
        val drop=rng.nextInt(100)<5
        val updated=state.copy(summonStones=(state.summonStones+
            (if(drop)1 else 0)).coerceAtMost(999))
        if(r.floor%BRANCH_INTERVAL==0)return updated.copy(
            tapRun=r.copy(phase=TapPhase.BRANCH,
                message="フロア分岐！ 休息・宝箱・危険な道から選択"))
        val floor=(r.floor+1).coerceAtMost(MAX_FLOOR)
        return updated.copy(tapRun=r.copy(
            floor=floor,bestFloor=maxOf(r.bestFloor,floor),steps=0,
            message=if(drop)"低確率ドロップ！ 召喚結晶+1、"+floor+"Fへ"
                    else "フロア突破！ "+floor+"Fへ"))
    }

    private fun win(state:QuestState,foe:WildMonster,rng:Random):QuestState {
        val r=state.tapRun
        val boss=r.phase==TapPhase.BOSS
        val drop=if(boss)2 else if(rng.nextInt(100)<8)1 else 0
        val next=state.copy(
            summonStones=(state.summonStones+drop).coerceAtMost(999),
            defeated=(state.defeated+1).coerceAtMost(999999),
            earnedXp=(state.earnedXp+(if(boss)30 else 4)).coerceAtMost(100),
            earnedHappiness=(state.earnedHappiness+1).coerceAtMost(20)
        )
        if(boss) {
            val floor=(r.floor+1).coerceAtMost(MAX_FLOOR)
            return GrowthRules.rewardBattle(next.copy(tapRun=r.copy(
                floor=floor,bestFloor=maxOf(r.bestFloor,floor),steps=0,
                gold=(r.gold+50+r.floor*10).coerceAtMost(999999),
                phase=TapPhase.WALK,enemy=null,enemyHp=0,combo=0,
                message="BOSS撃破！ 召喚結晶+2。"+floor+"Fへ！"
            )),boss=true,floor=r.floor)
        }
        return GrowthRules.rewardBattle(next.copy(tapRun=r.copy(
            phase=TapPhase.WALK,enemy=null,enemyHp=0,combo=0,
            gold=(r.gold+10+r.floor*2).coerceAtMost(999999),
            message="勝利！ "+foe.species+(if(drop>0)" / 召喚結晶+1！" else "")
        )),boss=false,floor=r.floor)
    }

    private fun death(state:QuestState):QuestState {
        val r=state.tapRun
        return state.copy(playerHp=100,potions=maxOf(state.potions,1),
            tapRun=TapRun(bestFloor=r.bestFloor,deaths=r.deaths+1,
                distance=r.distance,gold=r.gold,
                message="戦闘不能！ 探索は1Fから再開。仲間・図鑑・アイテムは保持！"))
    }

    fun selectParty(state:QuestState,id:String):QuestState {
        if(state.inBattle || state.captures.none { it.id==id })return state
        val ids=state.partyIds.filter { it!=id }
        return state.copy(partyIds=if(id in state.partyIds)ids
            else (ids+id).takeLast(3))
    }

    fun offlineSummon(state:QuestState,rng:Random=Random.Default):QuestState {
        if(state.inBattle || state.summonStones<1 || state.captures.size>=100)return state
        val bodies=MonsterBodyType.entries
        val body=bodies[rng.nextInt(bodies.size)]
        val elements=listOf("火","水","草","雷","光","闇")
        val element=elements[rng.nextInt(elements.size)]
        val prototype=MonsterPrototypeFactory.preview(
            "召喚"+body.name.take(3),body,element,
            variant=rng.nextInt(64))
        return registerSummon(state,MonsterPrototypeFactory.summon(prototype,1))
    }

    fun registerSummon(
        state:QuestState,monster:WildMonster,generatedByAi:Boolean=false
    ):QuestState {
        if(state.inBattle || state.summonStones<1 || state.captures.size>=100)return state
        require(monster.species.isNotBlank() && monster.maxHp in 1..100_000)
        val owned=monster.copy(id=UUID.randomUUID().toString(),stage=1,isBoss=false,
            lineage=if(generatedByAi)"ai-summoned" else monster.lineage)
        return state.copy(
            summonStones=state.summonStones-1,
            captures=state.captures+owned,
            catalog=addCatalog(state.catalog,owned),
            partyIds=if(state.partyIds.size<3)state.partyIds+owned.id else state.partyIds,
            tapRun=state.tapRun.copy(
                message="新しい仲間「"+owned.species+"」を召喚！ 図鑑にも登録した"))
    }

    private fun addCatalog(old:List<WildMonster>,wild:WildMonster):List<WildMonster> =
        if(old.any { it.species==wild.species && it.element==wild.element })old
        else (old+wild.copy(isBoss=false)).takeLast(150)
}
