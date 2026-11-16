package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.*

private val QuestGold=Color(0xFFF4D88C)
private val QuestInk=Color(0xFF121C27)
private val QuestPanel=Color(0xED152332)
private val QuestPaper=Color(0xFFF4F0D5)

/**
 * An exploration-first layout: the road is the game, not a thumbnail above
 * several dashboards. It retains the user's monster protagonist and the
 * original capture/heal additions while reusing the existing saved state.
 */
@Composable
fun MinuteQuestRebuildScreen(
    state:QuestState,store:MonsterArtworkStore,revision:Int,
    heldDirection:Int,onDirection:(Int)->Unit,onPress:(Int)->Unit,
    onCapture:()->Unit,onHeal:()->Unit,
    onSkill:(String,Boolean)->Unit,onBurst:()->Unit,
    onAutoCapture:()->Unit,onAllocateStat:(String)->Unit,
    onBuySupply:(String)->Unit,onClassic:()->Unit,
    onHome:()->Unit
) {
    val run=state.tapRun
    val journey=state.journey
    val enemy=run.enemy
    val battling=run.phase==TapPhase.FIGHT || run.phase==TapPhase.BOSS
    val challenge=ChallengeRules.active(state)
    val position=journey.roadPosition.coerceAtLeast(0)
    val biome=PixelBiomeRules.zone(MinuteRoadRules.area(position),run.phase==TapPhase.BOSS)
    val hero=remember(state.companion) {
        WildMonster(
            id=state.companion.id,species=state.companion.species,
            element=state.companion.element,maxHp=100,
            attack=10+state.companion.level,
            artFamily=state.companion.gameArtFamily(),
            extraSkillId=state.companion.extraSkillId,
            statSlots=state.companion.statSlots,
            stage=when {state.companion.level>=16->3
                state.companion.level>=7->2 else->1}
        )
    }
    var details by remember {mutableStateOf(false)}
    val displayX by animateFloatAsState(
        targetValue=position.toFloat(),
        animationSpec=tween(durationMillis=190),label="road-camera")

    BoxWithConstraints(Modifier.fillMaxSize().background(QuestInk)) {
        val controlHeight=(maxHeight*.31f).coerceIn(106.dp,220.dp)
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val stageHeight=maxHeight
                val stageWidth=maxWidth
                PixelParallaxStage(biome,displayX*14f,
                    foreground=false,modifier=Modifier.fillMaxSize())

                // Pixel creatures are intentionally smaller than the view so
                // the player can read the road, background and enemy distance.
                val heroSize=minOf(stageHeight*.50f,stageWidth*.17f,108.dp)
                val foeSize=minOf(stageHeight*.53f,stageWidth*.18f,116.dp)
                val heroLeft=MonsterViewport.clampLeft(
                    (stageWidth*.31f).value,heroSize.value,stageWidth.value).dp
                val heroFloor=MonsterViewport.floorOffset(
                    stageHeight.value,heroSize.value).dp
                val foeFloor=MonsterViewport.floorOffset(
                    stageHeight.value,foeSize.value).dp
                val hop=if(!battling && heldDirection!=0)
                    BossBarrageRules.travelHop(journey.travelMs,heldDirection)
                    else 0f
                MonsterArtworkView(hero,store,revision,
                    Modifier.size(heroSize).align(Alignment.BottomStart)
                        .offset(x=heroLeft,y=-heroFloor-12.dp*hop),
                    bossRelicKind=RelicRules.visualKind(state,state.companion.id))

                val distance=journey.roadEnemyPosition-position
                if(enemy!=null && distance in 0..3) {
                    val offset=stageWidth*.18f*(distance-1).coerceAtLeast(0).toFloat()
                    MonsterArtworkView(enemy,store,revision,
                        Modifier.size(foeSize).align(Alignment.BottomEnd)
                            .offset(x=offset-12.dp,y=-foeFloor),facingRight=false)
                }
                PixelParallaxStage(biome,displayX*14f,
                    foreground=true,modifier=Modifier.fillMaxSize())

                Row(Modifier.fillMaxWidth().padding(5.dp),
                    verticalAlignment=Alignment.Top,
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                        .background(QuestPanel).padding(6.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("${hero.species}  Lv${state.companion.level}",
                                color=QuestPaper,fontSize=12.sp,fontWeight=FontWeight.Bold,
                                maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("${state.playerHp}/${TalentRules.maxHp(state)}",
                                color=QuestPaper,fontSize=11.sp)
                        }
                        LinearProgressIndicator(
                            progress={state.playerHp.toFloat()/
                                TalentRules.maxHp(state).coerceAtLeast(1)},
                            modifier=Modifier.fillMaxWidth().height(6.dp),
                            color=Color(0xFF72D39D),trackColor=Color(0xFF30454A))
                    }
                    Column(Modifier.clip(RoundedCornerShape(7.dp))
                        .background(QuestPanel).padding(horizontal=10.dp,vertical=5.dp),
                        horizontalAlignment=Alignment.End) {
                        Text("${position*MinuteRoadRules.METERS_PER_STEP}m",
                            color=QuestGold,fontSize=15.sp,fontWeight=FontWeight.Black)
                        Text("${run.gold}G   エリア${MinuteRoadRules.area(position)}",
                            color=QuestPaper,fontSize=10.sp)
                    }
                    TextButton(onClick={details=true},
                        modifier=Modifier.height(40.dp).background(
                            QuestPanel,RoundedCornerShape(7.dp)),
                        contentPadding=PaddingValues(horizontal=9.dp)) {
                        Text("メニュー",color=QuestGold,fontSize=12.sp)
                    }
                }

                if(battling && enemy!=null) {
                    Column(Modifier.align(Alignment.TopCenter)
                        .padding(top=52.dp).fillMaxWidth(.55f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(QuestPanel).padding(5.dp),
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Text((if(run.phase==TapPhase.BOSS)"BOSS · " else "")+
                            enemy.species,color=QuestPaper,fontSize=10.sp,
                            maxLines=1,overflow=TextOverflow.Ellipsis)
                        LinearProgressIndicator(
                            progress={run.enemyHp.toFloat()/enemy.maxHp.coerceAtLeast(1)},
                            modifier=Modifier.fillMaxWidth().height(5.dp),
                            color=Color(0xFFEE7D70),trackColor=Color(0xFF44363E))
                    }
                }
                if(challenge) {
                    Text("試練中：探索は一時停止",
                        Modifier.align(Alignment.Center)
                            .background(QuestPanel,RoundedCornerShape(8.dp))
                            .padding(12.dp),color=QuestGold)
                } else if(run.message.isNotBlank()) {
                    Text(run.message,
                        Modifier.align(Alignment.BottomCenter)
                            .padding(bottom=5.dp)
                            .background(QuestPanel,RoundedCornerShape(5.dp))
                            .padding(horizontal=10.dp,vertical=4.dp),
                        color=QuestPaper,fontSize=10.sp,maxLines=1,
                        overflow=TextOverflow.Ellipsis)
                }
            }

            Column(Modifier.fillMaxWidth().height(controlHeight)
                .background(QuestInk).padding(horizontal=5.dp,vertical=4.dp),
                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth().height(43.dp),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    RoadActionButton("◎ 捕獲  ${TapAdventure.captureChance(state)}%",
                        enabled=!challenge && run.phase==TapPhase.FIGHT &&
                            state.captureOrbs>0 && state.captures.size<100 &&
                            MinuteRoadRules.inRange(state),
                        onClick=onCapture,modifier=Modifier.weight(1f))
                    RoadActionButton("✚ 回復  ×${state.potions}",
                        enabled=!challenge && state.potions>0 &&
                            state.playerHp<TalentRules.maxHp(state),
                        onClick=onHeal,modifier=Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    RoadDirectionZone("◀  もどる",-1,heldDirection,!challenge,
                        onDirection,onPress,Modifier.weight(1f))
                    RoadDirectionZone(
                        if(battling)"すすむ・攻撃  ▶" else "すすむ  ▶",
                        1,heldDirection,!challenge,onDirection,onPress,
                        Modifier.weight(1f))
                }
            }
        }
    }

    if(details) {
        Dialog(onDismissRequest={details=false}) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(QuestInk).border(1.dp,QuestGold,RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically) {
                    Text("冒険メニュー",color=QuestGold,fontWeight=FontWeight.Bold)
                    TextButton(onClick={details=false}) {Text("閉じる")}
                }
                Text("右を長押しで前進＆接触攻撃。連打で攻撃が速くなる。",
                    color=QuestPaper,fontSize=12.sp)
                Text("最高 ${journey.roadBestPosition*10}m  ·  連勝 ${journey.chain}  ·  捕獲玉 ${state.captureOrbs}",
                    color=QuestPaper,fontSize=11.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    RoadActionButton("自動捕獲 ${if(journey.autoCapture)"ON" else "OFF"}",
                        !challenge,onAutoCapture,Modifier.weight(1f))
                    RoadActionButton("奥義 ${journey.burstGauge}%",
                        !challenge && battling &&
                            journey.burstGauge>=JourneyRules.BURST_COST &&
                            MinuteRoadRules.inRange(state),onBurst,Modifier.weight(1f))
                }
                if(battling) PartySkillButtons(state,run.skillCooldowns,
                    enabled=!challenge,onUse=onSkill)
                if(journey.roadFreeStats>0) {
                    Text("成長ポイント 残り${journey.roadFreeStats}",color=QuestGold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        listOf("力" to "power","体" to "vitality","速" to "speed",
                            "知" to "wisdom","運" to "luck").forEach { (name,key)->
                            OutlinedButton(onClick={onAllocateStat(key)},
                                modifier=Modifier.weight(1f),
                                contentPadding=PaddingValues(1.dp)) {
                                Text("$name+",fontSize=11.sp)
                            }
                        }
                    }
                }
                if(run.phase==TapPhase.WALK && position>0 && position%100 in 0..2) {
                    Text("旅商人",color=QuestGold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        RoadActionButton("回復薬 35G",run.gold>=35 && state.potions<99,
                            {onBuySupply("potion")},Modifier.weight(1f))
                        RoadActionButton("捕獲玉 45G",run.gold>=45 && state.captureOrbs<99,
                            {onBuySupply("orb")},Modifier.weight(1f))
                    }
                }
                HorizontalDivider(color=QuestGold.copy(alpha=.25f))
                TextButton(onClick={details=false;onHome()}) {Text("ホームへ戻る")}
                TextButton(onClick={details=false;onClassic()}) {
                    Text("旧探索画面へ切り替える（比較用）",fontSize=12.sp)
                }
            }
        }
    }
}

@Composable
private fun RoadActionButton(
    title:String,enabled:Boolean,onClick:()->Unit,modifier:Modifier=Modifier
) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=38.dp),
        shape=RoundedCornerShape(6.dp),
        contentPadding=PaddingValues(horizontal=5.dp,vertical=2.dp),
        colors=ButtonDefaults.buttonColors(
            containerColor=Color(0xFF345267),contentColor=QuestPaper,
            disabledContainerColor=Color(0xFF273744),
            disabledContentColor=Color(0xFF8B9BA4))) {
        Text(title,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}

@Composable
private fun RoadDirectionZone(
    title:String,dir:Int,held:Int,enabled:Boolean,
    onDirection:(Int)->Unit,onPress:(Int)->Unit,modifier:Modifier=Modifier
) {
    val latestDirection=rememberUpdatedState(onDirection)
    val latestPress=rememberUpdatedState(onPress)
    val active=held==dir
    Box(modifier.fillMaxHeight().clip(RoundedCornerShape(7.dp))
        .background(if(active)Color(0xFF607E65) else Color(0xFF304E4B))
        .border(if(active)2.dp else 1.dp,QuestGold,RoundedCornerShape(7.dp))
        .semantics {
            role=Role.Button
            contentDescription=title
            if(!enabled)disabled()
            else onClick {latestPress.value(dir);true}
        }
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
                    }while(event.changes.any {it.pressed})
                } finally {
                    latestDirection.value(0)
                }
            }
        },contentAlignment=Alignment.Center) {
        Text(title,color=QuestPaper,fontSize=18.sp,
            fontWeight=FontWeight.Black,maxLines=1)
    }
}
