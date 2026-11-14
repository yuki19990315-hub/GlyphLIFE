package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.*
import kotlin.math.min

private val RunGold=Color(0xFFFFD58B)
private val RunCyan=Color(0xFF8BD9EE)
private val RunInk=Color(0xFF0A1525)
private val RunPanel=Color(0xFF172641)
private val RunText=Color(0xFFEAF4FF)

/** Original pixel stage for a horizontally scrolling, one-finger RPG.
 * Background alone scrolls: there is deliberately no jump or platformer
 * collision, so a player can focus on their captured monsters and strategy.
 */
@Composable
private fun JourneyBackdrop(floor:Int,position:Float,modifier:Modifier=Modifier) {
    val biome=((floor-1).coerceAtLeast(0)/5)%5
    Canvas(modifier) {
        val sky=listOf(Color(0xFF102C40),Color(0xFF1B263E),
            Color(0xFF112D3A),Color(0xFF2D1F3D),Color(0xFF242036))[biome]
        val back=listOf(Color(0xFF1C4555),Color(0xFF2B3F58),
            Color(0xFF1D4B56),Color(0xFF473153),Color(0xFF40334D))[biome]
        val trees=listOf(Color(0xFF31596B),Color(0xFF3C506A),
            Color(0xFF276064),Color(0xFF5B3A60),Color(0xFF4A4262))[biome]
        val tile=listOf(Color(0xFF435E61),Color(0xFF4B5A6D),
            Color(0xFF3D6C67),Color(0xFF684A69),Color(0xFF6A5567))[biome]
        val ux=size.width/64f
        val uy=size.height/64f
        val u=minOf(ux,uy)
        val h=size.height
        fun block(x:Float,y:Float,w:Float,hh:Float,color:Color) =
            drawRect(color,topLeft=Offset(x*ux,y*uy),
                size=Size(w*ux,hh*uy))
        drawRect(sky)
        // A distinct moon/sky light, not a copied sprite from another game.
        drawCircle(RunGold.copy(alpha=.35f),
            radius=u*6.5f,center=Offset(size.width*.78f,h*.18f))
        drawCircle(Color(0xFFFBE9BD).copy(alpha=.55f),
            radius=u*4.1f,center=Offset(size.width*.78f,h*.18f))
        for(i in 0 until 17) {
            val x=((i*13+9)%64).toFloat()
            val y=((i*7+3)%22).toFloat()
            block(x,y,1f,1f,Color(0xFFE0F5FF).copy(alpha=.30f))
        }
        val slow=(position*2f)%78f
        for(i in -1..6) {
            val x=i*18f-slow*.35f
            block(x,27f,8f,23f,back)
            block(x+4f,22f,5f,28f,back)
            block(x+8f,29f,8f,21f,back)
            block(x+5f,19f,2f,6f,sky)
        }
        val shift=(position*5f)%82f
        for(i in -1..6) {
            val x=i*16f-shift*.65f
            if(biome%2==0) {
                block(x+4f,36f,3f,18f,Color(0xFF203A43))
                block(x,32f,11f,10f,trees)
                block(x+2f,27f,7f,12f,trees)
                block(x+5f,23f,2f,14f,trees)
            } else {
                block(x+3f,28f,10f,26f,trees)
                block(x+1f,27f,14f,3f,tile)
                block(x+6f,19f,3f,9f,trees)
                block(x+5f,17f,5f,3f,tile)
                block(x+6f,35f,4f,3f,back)
            }
        }
        val floorY=h*.79f
        drawRect(Color(0xFF172B3B),
            topLeft=Offset(0f,floorY),
            size=Size(size.width,h-floorY))
        drawRect(tile,topLeft=Offset(0f,floorY),
            size=Size(size.width,u*2.3f))
        drawRect(Color(0xFFE1B96D).copy(alpha=.35f),
            topLeft=Offset(0f,floorY),
            size=Size(size.width,u*.48f))
        val pathShift=(position*7f)%10f
        for(i in -1..9) {
            block(i*10f-pathShift,58f,8f,1.5f,
                Color(0xFF314D57).copy(alpha=.75f))
            block(i*10f+1f-pathShift,63f,4f,1.6f,
                Color(0xFF2B404A))
        }
        if(floor%5==0) {
            // The red obelisk hints at the next major boss encounter.
            block(57f,26f,3f,27f,Color(0xFF5E344F).copy(alpha=.65f))
            block(58f,23f,1f,15f,Color(0xFFE56F9A).copy(alpha=.65f))
        }
    }
}

@Composable
private fun JourneyStat(label:String,value:String,color:Color,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(1.dp)) {
        Text(label,color=RunCyan.copy(alpha=.83f),
            style=MaterialTheme.typography.labelSmall)
        Text(value,color=color,fontWeight=FontWeight.Bold,
            style=MaterialTheme.typography.titleSmall)
    }
}

/** Press instantly performs one step/strike. Keeping a finger pressed calls
 * onDirection, and the engine repeats movement and automatic melee attacks.
 * All pointer events in this surface are consumed so a vertical scroll never
 * steals a held movement gesture. */
@Composable
private fun JourneyHoldButton(
    symbol:String,title:String,hint:String,dir:Int,
    held:Int,enabled:Boolean,onDirection:(Int)->Unit,
    onPress:(Int)->Unit,modifier:Modifier=Modifier
) {
    val latestDirection=rememberUpdatedState(onDirection)
    val latestPress=rememberUpdatedState(onPress)
    val isHeld=held==dir
    val base=if(dir>0)Color(0xFFBD8C41) else Color(0xFF355B7B)
    val surface=if(isHeld)base.copy(alpha=.9f) else base.copy(alpha=.67f)
    Box(modifier.height(85.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(if(enabled)surface else Color(0xFF2D3543))
        .border(if(isHeld)2.dp else 1.dp,
            if(isHeld)RunGold else Color(0xFF70869D),
            RoundedCornerShape(12.dp))
        .pointerInput(dir,enabled) {
            if(!enabled)return@pointerInput
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                down.consume()
                latestPress.value(dir)
                latestDirection.value(dir)
                try {
                    do {
                        val event=awaitPointerEvent()
                        event.changes.forEach {it.consume()}
                    }while(event.changes.any{it.pressed})
                }finally {
                    latestDirection.value(0)
                }
            }
        },contentAlignment=Alignment.Center) {
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(symbol,color=RunText,fontSize=30.sp,
                fontWeight=FontWeight.Black)
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(title,color=RunText,fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.titleSmall)
                Text(hint,color=RunText.copy(alpha=.78f),
                    style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Original UI inspired by the ease of left/right-only side-scrolling RPGs.
 * Monsters, art, combat, capture and every owned stat still come from the
 * Monster Quest domain model, never from a copied reference-game asset. */
@Composable
fun JourneyExploreScreen(
    state:QuestState,store:MonsterArtworkStore,revision:Int,
    heldDirection:Int,onDirection:(Int)->Unit,onPress:(Int)->Unit,
    onCapture:()->Unit,onHeal:()->Unit,
    onSkill:(String,Boolean)->Unit,onDodge:(Int)->Unit,
    onBranch:(Int)->Unit,onAutoCapture:()->Unit,
    onClassic:()->Unit
) {
    val run=state.tapRun
    val journey=state.journey
    val enemy=run.enemy
    val battling=run.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS)
    val boss=run.phase==TapPhase.BOSS
    val rare=enemy?.lineage=="rare"
    val companion=remember(state.companion) {
        WildMonster(id=state.companion.id,
            species=state.companion.species,element=state.companion.element,
            maxHp=100,attack=10+state.companion.level,
            artFamily=state.companion.gameArtFamily(),
            extraSkillId=state.companion.extraSkillId,
            statSlots=state.companion.statSlots,
            stage=if(state.companion.level>=16)3
                else if(state.companion.level>=7)2 else 1)
    }
    var showSkills by remember { mutableStateOf(false) }
    val target=HuntRules.selected(state)

    Column(Modifier.fillMaxSize().background(RunInk)
        .verticalScroll(rememberScrollState())
        .padding(horizontal=10.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically) {
            Column {
                Text("QUEST / JOURNEY",color=RunGold,
                    style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Black)
                Text("進むほど強くなる敵に挑戦",
                    color=RunCyan,style=MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick=onClassic) {
                Text("旧タップ操作",color=RunCyan,
                    style=MaterialTheme.typography.labelSmall)
            }
        }

        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(7.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Surface(color=Color(0xFF283C59),
                shape=RoundedCornerShape(6.dp)) {
                Text(if(boss)"BOSS FLOOR "+run.floor
                    else "FLOOR "+run.floor,
                    Modifier.padding(horizontal=11.dp,vertical=6.dp),
                    color=RunGold,fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.labelMedium)
            }
            Surface(color=Color(0xFF193E43),
                shape=RoundedCornerShape(6.dp)) {
                Text("距離 "+run.distance,
                    Modifier.padding(horizontal=10.dp,vertical=6.dp),
                    color=RunText,style=MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            Text("BEST "+run.bestFloor+"F",color=RunCyan,
                style=MaterialTheme.typography.labelSmall)
        }

        BoxWithConstraints(Modifier.fillMaxWidth()
            .height(255.dp)
            .clip(RoundedCornerShape(11.dp))
            .border(2.dp,Color(0xFF728BA6),RoundedCornerShape(11.dp))
        ) {
            val fraction=when {
                run.phase!=TapPhase.WALK -> 0f
                heldDirection>0 ->
                    journey.repeatMs.toFloat()/JourneyRules.WALK_INTERVAL_MS
                heldDirection<0 ->
                    -journey.repeatMs.toFloat()/JourneyRules.BACK_INTERVAL_MS
                else -> 0f
            }
            val scenePosition=(run.floor-1)*TapAdventure.STEPS_PER_FLOOR+
                run.steps+fraction
            JourneyBackdrop(run.floor,scenePosition,Modifier.fillMaxSize())

            // Monster sprites are independently full-body fitted to 64px and
            // further letterboxed inside the field. Never crop big boss wings.
            val spriteSize=minOf(maxWidth*.43f,171.dp)
            val bossSize=minOf(maxWidth*.47f,182.dp)
            MonsterArtworkView(
                companion,store,revision,
                Modifier.size(spriteSize)
                    .align(Alignment.BottomStart)
                    .padding(start=7.dp,bottom=34.dp)
                    .offset(y=if(heldDirection!=0 && !battling)
                        (if((journey.travelMs/140)%2==0)-3 else 0).dp
                        else 0.dp),
                bossRelicKind=RelicRules.visualKind(
                    state,state.companion.id))
            if(enemy!=null) {
                if(rare) {
                    Box(Modifier.size(bossSize)
                        .align(Alignment.BottomEnd)
                        .padding(end=7.dp,bottom=34.dp)
                        .border(2.dp,RunGold.copy(alpha=.58f),
                            RoundedCornerShape(90.dp)))
                }
                MonsterArtworkView(
                    enemy,store,revision,
                    Modifier.size(if(boss)bossSize else spriteSize)
                        .align(Alignment.BottomEnd)
                        .padding(end=6.dp,bottom=34.dp),
                    facingRight=false)
            } else {
                Text("▶ 冒険中",color=RunText,
                    fontWeight=FontWeight.Bold,
                    modifier=Modifier.align(Alignment.CenterEnd)
                        .padding(end=22.dp)
                        .background(Color(0xB4172847),
                            RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    style=MaterialTheme.typography.labelMedium)
            }

            if(battling) {
                Column(
                    Modifier.align(Alignment.TopCenter)
                        .padding(start=6.dp,end=6.dp,top=7.dp)
                        .background(Color(0xD70C1930),
                            RoundedCornerShape(6.dp))
                        .padding(horizontal=9.dp,vertical=5.dp),
                    horizontalAlignment=Alignment.CenterHorizontally
                ) {
                    Text((if(boss)"♜ BOSS " else if(rare)"✦ RARE " else "⚔ ")+
                        enemy!!.species,color=if(rare)RunGold else RunText,
                        fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.labelSmall,maxLines=1)
                    Text("敵HP "+run.enemyHp+"/"+enemy.maxHp,
                        color=RunCyan,
                        style=MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(
                    progress={run.enemyHp.toFloat()/
                        enemy!!.maxHp.coerceAtLeast(1)},
                    color=if(rare)RunGold else Color(0xFFFF7794),
                    trackColor=Color(0xFF24344D),
                    modifier=Modifier.fillMaxWidth()
                        .align(Alignment.TopCenter).height(4.dp))
            }
            if(battling) {
                Surface(color=Color(0xDB0B1932),
                    shape=RoundedCornerShape(5.dp),
                    modifier=Modifier.align(Alignment.BottomCenter)
                        .padding(bottom=8.dp)) {
                    Text(if(boss) {
                        if(run.weakpointTaps>0)
                            "JUST DODGE!  攻撃×3"
                        else "大技まで "+String.format("%.1f",run.attackTimerMs/1000f)+"秒"
                    } else
                        "攻撃まで "+String.format("%.1f",run.attackTimerMs/1000f)+"秒"+
                        "　"+ElementalRules.label(
                            state.companion.element,enemy!!.element),
                        Modifier.padding(horizontal=7.dp,vertical=4.dp),
                        color=RunGold,
                        style=MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Dense RPG stat panels sit UNDER the short action scene, like the
        // reference's information-first silhouette, but remain original.
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress={state.playerHp.toFloat()/
                        TalentRules.maxHp(state).coerceAtLeast(1)},
                    modifier=Modifier.fillMaxWidth().height(9.dp),
                    color=Color(0xFF7AD7B3),
                    trackColor=Color(0xFF253850))
                Text("HP "+state.playerHp+"/"+TalentRules.maxHp(state),
                    color=RunText,style=MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                val xp=state.companionXp.toFloat()/
                    GrowthRules.xpNeeded(state.companion.level).coerceAtLeast(1)
                LinearProgressIndicator(
                    progress={xp.coerceIn(0f,1f)},
                    modifier=Modifier.fillMaxWidth().height(9.dp),
                    color=RunGold,trackColor=Color(0xFF253850))
                Text("Lv"+state.companion.level+
                    "  EXP "+state.companionXp,color=RunText,
                    style=MaterialTheme.typography.labelSmall)
            }
        }

        Row(Modifier.fillMaxWidth()
            .background(RunPanel,RoundedCornerShape(8.dp))
            .border(1.dp,Color(0xFF425B78),RoundedCornerShape(8.dp))
            .padding(9.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            JourneyStat("所持金",state.tapRun.gold.toString()+"G",
                RunGold,Modifier.weight(1f))
            JourneyStat("捕獲玉",state.captureOrbs.toString(),
                RunText,Modifier.weight(.85f))
            JourneyStat("結晶",state.summonStones.toString(),
                RunText,Modifier.weight(.75f))
            JourneyStat("連続勝利","×"+journey.chain,
                if(journey.chain>=5)RunGold else RunCyan,Modifier.weight(1f))
        }

        if(journey.bonusText.isNotBlank()) {
            Text(journey.bonusText,color=RunGold,
                fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.labelMedium)
        }
        Surface(color=Color(0xFF182C46),
            shape=RoundedCornerShape(7.dp)) {
            Text(run.message,
                Modifier.fillMaxWidth().padding(10.dp),
                color=RunText,style=MaterialTheme.typography.bodySmall)
        }
        if(target!=null) {
            Text("◎ 追跡： "+target.species+"（通常遭遇の45%）",
                color=RunCyan,style=MaterialTheme.typography.labelSmall)
        }
        if(ChallengeRules.active(state)) {
            Text("挑戦中のため探索は一時停止しています。",
                color=RunGold,style=MaterialTheme.typography.labelMedium)
        }

        if(run.phase==TapPhase.BRANCH) {
            Text("◆ 分かれ道：次の行き先を選択",color=RunGold,
                style=MaterialTheme.typography.titleSmall)
            listOf("✚ 泉：HP回復","◇ 宝箱：捕獲玉＋アイテム",
                "⚔ 危険な近道：強敵とレア報酬").forEachIndexed{index,title->
                OutlinedButton(onClick={onBranch(index)},
                    enabled=!ChallengeRules.active(state),
                    modifier=Modifier.fillMaxWidth().height(48.dp)) {
                    Text(title,color=RunText,style=MaterialTheme.typography.bodySmall)
                }
            }
        }else {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                JourneyHoldButton("◀",
                    if(run.phase==TapPhase.FIGHT)"にげる" else
                        if(boss)"左回避" else "もどる",
                    if(run.phase==TapPhase.FIGHT)"2歩後退" else
                        if(boss)"予告を避ける" else "左に移動",
                    -1,heldDirection,!ChallengeRules.active(state),
                    onDirection,onPress,Modifier.weight(1f))
                JourneyHoldButton("▶",
                    if(battling)"こうげき" else "すすむ",
                    if(battling)"長押しで連撃" else "長押しで冒険",
                    1,heldDirection,!ChallengeRules.active(state),
                    onDirection,onPress,Modifier.weight(1.34f))
            }
            if(boss) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text("ボスの予告方向と逆へ回避",color=RunCyan,
                        modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick={onDodge(-1)}) {Text("◀ 回避")}
                    OutlinedButton(onClick={onDodge(1)}) {Text("回避 ▶")}
                }
            }

            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(7.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Button(onClick=onCapture,
                    enabled=run.phase==TapPhase.FIGHT &&
                        state.captureOrbs>0 && state.captures.size<100,
                    modifier=Modifier.weight(1.2f).height(50.dp),
                    colors=ButtonDefaults.buttonColors(
                        containerColor=Color(0xFF376F69))) {
                    Text("◉ 捕獲 "+TapAdventure.captureChance(state)+"%",
                        color=RunText,
                        style=MaterialTheme.typography.labelSmall,
                        fontWeight=FontWeight.Bold)
                }
                OutlinedButton(onClick=onHeal,
                    enabled=state.potions>0 &&
                        state.playerHp<TalentRules.maxHp(state),
                    modifier=Modifier.weight(.75f).height(50.dp)) {
                    Text("✚ "+state.potions,
                        style=MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(onClick={showSkills=!showSkills},
                    enabled=battling,
                    modifier=Modifier.weight(1f).height(50.dp)) {
                    Text(if(showSkills)"スキル閉じる" else "✦ スキル",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
            if(showSkills && battling) {
                PartySkillButtons(state,run.skillCooldowns,
                    enabled=!ChallengeRules.active(state),
                    onUse=onSkill)
            }
            Row(Modifier.fillMaxWidth(),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=journey.autoCapture,
                    onClick=onAutoCapture,
                    label={Text(if(journey.autoCapture)"自動捕獲 ON"
                        else "自動捕獲 OFF",
                        style=MaterialTheme.typography.labelSmall)})
                Text("敵HP35%以下で各遭遇1回だけ捕獲を試す",
                    color=RunCyan,style=MaterialTheme.typography.labelSmall,
                    modifier=Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween) {
            Text("最高連勝 "+journey.bestChain,
                color=RunCyan,style=MaterialTheme.typography.labelSmall)
            Text("捕獲 "+journey.totalCaptures+" / 発見 "+state.catalog.size,
                color=RunCyan,style=MaterialTheme.typography.labelSmall)
        }
        Text("右を長押し：移動・自動攻撃。離すと停止。"+
            "左：後退／戦闘から退却。ボスは回避とスキルを駆使！",
            color=Color(0xFF92A8C3),
            style=MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(18.dp))
    }
}
