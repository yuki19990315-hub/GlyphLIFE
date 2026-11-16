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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.*
import kotlinx.coroutines.delay
import kotlin.math.min

private val RunGold=Color(0xFFF8D58E)
private val RunCyan=Color(0xFF8BD9EE)
private val RunInk=Color(0xFF07131D)
private val RunPanel=Color(0xFF10283A)
private val RunText=Color(0xFFFFF0D1)
private val RunBrass=Color(0xFF99764A)
private val RunRed=Color(0xFFDA6674)

private val QuestFrameShape=RoundedCornerShape(8.dp)

private data class StageDamageEvent(val amount:Int,val sequence:Int)

/** Combat notifications are children of the clipped stage, never of the
 * scrolling information column. The caller provides an edge-safe position. */
@Composable
private fun StageDamageBubble(
    event:StageDamageEvent?,isEnemy:Boolean,
    modifier:Modifier=Modifier
) {
    if(event==null)return
    Box(modifier
        .background(if(isEnemy)Color(0xE34C2738) else Color(0xE334435A),
            RoundedCornerShape(6.dp))
        .border(1.dp,if(isEnemy)RunGold else RunRed,
            RoundedCornerShape(6.dp))
        .padding(horizontal=8.dp,vertical=4.dp)) {
        Text("−${event.amount}"+(if(isEnemy)" DMG" else " HP"),
            color=Color.White,fontWeight=FontWeight.Black,
            fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuestEmblem(label:String,modifier:Modifier=Modifier) {
    Box(modifier.size(43.dp)
        .background(Brush.verticalGradient(listOf(Color(0xFF314355),RunInk)),
            RoundedCornerShape(6.dp))
        .border(2.dp,RunGold,RoundedCornerShape(6.dp)),
        contentAlignment=Alignment.Center) {
        Text(label,color=RunGold,fontSize=25.sp,fontWeight=FontWeight.Black)
    }
}

@Composable
private fun QuestMiniMeter(label:String,value:String,progress:Float,
    fill:Color,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(label,color=RunGold,fontSize=10.sp,fontWeight=FontWeight.Bold)
            Text(value,color=RunText,fontSize=10.sp,fontWeight=FontWeight.Bold)
        }
        LinearProgressIndicator(progress={progress.coerceIn(0f,1f)},
            modifier=Modifier.fillMaxWidth().height(9.dp)
                .border(1.dp,RunBrass, RoundedCornerShape(2.dp)),
            color=fill,trackColor=Color(0xFF112032))
    }
}

@Composable
private fun QuestBattleButton(title:String,sub:String,onClick:()->Unit,
    enabled:Boolean,modifier:Modifier=Modifier,
    highlight:Boolean=false,height:Dp=70.dp) {
    val stroke=if(enabled && highlight)RunGold else RunBrass
    Button(onClick=onClick,enabled=enabled,
        modifier=modifier.height(height)
            .border(2.dp,stroke,QuestFrameShape),
        shape=QuestFrameShape,
        contentPadding=PaddingValues(horizontal=2.dp,vertical=2.dp),
        colors=ButtonDefaults.buttonColors(
            containerColor=if(highlight)Color(0xFF67412B) else Color(0xFF14364A),
            disabledContainerColor=Color(0xFF182C37),
            disabledContentColor=Color(0xFF84919B))) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.Center) {
            Text(title,color=if(enabled)RunText else Color(0xFF84919B),
                fontSize=if(highlight)17.sp else 14.sp,
                fontWeight=FontWeight.Black,maxLines=1)
            Text(sub,color=if(enabled)RunGold else Color(0xFF84919B),
                fontSize=10.sp,maxLines=1)
        }
    }
}

/** Continuous projectile telegraphs; the hero automatically hops while
 * advancing or retreating, so there is no explicit jump button. */
@Composable
private fun BossBarrageLayer(run:TapRun,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val pixel=minOf(size.width,size.height)/64f
        val floorY=size.height*.78f
        val heroX=size.width*BossBarrageRules.screenX(run.bossPosition)
        drawRect(Color(0xFF9ADDBB).copy(alpha=.7f),
            topLeft=Offset(heroX-6*pixel,floorY+2*pixel),
            size=Size(12*pixel,1.7f*pixel))
        for(shot in run.bossBullets) {
            val x=size.width*BossBarrageRules.screenX(shot.targetX)
            val warning=if(shot.airborne)Color(0xFF77DAF3)
                else Color(0xFFFF7484)
            val targetY=if(shot.airborne)size.height*.56f else floorY
            drawRect(warning.copy(alpha=.30f),
                topLeft=Offset(x-9*pixel,targetY-6*pixel),
                size=Size(18*pixel,7*pixel))
            drawRect(warning,topLeft=Offset(x-7*pixel,targetY),
                size=Size(14*pixel,2*pixel))
        }
        run.bossBullets.forEach { shot ->
            val p=(shot.ageMs.toFloat()/shot.flightMs).coerceIn(0f,1f)
            val landingX=size.width*BossBarrageRules.screenX(shot.targetX)
            val x=size.width*.85f+(landingX-size.width*.85f)*p
            val y=size.height*(.20f+(if(shot.airborne).34f else .57f)*p)
            val color=if(shot.airborne)Color(0xFF88DFF0)
                else if(shot.style==2)Color(0xFFFF98E5)
                else Color(0xFFFFB16A)
            val block=pixel*(if(shot.style==2)3.3f else 2.8f)
            drawRect(color.copy(alpha=.30f),
                topLeft=Offset(x-block*1.7f,y-block*1.7f),
                size=Size(block*3.4f,block*3.4f))
            drawRect(color,topLeft=Offset(x-block,y-block),
                size=Size(block*2,block*2))
            drawRect(Color(0xFFFFF4DF),
                topLeft=Offset(x-block*.4f,y-block*.4f),
                size=Size(block*.8f,block*.8f))
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
    onPress:(Int)->Unit,modifier:Modifier=Modifier,
    buttonHeight:Dp=85.dp
) {
    val latestDirection=rememberUpdatedState(onDirection)
    val latestPress=rememberUpdatedState(onPress)
    val isHeld=held==dir
    val top=if(dir>0)Color(0xFF315A66) else Color(0xFF273F57)
    val bottom=if(dir>0)Color(0xFF103241) else Color(0xFF112238)
    Box(modifier.height(buttonHeight)
        .clip(QuestFrameShape)
        .background(Brush.verticalGradient(
            if(enabled)listOf(if(isHeld)RunBrass else top,bottom)
            else listOf(Color(0xFF24333E),Color(0xFF14212A))))
        .border(if(isHeld)3.dp else 2.dp,
            if(isHeld)RunGold else RunBrass,QuestFrameShape)
        .semantics {
            role=Role.Button
            contentDescription="$title。$hint"
            if(!enabled)disabled()
            else onClick {
                latestPress.value(dir)
                true
            }
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
                    }while(event.changes.any{it.pressed})
                }finally {
                    latestDirection.value(0)
                }
            }
        },contentAlignment=Alignment.Center) {
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(symbol,color=RunGold,fontSize=22.sp,
                fontWeight=FontWeight.Black)
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(title,color=RunText,fontWeight=FontWeight.Black,
                    style=MaterialTheme.typography.titleSmall)
                Text(hint,color=RunGold.copy(alpha=.85f),fontSize=9.sp,
                    maxLines=1)
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
    onAttack:()->Unit,
    onCapture:()->Unit,onHeal:()->Unit,
    onSkill:(String,Boolean)->Unit,
    onBranch:(Int)->Unit,onShrine:(Int)->Unit,onBurst:()->Unit,
    onAutoCapture:()->Unit,
    onBuySupply:(String)->Unit,onClassic:()->Unit
) {
    val run=state.tapRun
    val journey=state.journey
    val enemy=run.enemy
    val battling=run.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS)
    val boss=run.phase==TapPhase.BOSS
    val roadX=journey.roadPosition.coerceAtLeast(0)
    val biome=PixelBiomeRules.zone(MinuteRoadRules.area(roadX),boss)
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

    // Read damage from HP deltas, not localized battle-log strings. Reset
    // the enemy baseline when an encounter changes.
    var lastEnemyId by remember { mutableStateOf(enemy?.id) }
    var lastEnemyHp by remember { mutableIntStateOf(run.enemyHp) }
    var lastPlayerHp by remember { mutableIntStateOf(state.playerHp) }
    var damageSequence by remember { mutableIntStateOf(0) }
    var enemyDamage by remember { mutableStateOf<StageDamageEvent?>(null) }
    var playerDamage by remember { mutableStateOf<StageDamageEvent?>(null) }
    var stageMessage by remember { mutableStateOf("") }
    LaunchedEffect(battling,run.message) {
        if(battling && run.message.isNotBlank()) {
            stageMessage=run.message
            delay(2200)
            stageMessage=""
        } else stageMessage=""
    }
    LaunchedEffect(enemy?.id,run.enemyHp) {
        if(lastEnemyId!=enemy?.id) {
            enemyDamage=null
            playerDamage=null
        }
        if(battling && enemy!=null && lastEnemyId==enemy.id &&
            run.enemyHp<lastEnemyHp) {
            damageSequence++
            enemyDamage=StageDamageEvent(lastEnemyHp-run.enemyHp,damageSequence)
        }
        lastEnemyId=enemy?.id
        lastEnemyHp=run.enemyHp
    }
    LaunchedEffect(state.playerHp) {
        if(battling && state.playerHp<lastPlayerHp) {
            damageSequence++
            playerDamage=StageDamageEvent(lastPlayerHp-state.playerHp,damageSequence)
        }
        lastPlayerHp=state.playerHp
    }
    LaunchedEffect(enemyDamage?.sequence) {
        if(enemyDamage!=null) {
            delay(1100)
            enemyDamage=null
        }
    }
    LaunchedEffect(playerDamage?.sequence) {
        if(playerDamage!=null) {
            delay(1100)
            playerDamage=null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(RunInk)) {
    val small=maxHeight<660.dp
    val tiny=maxHeight<560.dp
    val (stageWidthValue,stageHeightValue)=MonsterViewport.stageSize(
        maxWidth.value,
        (if(tiny)158.dp else if(small)202.dp else 252.dp).value)
    val stageWidth=stageWidthValue.dp
    val stageHeight=stageHeightValue.dp
    val controlHeight=if(tiny)57.dp else if(small)65.dp else 76.dp
    Column(Modifier.fillMaxSize()
        .padding(horizontal=9.dp,vertical=5.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(
                listOf(Color(0xFF1E3345),Color(0xFF0B1F30))),QuestFrameShape)
            .border(1.dp,RunBrass,QuestFrameShape)
            .padding(horizontal=7.dp,vertical=5.dp),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically) {
            QuestEmblem("♜")
            Column(Modifier.weight(1f).padding(start=8.dp)) {
                Text("MONSTER QUEST",color=RunGold,
                    fontSize=16.sp,fontWeight=FontWeight.Black,
                    letterSpacing=1.2.sp,maxLines=1)
                Text("${state.companion.species}  ·  LV ${state.companion.level}",
                    color=RunCyan,fontSize=10.sp,maxLines=1)
            }
            Column(horizontalAlignment=Alignment.End) {
                Text("✦ ${state.summonStones}",color=RunGold,
                    fontSize=14.sp,fontWeight=FontWeight.Bold)
                Text("● ${run.gold} G",color=RunText,fontSize=10.sp)
            }
        }

        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(5.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Surface(color=Color(0xFF263B50),
                shape=RoundedCornerShape(5.dp),
                border=androidx.compose.foundation.BorderStroke(1.dp,RunBrass)) {
                Text(if(boss)"☠ BOSS · ${biome.displayName}"
                    else "◆ エリア${MinuteRoadRules.area(roadX)} · ${biome.displayName}",
                    Modifier.padding(horizontal=8.dp,vertical=5.dp),
                    color=RunGold,fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.labelMedium)
            }
            Surface(color=Color(0xFF19353D),
                shape=RoundedCornerShape(5.dp)) {
                Text("${roadX*MinuteRoadRules.METERS_PER_STEP}m",
                    Modifier.padding(horizontal=8.dp,vertical=5.dp),
                    color=RunCyan,style=MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            Text("BEST ${journey.roadBestPosition*MinuteRoadRules.METERS_PER_STEP}m",
                color=RunGold,fontSize=10.sp)
            TextButton(onClick=onClassic,
                contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),
                modifier=Modifier.height(30.dp)) {
                Text("旧モード",color=RunCyan,fontSize=9.sp)
            }
        }

        BoxWithConstraints(Modifier.width(stageWidth)
            .height(stageHeight)
            .align(Alignment.CenterHorizontally)
            .clip(QuestFrameShape)
            .border(3.dp,RunGold,QuestFrameShape)
        ) {
            val fraction=when {
                heldDirection>0 && !battling ->
                    journey.repeatMs.toFloat()/MinuteRoadRules.WALK_INTERVAL_MS
                heldDirection<0 ->
                    -journey.repeatMs.toFloat()/MinuteRoadRules.BACK_INTERVAL_MS
                else -> 0f
            }
            val worldTravel=(roadX+fraction)*14f
            PixelParallaxStage(biome,worldTravel,foreground=false,
                modifier=Modifier.fillMaxSize())

            // Use the entire square sprite viewport. Insetting via padding
            // *inside* a fixed-size Image previously squashed its content to
            // a smaller rectangle and made large monsters look truncated.
            // Move the whole square instead, with the opaque feet aligned to
            // the backdrop's 79%-height ground line.
            val spriteSize=if(boss)
                minOf(maxWidth*.26f,if(tiny)75.dp else 112.dp)
                else minOf(maxWidth*.34f,if(tiny)100.dp else 150.dp)
            val bossSize=minOf(maxWidth*.36f,if(tiny)112.dp else 165.dp)
            val foeSize=if(boss)bossSize else spriteSize
            val heroGroundOffset=MonsterViewport.floorOffset(
                stageHeight.value,spriteSize.value).dp
            val enemyGroundOffset=MonsterViewport.floorOffset(
                stageHeight.value,foeSize.value).dp
            val heroCenter=maxWidth*.22f
            val heroLeft=MonsterViewport.clampLeft(
                heroCenter.value,spriteSize.value,maxWidth.value).dp
            val walkingHop=if(!battling && heldDirection!=0)
                BossBarrageRules.travelHop(journey.travelMs,heldDirection)
                else 0f
            MonsterArtworkView(
                companion,store,revision,
                Modifier.size(spriteSize)
                    .align(Alignment.BottomStart)
                    .offset(
                        x=heroLeft,
                        y=-heroGroundOffset-18.dp*walkingHop
                    ),
                bossRelicKind=RelicRules.visualKind(
                    state,state.companion.id))
            val foeDistance=journey.roadEnemyPosition-roadX
            if(enemy!=null && foeDistance<=3) {
                if(rare) {
                    Box(Modifier.size(foeSize)
                        .align(Alignment.BottomEnd)
                        .offset(x=(-6).dp,y=-enemyGroundOffset)
                        .background(RunGold.copy(alpha=.08f)))
                }
                MonsterArtworkView(
                    enemy,store,revision,
                    Modifier.size(foeSize)
                        .align(Alignment.BottomEnd)
                        .offset(x=(-6).dp,y=-enemyGroundOffset),
                    facingRight=false)
            } else {
                Text(if(enemy!=null)"▶ 敵まで${foeDistance}歩" else "▶ 一本道を冒険中",
                    color=RunText,
                    fontWeight=FontWeight.Bold,
                    modifier=Modifier.align(Alignment.CenterEnd)
                        .padding(end=22.dp)
                        .background(Color(0xB4172847),
                            RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    style=MaterialTheme.typography.labelMedium)
            }

            PixelParallaxStage(biome,worldTravel,foreground=true,
                modifier=Modifier.fillMaxSize())

            // Road bosses are tougher melee encounters, not a separate
            // projectile-dodging minigame. The same two inputs always work.

            if(battling) {
                Column(
                    Modifier.align(Alignment.TopCenter)
                        .padding(start=12.dp,end=12.dp,top=8.dp)
                        .fillMaxWidth(.82f)
                        .background(Color(0xD90D1B2B),QuestFrameShape)
                        .border(1.dp,RunBrass,QuestFrameShape)
                        .padding(horizontal=9.dp,vertical=5.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(3.dp)
                ) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text((if(boss)"☠ BOSS  " else if(rare)"✦ RARE  " else "⚔ ")+
                            enemy!!.species,color=RunGold,
                            modifier=Modifier.weight(1f),
                            fontSize=11.sp,fontWeight=FontWeight.Black,
                            maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("${run.enemyHp}/${enemy!!.maxHp}",
                            color=RunText,fontSize=10.sp,maxLines=1)
                    }
                    LinearProgressIndicator(
                        progress={run.enemyHp.toFloat()/enemy!!.maxHp.coerceAtLeast(1)},
                        color=if(rare)RunGold else RunRed,
                        trackColor=Color(0xFF364052),
                        modifier=Modifier.fillMaxWidth().height(7.dp)
                            .border(1.dp,RunBrass))
                }
            }
            if(battling) {
                // All combat popups are children of this clipped stage.
                // Leave a safe zone below the enemy HP bar on short screens.
                val bubbleWidth=minOf(128.dp,maxWidth*.36f)
                val bubbleY=58.dp
                StageDamageBubble(playerDamage,isEnemy=false,
                    modifier=Modifier.align(Alignment.TopStart)
                        .offset(x=8.dp,y=bubbleY)
                        .width(bubbleWidth))
                StageDamageBubble(enemyDamage,isEnemy=true,
                    modifier=Modifier.align(Alignment.TopEnd)
                        .offset(x=(-8).dp,y=bubbleY)
                        .width(bubbleWidth))
                // Move combat messages from the scrolling lower panel into
                // a wrapped, edge-safe bubble above the in-scene battle hint.
                // On the shortest displays, alternate the combat log with
                // numeric hit markers rather than stacking them over sprites.
                if(stageMessage.isNotBlank() &&
                    enemyDamage==null && playerDamage==null) {
                    Box(Modifier.align(Alignment.BottomCenter)
                        .padding(start=8.dp,end=8.dp,bottom=42.dp)
                        .fillMaxWidth()
                        .background(Color(0xE51A2940),QuestFrameShape)
                        .border(1.dp,RunBrass,QuestFrameShape)
                        .padding(horizontal=6.dp,vertical=4.dp),
                        contentAlignment=Alignment.Center) {
                        Text(stageMessage,color=RunText,fontSize=10.sp,
                            maxLines=2,overflow=TextOverflow.Ellipsis,
                            textAlign=TextAlign.Center)
                    }
                }
            }
            if(battling) {
                Surface(color=Color(0xDB0B1932),
                    shape=RoundedCornerShape(5.dp),
                    modifier=Modifier.align(Alignment.BottomCenter)
                        .padding(bottom=7.dp)
                        .widthIn(max=stageWidth-16.dp)) {
                    Text(if(boss) "⚔ ボスも右長押しで接近・自動攻撃" else
                        "⚔ "+ElementalRules.label(
                            state.companion.element,enemy!!.element),
                        Modifier.padding(horizontal=7.dp,vertical=4.dp),
                        color=RunGold,
                        fontSize=10.sp,maxLines=2,
                        overflow=TextOverflow.Ellipsis,
                        textAlign=TextAlign.Center)
                }
            }
        }

        // Golden RPG status bar from the approved concept. It stays visible
        // while less important collection and supply details can scroll.
        Row(Modifier.fillMaxWidth()
            .background(RunPanel,QuestFrameShape)
            .border(1.dp,RunBrass,QuestFrameShape)
            .padding(horizontal=9.dp,vertical=7.dp),
            horizontalArrangement=Arrangement.spacedBy(10.dp),
            verticalAlignment=Alignment.CenterVertically) {
            QuestMiniMeter("❤  HP",
                "${state.playerHp}/${TalentRules.maxHp(state)}",
                state.playerHp.toFloat()/TalentRules.maxHp(state).coerceAtLeast(1),
                Color(0xFF72C6A5),Modifier.weight(1.25f))
            QuestMiniMeter("✦  EXP", "Lv${state.companion.level}",
                state.companionXp.toFloat()/
                    GrowthRules.xpNeeded(state.companion.level).coerceAtLeast(1),
                RunCyan,Modifier.weight(.9f))
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("連勝",color=RunCyan,fontSize=9.sp)
                Text("×${journey.chain}",color=RunGold,
                    fontSize=16.sp,fontWeight=FontWeight.Black)
            }
        }

        // Information can scroll, but the direction and action buttons
        // battle actions below MUST stay visible on compact phone screens.
        Column(Modifier.weight(1f).fillMaxWidth()
            .verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(7.dp)) {
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
        if(!battling) {
            Surface(color=Color(0xFF182C46),
                shape=RoundedCornerShape(7.dp)) {
                Text(run.message,
                    Modifier.fillMaxWidth().padding(10.dp),
                    color=RunText,style=MaterialTheme.typography.bodySmall)
            }
        }
        if(target!=null) {
            Text("◎ 追跡： "+target.species+"（通常遭遇の45%）",
                color=RunCyan,style=MaterialTheme.typography.labelSmall)
        }
        if(run.phase==TapPhase.WALK && roadX>0 &&
            roadX%100 in 0..2) {
            Surface(color=Color(0xFF26384D),shape=RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("◇ 旅商人の小屋  /  ${roadX*10}m",
                        color=RunGold,fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.titleSmall)
                    Text("遠征中の補給。所持金を使ってアイテムを購入！",
                        color=RunCyan,style=MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick={onBuySupply("potion")},
                            enabled=state.tapRun.gold>=35&&state.potions<99,
                            modifier=Modifier.weight(1f)
                        ) {Text("回復薬 35G",
                            style=MaterialTheme.typography.labelSmall)}
                        OutlinedButton(
                            onClick={onBuySupply("orb")},
                            enabled=state.tapRun.gold>=45&&state.captureOrbs<99,
                            modifier=Modifier.weight(1f)
                        ) {Text("捕獲玉 45G",
                            style=MaterialTheme.typography.labelSmall)}
                    }
                }
            }
        }
        if(ChallengeRules.active(state)) {
            Text("挑戦中のため探索は一時停止しています。",
                color=RunGold,style=MaterialTheme.typography.labelMedium)
        }

        if(boss) {
            Surface(color=Color(0xFF26334F),shape=RoundedCornerShape(8.dp)) {
                Text("強敵出現！ 右で近づき自動攻撃、左で距離を取れる。",
                    Modifier.fillMaxWidth().padding(9.dp),
                    color=RunCyan,style=MaterialTheme.typography.labelSmall)
            }
        }

        if(showSkills && battling) {
            PartySkillButtons(state,run.skillCooldowns,
                enabled=!ChallengeRules.active(state),onUse=onSkill)
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
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween) {
            Text("最高連勝 "+journey.bestChain,
                color=RunCyan,style=MaterialTheme.typography.labelSmall)
            Text("捕獲 "+journey.totalCaptures+" / 発見 "+state.catalog.size,
                color=RunCyan,style=MaterialTheme.typography.labelSmall)
        }
        Text("右を長押しで前進＆接触自動攻撃。連打で攻撃が速くなる。"+
            " 左は後退。奥義 "+journey.burstUses+"回",
            color=Color(0xFF92A8C3),style=MaterialTheme.typography.labelSmall)
        }

        kotlin.run {
            Row(Modifier.fillMaxWidth()
                .background(Color(0xFF101F31),RoundedCornerShape(5.dp))
                .padding(horizontal=5.dp,vertical=4.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("✦ 奥義ゲージ",color=RunGold,
                    fontSize=10.sp,fontWeight=FontWeight.Bold)
                LinearProgressIndicator(
                    progress={journey.burstGauge.toFloat()/JourneyRules.BURST_COST},
                    color=if(journey.burstGauge==JourneyRules.BURST_COST)
                        RunGold else Color(0xFFA5A4F0),
                    trackColor=Color(0xFF29364A),
                    modifier=Modifier.weight(1f).height(8.dp)
                        .border(1.dp,RunBrass))
                Text("${journey.burstGauge}%",color=RunGold,
                    fontSize=10.sp)
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                JourneyHoldButton("◀",
                    "もどる", "距離を取る",
                    -1,heldDirection,!ChallengeRules.active(state),
                    onDirection,onPress,Modifier.weight(1f),controlHeight)
                JourneyHoldButton("▶",
                    if(battling)"たたかう" else "すすむ",
                    if(battling)"接触で自動攻撃" else "長押しで前進",
                    1,heldDirection,!ChallengeRules.active(state),
                    onDirection,onPress,Modifier.weight(1f),controlHeight)
            }

            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(4.dp),
                verticalAlignment=Alignment.CenterVertically) {
                QuestBattleButton("✦ 奥義",
                    if(journey.burstGauge==JourneyRules.BURST_COST)"発動可能"
                    else "${journey.burstGauge}%",
                    onBurst,
                    battling && journey.burstGauge>=JourneyRules.BURST_COST &&
                        MinuteRoadRules.inRange(state),
                    Modifier.weight(1.15f),highlight=true,height=44.dp)
                QuestBattleButton("◉ 捕獲",
                    "${TapAdventure.captureChance(state)}%",
                    onCapture,
                    run.phase==TapPhase.FIGHT && state.captureOrbs>0 &&
                        state.captures.size<100 && MinuteRoadRules.inRange(state),
                    Modifier.weight(1f),height=44.dp)
                OutlinedButton(onClick=onHeal,
                    enabled=state.potions>0 &&
                        state.playerHp<TalentRules.maxHp(state),
                    modifier=Modifier.weight(.72f).height(44.dp),
                    contentPadding=PaddingValues(0.dp),
                    shape=QuestFrameShape) {
                    Text("✚ ${state.potions}",fontSize=11.sp)
                }
                OutlinedButton(onClick={showSkills=!showSkills},
                    enabled=battling,
                    modifier=Modifier.weight(.72f).height(44.dp),
                    contentPadding=PaddingValues(0.dp),
                    shape=QuestFrameShape) {
                    Text(if(showSkills)"閉じる" else "技",
                        fontSize=11.sp)
                }
            }
        }
    }
    }
}
