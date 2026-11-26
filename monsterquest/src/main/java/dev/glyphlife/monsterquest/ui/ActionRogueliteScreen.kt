package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glyphlife.monsterquest.model.QuestState
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.model.gameArtFamily
import dev.glyphlife.monsterquest.model.OfflineMonsterForge
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import kotlinx.coroutines.delay
import kotlin.math.abs


/**
 * V6 playable, isolated action-roguelite vertical slice.
 * Own run state does not mutate legacy QuestState saves. World generation is deterministic
 * for a given floor/seed, and all tile collision checks live in one pure model.
 */
internal data class ActionRun(
    val floor:Int=1, val seed:Int=13827, val px:Int=1, val py:Int=10,
    val ex:Int=7, val ey:Int=2, val enemyHp:Int=32, val hp:Int=100,
    val stamina:Int=100, val potions:Int=2, val shards:Int=3,
    val captures:Int=0, val kills:Int=0, val active:Int=0,
    val supportCooldownMs:Int=950,
    val invulnerableMs:Int=0, val enemyCooldownMs:Int=650,
    val skillCooldownMs:Int=0, val message:String="遺跡の入口に到着"
) {
    val won:Boolean get()=enemyHp<=0
    val dead:Boolean get()=hp<=0
}
internal object ActionDungeon {
    const val W=9
    const val H=13
    private fun hash(x:Int,y:Int,seed:Int):Int {
        var v=seed xor (x*73856093) xor (y*19349663)
        v=v xor (v ushr 16); v*=0x7feb352d
        v=v xor (v ushr 15)
        return v and Int.MAX_VALUE
    }
    fun wall(x:Int,y:Int,seed:Int):Boolean {
        if(x !in 0 until W || y !in 0 until H)return true
        if(x==0 || x==W-1 || y==0 || y==H-1)return true
        // Fully open central corridor and entry/exit lanes guarantee connectivity.
        if(x in 3..5 || y==2 || y==10)return false
        return hash(x,y,seed)%7==0
    }
    fun start(floor:Int,seed:Int):ActionRun {
        val s=seed+floor*7919
        return ActionRun(floor=floor,seed=seed,px=4,py=10,
            ex=4,ey=2,enemyHp=24+floor*8,
            hp=100,shards=3,enemyCooldownMs=700)
    }
    fun move(r:ActionRun,dx:Int,dy:Int):ActionRun {
        if(r.dead)return r
        val x=r.px+dx;val y=r.py+dy
        if(wall(x,y,r.seed+r.floor*7919))return r
        if(!r.won && x==r.ex && y==r.ey)return r
        return r.copy(px=x,py=y,stamina=(r.stamina+3).coerceAtMost(100))
    }
    fun dodge(r:ActionRun,dx:Int,dy:Int):ActionRun {
        if(r.dead || r.stamina<24)return r
        var n=r.copy(stamina=r.stamina-24,invulnerableMs=450,message="回避成功")
        repeat(2){n=move(n,dx,dy)}
        return n
    }
    fun attack(r:ActionRun,skill:Boolean=false,monster:WildMonster?=null):ActionRun {
        if(r.dead || r.won || skill && (r.skillCooldownMs>0 || r.stamina<30))return r
        val body=monster?.let(MonsterDesign::bodyType)
        val range=if(skill || body==dev.glyphlife.monsterquest.pixel.MonsterBodyType.AVIAN ||
            body==dev.glyphlife.monsterquest.pixel.MonsterBodyType.AQUATIC)3 else 1
        val dist=abs(r.px-r.ex)+abs(r.py-r.ey)
        val n=if(skill)r.copy(stamina=r.stamina-30,skillCooldownMs=1700) else r
        if(dist>range)return n.copy(message="攻撃が届かない")
        val base=(monster?.attack ?: 9).coerceIn(1,80)
        val damage=base+(if(skill)9 else 0)+r.floor*2+((monster?.level ?: 1)/5)+
            ((monster?.stage ?: 1)-1)*3
        val left=(r.enemyHp-damage).coerceAtLeast(0)
        return n.copy(enemyHp=left,shards=n.shards+(if(left==0)5+r.floor else 0),
            kills=n.kills+(if(left==0)1 else 0),
            message=if(left==0)"守護者を撃破！ 次の階へ" else "敵に$damage ダメージ")
    }
    /** Auto support uses persisted creature statistics, including forge/fusion results. */
    fun support(r:ActionRun,party:List<WildMonster>,enemyElement:String):ActionRun {
        if(r.dead || r.won || party.size<2 || r.supportCooldownMs>0)return r
        val supporters=party.indices.filter {it!=r.active%party.size}.take(2)
        if(supporters.isEmpty())return r
        val near=abs(r.px-r.ex)+abs(r.py-r.ey)<=4
        if(!near)return r.copy(supportCooldownMs=600)
        val damage=supporters.sumOf{index ->
            val m=party[index]
            val advantage=when(m.element to enemyElement) {
                "水" to "火","火" to "草","草" to "水",
                "雷" to "水","光" to "闇" -> 4
                else -> 0
            }
            (m.attack.coerceIn(1,80)/3+1+m.stage.coerceIn(1,3)*2+advantage).coerceIn(1,35)
        }
        val left=(r.enemyHp-damage).coerceAtLeast(0)
        return r.copy(enemyHp=left,supportCooldownMs=1150,
            shards=r.shards+(if(left==0)5+r.floor else 0),
            kills=r.kills+(if(left==0)1 else 0),
            message=if(left==0)"仲間の連携で敵を撃破！" else "控え仲間が$damage ダメージ")
    }
    fun capture(r:ActionRun):ActionRun {
        if(r.dead || r.won || r.enemyHp>12 || r.shards<3)
            return r.copy(message="捕獲には敵HP12以下・結晶3個が必要")
        return r.copy(enemyHp=0,captures=r.captures+1,shards=r.shards-3,
            message="捕獲成功！ 図鑑と所持一覧に登録")
    }
    fun heal(r:ActionRun):ActionRun =
        if(r.potions<=0 || r.hp>=100)r else r.copy(potions=r.potions-1,
            hp=(r.hp+40).coerceAtMost(100),message="HPを40回復")
    fun tick(r:ActionRun,ms:Int=100):ActionRun {
        if(r.dead || r.won)return r
        var n=r.copy(invulnerableMs=(r.invulnerableMs-ms).coerceAtLeast(0),
            enemyCooldownMs=r.enemyCooldownMs-ms,
            skillCooldownMs=(r.skillCooldownMs-ms).coerceAtLeast(0),
            supportCooldownMs=(r.supportCooldownMs-ms).coerceAtLeast(0),
            stamina=(r.stamina+1).coerceAtMost(100))
        if(n.enemyCooldownMs>0)return n
        n=n.copy(enemyCooldownMs=650)
        val dist=abs(n.px-n.ex)+abs(n.py-n.ey)
        if(dist<=1 && n.invulnerableMs==0) {
            n=n.copy(hp=(n.hp-(6+n.floor*2)).coerceAtLeast(0),invulnerableMs=450,
                message="敵の攻撃を受けた")
        } else if(dist>1) {
            val candidates=listOf(
                (n.ex+if(n.px>n.ex)1 else -1) to n.ey,
                n.ex to (n.ey+if(n.py>n.ey)1 else -1))
            val step=candidates.firstOrNull{(x,y)->
                !wall(x,y,n.seed+n.floor*7919) && !(x==n.px && y==n.py)}
            if(step!=null)n=n.copy(ex=step.first,ey=step.second)
        }
        return n
    }
}

/** The main app keeps ownership of the permanent roster and original QuestState. */
@Composable
fun ActionRogueliteScreen(state:QuestState,store:MonsterArtworkStore,revision:Int,
    onCapture:(WildMonster)->Unit, visible:Boolean=true) {
    val context=LocalContext.current
    val prefs=remember {context.getSharedPreferences("monster_quest_action_v6",0)}
    var floor by remember {mutableIntStateOf(prefs.getInt("best_floor",1).coerceIn(1,1000))}
    var run by remember {mutableStateOf(ActionDungeon.start(floor,13827))}
    var direction by remember {mutableStateOf(0 to 0)}
    val generatedEnemy=remember(run.floor,run.seed) {
        val prompts=listOf("火の狼 角 尾", "雷の鳥 翼", "闇の竜 装甲",
            "水の海獣 鱗", "光の騎士 装甲", "草の精霊 花")
        OfflineMonsterForge.design(prompts[(run.floor-1)%prompts.size],
            "action-v6-${run.seed}-${run.floor}")
    }
    val roster=remember(state.captures,state.companion) {
        state.captures.filter{it.id in state.partyIds}.take(3).ifEmpty{
            state.captures.take(3).ifEmpty {
                listOf(WildMonster(
                    id=state.companion.id,species=state.companion.species,
                    element=state.companion.element,maxHp=100,
                    attack=12,level=state.companion.level,
                    artFamily=state.companion.gameArtFamily()))
            }
        }
    }
    LaunchedEffect(direction,visible,run.dead) {
        if(!visible)return@LaunchedEffect
        while(direction!=(0 to 0) && !run.dead){
            run=ActionDungeon.move(run,direction.first,direction.second)
            delay(125)
        }
    }
    LaunchedEffect(visible,run.dead,run.won,roster,generatedEnemy.element) {
        if(!visible)return@LaunchedEffect
        while(!run.dead && !run.won) {
            delay(100)
            run=ActionDungeon.support(ActionDungeon.tick(run),roster,generatedEnemy.element)
        }
    }
    LaunchedEffect(visible) { if(!visible) direction=0 to 0 }
    DisposableEffect(Unit) { onDispose {direction=0 to 0} }
    Column(Modifier.fillMaxSize().background(Color(0xFF17212A)).padding(7.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("MONSTER QUEST  /  V6 ACTION",color=Color(0xFFF3D68D),
                fontSize=12.sp,fontWeight=FontWeight.Bold)
            Text("階層 ${run.floor}  結晶 ${run.shards}",color=Color.White,fontSize=12.sp)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("HP ${run.hp}/100",color=Color(0xFFFFA8A8),fontSize=12.sp)
            Text("SP ${run.stamina}/100",color=Color(0xFFA8D9FF),fontSize=12.sp)
            Text("捕獲 ${run.captures}",color=Color.White,fontSize=12.sp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).border(2.dp,Color(0xFF9EAF87))) {
            val tile=minOf(maxWidth/ActionDungeon.W,maxHeight/ActionDungeon.H)
            val boardW=tile*ActionDungeon.W
            val boardH=tile*ActionDungeon.H
            Box(Modifier.size(boardW,boardH).align(Alignment.Center)) {
                Canvas(Modifier.fillMaxSize()) {
                    val cell=size.width/ActionDungeon.W
                    for(y in 0 until ActionDungeon.H) for(x in 0 until ActionDungeon.W) {
                        val pos=Offset(x*cell,y*cell)
                        val dark=ActionDungeon.wall(x,y,run.seed+run.floor*7919)
                        drawRect(if(dark)Color(0xFF36463D) else
                            if((x+y)%2==0)Color(0xFF62795C) else Color(0xFF5A7155),
                            topLeft=pos,size=androidx.compose.ui.geometry.Size(cell,cell))
                        if(dark)drawRect(Color(0xFF82977E),topLeft=pos,
                            size=androidx.compose.ui.geometry.Size(cell,cell),style=Stroke(1f))
                    }
                    if(run.won)drawCircle(Color(0xFFFFDC80),radius=cell*.3f,
                        center=Offset(cell*4.5f,cell*2.5f))
                }
                if(!run.won) {
                    MonsterArtworkView(
                        monster=generatedEnemy,
                        store=store,revision=revision,
                        modifier=Modifier.offset(x=tile*run.ex,y=tile*run.ey).size(tile),
                        facingRight=false)
                }
                if(roster.isNotEmpty()) {
                    MonsterArtworkView(monster=roster[run.active%roster.size],
                        store=store,revision=revision,
                        modifier=Modifier.offset(x=tile*run.px,y=tile*run.py).size(tile))
                } else {
                    Box(Modifier.offset(x=tile*run.px,y=tile*run.py)
                        .size(tile).background(Color(0xFFEBC96E),RoundedCornerShape(8.dp)))
                }
            }
            Text(if(run.won)"勝利！" else "敵HP ${run.enemyHp}",
                color=Color.White,modifier=Modifier.align(Alignment.TopEnd)
                    .background(Color(0xAA142122)).padding(4.dp),fontSize=11.sp)
        }
        Text(run.message,color=Color(0xFFF4E4B2),fontSize=11.sp,maxLines=1)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,
            verticalAlignment=Alignment.CenterVertically) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                DirectionHold("▲",0,-1){direction=it}
                Row {
                    DirectionHold("◀",-1,0){direction=it}
                    DirectionHold("▼",0,1){direction=it}
                    DirectionHold("▶",1,0){direction=it}
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    ActionButton("攻撃"){run=ActionDungeon.attack(run,monster=roster[run.active%roster.size])}
                    ActionButton("技"){run=ActionDungeon.attack(run,true,roster[run.active%roster.size])}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    ActionButton("回避"){run=ActionDungeon.dodge(run,0,-1)}
                    ActionButton("回復"){run=ActionDungeon.heal(run)}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    ActionButton("捕獲"){
                        val next=ActionDungeon.capture(run)
                        if(next.captures>run.captures) onCapture(generatedEnemy)
                        run=next
                    }
                    ActionButton("交代"){if(roster.isNotEmpty())run=run.copy(active=(run.active+1)%roster.size)}
                }
            }
        }
        if(run.won || run.dead) {
            Button(onClick={
                val next=if(run.won)run.floor+1 else run.floor
                if(run.won && next>floor){floor=next;prefs.edit().putInt("best_floor",floor).apply()}
                run=ActionDungeon.start(next,run.seed)
            },modifier=Modifier.fillMaxWidth()){
                Text(if(run.won)"次の階へ" else "同じ階から再挑戦")
            }
        }
        Text("捕獲は所持・図鑑に保存。生成・合成個体の能力を戦闘に反映。",
            color=Color(0xFFB0BCC5),fontSize=9.sp,maxLines=1)
    }
}

@Composable
private fun ActionButton(label:String,onClick:()->Unit) {
    Button(onClick=onClick,modifier=Modifier.width(68.dp).height(38.dp),
        contentPadding=PaddingValues(0.dp)) {Text(label,fontSize=12.sp)}
}

@Composable
private fun DirectionHold(label:String,dx:Int,dy:Int,onHeld:(Pair<Int,Int>)->Unit) {
    Box(Modifier.size(43.dp).padding(1.dp)
        .background(Color(0xFF435B6A),RoundedCornerShape(7.dp))
        .pointerInput(dx,dy){
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                onHeld(dx to dy)
                waitForUpOrCancellation()
                onHeld(0 to 0)
            }
        },contentAlignment=Alignment.Center) {
        Text(label,color=Color.White,fontSize=17.sp)
    }
}
