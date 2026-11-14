package dev.glyphlife.monsterquest.model

import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

/** Feet are world coordinates (y grows down), one tile = one unit. */
data class SideRun(
    val legacy:Boolean=false,
    val room:Int=0,
    val x:Float=2f,
    val y:Float=10f,
    val vy:Float=0f,
    val grounded:Boolean=true,
    val usedAirJump:Boolean=false,
    val facing:Int=1,
    val acquired:Set<String> = emptySet(),
    val cleared:Set<Int> = emptySet(),
    val enemyHp:Int=0,
    val enemyTimerMs:Int=2200,
    val attackCooldownMs:Int=0,
    val dashCooldownMs:Int=0,
    val hurtCooldownMs:Int=0,
    val guardHits:Int=0,
    val venomCharges:Int=0,
    val skillCooldowns:Map<String,Int> = emptyMap(),
    val checkpoint:Int=0,
    val deaths:Int=0,
    val elapsedMs:Int=0,
    val message:String="左右移動・ジャンプ・攻撃で遺跡を探索！"
)

data class SidePlatform(val start:Float,val end:Float,val y:Float)
data class SideRoom(
    val name:String,
    val subtitle:String,
    val enemyName:String,
    val enemyElement:String,
    val enemyBody:String,
    val enemyX:Float,
    val platforms:List<SidePlatform>,
    val spikes:List<ClosedFloatingPointRange<Float>> = emptyList(),
    val boss:Boolean=false
)

/** Offline side-scrolling exploration with a real jump arc, collisions,
 * a return route, permanent mobility unlocks and a loopable boss. */
object MetroidRules {
    const val ROOM_WIDTH=24f
    const val VIEW_WIDTH=14f
    const val FLOOR=10f
    const val ROOM_COUNT=6
    const val JUMP_V=-7.7f
    const val GRAVITY=18f
    private const val SPEED=4.1f
    val rooms=listOf(
        SideRoom("古びた地下道","遺跡の入口","スライム","水","SLIME",13f,
            listOf(SidePlatform(6f,10f,8.5f))),
        SideRoom("翠影の森","上段には未踏の横道","森の夜蝙蝠","闇","AVIAN",14f,
            listOf(SidePlatform(3.3f,6.8f,7.0f),
                SidePlatform(11f,15f,8.6f)),
            listOf(18f..19f)),
        SideRoom("水没した工房","二段ジャンプの遺物が眠る","鉱晶ゴーレム","水","GOLEM",13.5f,
            listOf(SidePlatform(5f,9f,8.4f),
                SidePlatform(15f,20f,8f)),
            listOf(10f..11f)),
        SideRoom("天空回廊","戻り探索で入る隠し道","風刃の翼獣","雷","AVIAN",13f,
            listOf(SidePlatform(7f,11f,8.2f)),
            listOf(14.5f..16f)),
        SideRoom("深淵の門","ダッシュを使いこなそう","影の甲冑騎士","闇","BIPED",15f,
            listOf(SidePlatform(5f,9f,8.4f),SidePlatform(14f,18f,7.8f)),
            listOf(11f..12f)),
        SideRoom("月蝕の王座","最深部の守護者","月蝕の竜","闇","DRAGON",17.3f,
            listOf(SidePlatform(8f,11f,8.1f)),boss=true)
    )

    fun enemy(room:Int):WildMonster {
        val index=room.coerceIn(0,rooms.lastIndex)
        val r=rooms[index]
        return WildMonster(
            id=UUID.nameUUIDFromBytes(("monsterquest-side-room-"+index)
                .toByteArray(Charsets.UTF_8)).toString(),
            species=r.enemyName,element=r.enemyElement,bodyType=r.enemyBody,
            maxHp=(50+index*25+(if(r.boss)140 else 0)),
            attack=5+index*2+(if(r.boss)8 else 0),
            isBoss=r.boss,level=1+index*4,stage=if(index>=4)2 else 1
        )
    }

    fun maxEnemyHp(room:Int):Int=enemy(room).maxHp

    fun inCombat(state:QuestState):Boolean = !state.side.legacy &&
        state.side.enemyHp>0 &&
        abs(state.side.x-rooms[state.side.room].enemyX)<3.3f

    fun isOccupied(state:QuestState):Boolean =
        ChallengeRules.active(state) ||
            (state.side.legacy &&
                state.tapRun.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS)) ||
            inCombat(state)

    /** New rooms respawn their original enemy; clearing a room is persistent. */
    private fun loadRoom(s:SideRun,to:Int,fromLeft:Boolean):SideRun {
        val pos=if(fromLeft)1.6f else ROOM_WIDTH-1.6f
        return s.copy(room=to,x=pos,y=FLOOR,vy=0f,grounded=true,
            usedAirJump=false,enemyHp=if(to in s.cleared)0 else maxEnemyHp(to),
            enemyTimerMs=2200,attackCooldownMs=0,skillCooldowns=emptyMap(),
            guardHits=0,venomCharges=0,
            message=rooms[to].name+" に入った")
    }

    fun normalized(state:QuestState):QuestState {
        val r=state.side
        if(r.enemyHp>0 || r.room in r.cleared)return state
        return state.copy(side=r.copy(enemyHp=maxEnemyHp(r.room)))
    }

    fun switchLegacy(state:QuestState,old:Boolean):QuestState {
        if(ChallengeRules.active(state))return state
        return state.copy(side=state.side.copy(legacy=old,
            message=if(old)"従来のタップ探索へ切り替え" else "横スクロール探索を開始！"))
    }

    fun jump(state:QuestState):QuestState {
        val r=state.side
        if(r.legacy || ChallengeRules.active(state))return state
        if(r.grounded)return state.copy(side=r.copy(
            grounded=false,vy=JUMP_V,usedAirJump=false))
        if("double" in r.acquired && !r.usedAirJump)
            return state.copy(side=r.copy(vy=JUMP_V,usedAirJump=true,
                message="二段ジャンプ！"))
        return state
    }

    fun dash(state:QuestState):QuestState {
        val r=state.side
        if(r.legacy || "dash" !in r.acquired || r.dashCooldownMs>0 ||
            ChallengeRules.active(state))return state
        return state.copy(side=r.copy(
            x=(r.x+r.facing*3.8f).coerceIn(0.6f,ROOM_WIDTH-0.6f),
            dashCooldownMs=1700,hurtCooldownMs=420,
            message="風走り！ ダッシュで障害を抜けた"))
    }

    /** Upper hidden exit: requires an unlocked double jump and reaching
     * the elevated tree platform. Route 1->3 returns to room 1. */
    fun interact(state:QuestState):QuestState {
        val r=state.side
        if(r.legacy || ChallengeRules.active(state))return state
        if(r.room==2 && abs(r.x-19f)<2f && "double" !in r.acquired) {
            return state.copy(side=r.copy(acquired=r.acquired+"double",
                message="【空蝉の翼】獲得！ 空中でもう一度ジャンプ可能"))
        }
        if(r.room==3 && abs(r.x-19f)<2f && "dash" !in r.acquired) {
            return state.copy(side=r.copy(acquired=r.acquired+"dash",
                message="【風走り】獲得！ ダッシュで飛距離を伸ばせる"))
        }
        if(r.room==1 && abs(r.x-5f)<2f && r.y<=8.3f) {
            if("double" !in r.acquired)return state.copy(side=r.copy(
                message="高い通路には二段ジャンプの能力が必要"))
            return state.copy(side=loadRoom(r,3,true).copy(
                checkpoint=3,message="隠し回廊へ！ 風走りを探そう"))
        }
        if(r.room==3 && r.x<=3.4f)
            return state.copy(side=loadRoom(r,1,true).copy(
                message="翠影の森へ戻った"))
        if(r.room==5 && 5 in r.cleared) {
            return state.copy(side=r.copy(
                cleared=emptySet(),enemyHp=maxEnemyHp(5),
                message="守護者が再び目覚めた。全エリアの敵が復活！"))
        }
        return state.copy(side=r.copy(message="近くの遺物や扉を調べよう"))
    }

    private fun destination(r:SideRun,dir:Int):Int?=when(r.room) {
        0 -> if(dir>0)1 else null
        1 -> if(dir>0)2 else 0
        2 -> if(dir<0)1 else if("dash" in r.acquired)4 else null
        3 -> if(dir<0 || dir>0)1 else null
        4 -> if(dir<0)2 else 5
        5 -> if(dir<0)4 else null
        else -> null
    }

    fun tick(state:QuestState,direction:Int,dtMs:Int):QuestState {
        val r=state.side
        if(r.legacy || ChallengeRules.active(state) || dtMs<=0)return state
        val step=dtMs.coerceIn(1,50)
        val sec=step/1000f
        val dir=direction.coerceIn(-1,1)
        var x=(r.x+dir*SPEED*sec).coerceIn(0.4f,ROOM_WIDTH-0.4f)
        val vy=(r.vy+GRAVITY*sec).coerceAtMost(16f)
        var y=r.y+vy*sec
        var nextVy=vy
        var onGround=false
        val platforms=rooms[r.room].platforms
        if(vy>=0f) {
            for(p in platforms) {
                if(r.y<=p.y+0.1f && y>=p.y &&
                    x>=p.start-0.15f && x<=p.end+0.15f) {
                    y=p.y;nextVy=0f;onGround=true;break
                }
            }
            if(y>=FLOOR) {y=FLOOR;nextVy=0f;onGround=true}
        }
        if(y<1.2f) {y=1.2f;nextVy=0f}
        val cooldown=(r.hurtCooldownMs-step).coerceAtLeast(0)
        var after=state.copy(side=r.copy(
            x=x,y=y,vy=nextVy,grounded=onGround,
            usedAirJump=if(onGround)false else r.usedAirJump,
            facing=if(dir==0)r.facing else dir,
            attackCooldownMs=(r.attackCooldownMs-step).coerceAtLeast(0),
            dashCooldownMs=(r.dashCooldownMs-step).coerceAtLeast(0),
            hurtCooldownMs=cooldown,enemyTimerMs=r.enemyTimerMs-step,
            elapsedMs=(r.elapsedMs+step)%100000
        ))
        val now=after.side
        if(dir>0 && x>=ROOM_WIDTH-0.7f ||
           dir<0 && x<=0.7f) {
            val target=destination(now,dir)
            if(target!=null) return after.copy(side=loadRoom(now,target,
                fromLeft=dir>0).copy(checkpoint=target))
            if(dir>0 && r.room==2 && "dash" !in r.acquired)
                return after.copy(side=now.copy(
                    message="封印の門：天空回廊で風走りを習得しよう"))
        }
        val spikes=rooms[now.room].spikes
        if(cooldown==0 && now.y>=FLOOR-0.15f &&
            spikes.any{now.x in it})
            after=damage(after,9,"トゲ床でダメージ！")
        val checked=after.side
        if(checked.enemyHp>0 && checked.enemyTimerMs<=0) {
            val foe=enemy(checked.room)
            val close=abs(checked.x-rooms[checked.room].enemyX)<2.7f &&
                checked.y>=7.3f
            after=after.copy(side=checked.copy(enemyTimerMs=if(foe.isBoss)1500 else 2200))
            if(close && after.side.hurtCooldownMs==0) {
                val raw=(foe.attack-state.defenseUpgrade-
                    RelicRules.stats(state).defense-
                    TalentRules.teamStats(state).defense).coerceAtLeast(1)
                val taken=if(checked.guardHits>0)(raw*3/10).coerceAtLeast(1)
                    else raw
                after=damage(after.copy(side=after.side.copy(
                    guardHits=(checked.guardHits-1).coerceAtLeast(0))),
                    taken,foe.species+"の反撃！ -"+taken)
            }
        }
        return after
    }

    private fun damage(state:QuestState,amount:Int,message:String):QuestState {
        val hp=state.playerHp-amount
        if(hp>0)return state.copy(playerHp=hp,
            side=state.side.copy(hurtCooldownMs=900,message=message))
        val r=state.side
        val respawn=loadRoom(r,r.checkpoint.coerceIn(0,rooms.lastIndex),true)
        return state.copy(
            playerHp=TalentRules.maxHp(state),
            side=respawn.copy(deaths=r.deaths+1,message=
                "力尽きた。休息地点に戻った！ 習得能力と仲間は維持"))
    }

    fun heal(state:QuestState):QuestState {
        if(state.side.legacy || state.potions<1 ||
            state.playerHp>=TalentRules.maxHp(state))return state
        return state.copy(potions=state.potions-1,
            playerHp=(state.playerHp+40).coerceAtMost(TalentRules.maxHp(state)),
            side=state.side.copy(message="回復薬でHPを40回復"))
    }

    fun attack(state:QuestState,rng:Random=Random.Default):QuestState {
        val r=state.side
        if(r.legacy || r.attackCooldownMs>0 ||
            ChallengeRules.active(state))return state
        val foe=enemy(r.room)
        val close=r.enemyHp>0 &&
            abs(r.x-rooms[r.room].enemyX)<=3.5f && r.y>=7f
        if(!close)return state.copy(side=r.copy(
            attackCooldownMs=220,message="攻撃が届かない！ 近づこう"))
        val relic=RelicRules.stats(state)
        val talent=TalentRules.teamStats(state)
        val support=state.partyIds.mapNotNull {id->
            state.captures.firstOrNull{it.id==id}
        }.sumOf{it.attack.coerceIn(0,120)/12}.coerceAtMost(28)
        val crit=rng.nextInt(100)<(12+relic.critical+talent.critical).coerceAtMost(80)
        val amount=(9+state.companion.level/3+
            state.attackUpgrade*2+support+relic.attack+talent.attack+
            rng.nextInt(5))*(if(crit)2 else 1)+
            (if(r.venomCharges>0)8 else 0)
        val remaining=(r.enemyHp-amount).coerceAtLeast(0)
        val updated=state.copy(side=r.copy(
            enemyHp=remaining,attackCooldownMs=220,
            venomCharges=(r.venomCharges-1).coerceAtLeast(0),
            skillCooldowns=SkillRules.countdown(r.skillCooldowns,relic.charge),
            message=(if(crit)"会心！ " else "")+amount+"ダメージ"))
        return if(remaining==0) win(updated,foe,rng) else updated
    }

    fun useSkill(state:QuestState,id:String,bonus:Boolean=false,
                 rng:Random=Random.Default):QuestState {
        val r=state.side
        if(r.legacy || r.enemyHp<=0 ||
            abs(r.x-rooms[r.room].enemyX)>5f ||
            ChallengeRules.active(state))return state
        val member=SkillRules.party(state).firstOrNull{it.id==id} ?: return state
        val key=SkillRules.cooldownKey(id,bonus)
        if((r.skillCooldowns[key]?:0)>0)return state
        val skill=if(bonus)SkillRules.extraFor(member) else SkillRules.forMonster(member)
        val effect=SkillRules.effect(skill,member,RelicRules.stats(state))
        val remaining=(r.enemyHp-effect.damage).coerceAtLeast(0)
        val updated=state.copy(
            playerHp=(state.playerHp+effect.healing).coerceAtMost(TalentRules.maxHp(state)),
            side=r.copy(enemyHp=remaining,
                guardHits=(r.guardHits+effect.shield).coerceAtMost(1),
                venomCharges=(r.venomCharges+effect.poison).coerceAtMost(2),
                enemyTimerMs=(r.enemyTimerMs+effect.delay).coerceAtMost(5500),
                skillCooldowns=r.skillCooldowns+(key to skill.cooldown),
                message=member.species+"の「"+skill.name+"」！"+
                    (if(effect.damage>0)" "+effect.damage+"ダメージ" else "")+
                    (if(effect.healing>0)" HP回復" else "")+
                    (if(effect.shield>0)" バリア展開" else "")
            )
        )
        return if(remaining==0)win(updated,enemy(r.room),rng)else updated
    }

    private fun win(state:QuestState,foe:WildMonster,rng:Random):QuestState {
        val r=state.side
        val gold=18+(r.room+1)*8+(if(foe.isBoss)100 else 0)
        val cleared=state.copy(
            defeated=(state.defeated+1).coerceAtMost(999999),
            tapRun=state.tapRun.copy(gold=
                (state.tapRun.gold+gold).coerceAtMost(999999)),
            side=r.copy(cleared=r.cleared+r.room,enemyHp=0,
                message=foe.species+"を撃破！ +"+gold+"G"+
                    (if(foe.isBoss)" 月蝕の王座を解放！" else ""))
        )
        val grown=GrowthRules.rewardBattle(cleared,foe.isBoss,
            (r.room+1)*5)
        val loot=if(foe.isBoss) RelicRules.reward(
            grown,foe,(r.room+1)*5,rng,challenge=false)
            else grown
        return RelicRules.afterVictory(loot,false)
    }

    fun capture(state:QuestState,rng:Random=Random.Default):QuestState {
        val r=state.side
        if(r.legacy || r.enemyHp<=0 || state.captureOrbs<1 ||
            state.captures.size>=100 || r.room in r.cleared ||
            ChallengeRules.active(state))return state
        val foe=enemy(r.room)
        if(foe.isBoss)return state.copy(side=r.copy(
            message="守護者は捕獲できない"))
        if(abs(r.x-rooms[r.room].enemyX)>3.5f)return state.copy(side=r.copy(
            message="捕獲するにはモンスターに近づこう"))
        val chance=(80-50f*r.enemyHp/foe.maxHp).toInt().coerceIn(25,80)
        val charged=state.copy(captureOrbs=state.captureOrbs-1)
        if(rng.nextInt(100)>=chance)return charged.copy(side=r.copy(
            message="捕獲失敗！ HPを減らすと成功しやすい"))
        val wild=TalentRules.awaken(foe.copy(
            id=UUID.randomUUID().toString(),isBoss=false,
            level=1,xp=0,victories=0))
        return charged.copy(captures=charged.captures+wild,
            catalog=(charged.catalog+wild).distinctBy{it.species to it.element}
                .takeLast(150),
            side=r.copy(cleared=r.cleared+r.room,enemyHp=0,
                message=foe.species+"を捕獲！ 個別の追加スキルと潜在能力を育てよう"))
    }
}
