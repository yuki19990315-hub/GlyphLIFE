package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.*
import dev.glyphlife.monsterquest.pixel.BossRelicPixels
import dev.glyphlife.monsterquest.pixel.GbaMonsterArtStandard
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline
import kotlin.math.roundToInt

private val SideGold=Color(0xFFFFDC91)
private val SideBlue=Color(0xFF8AC8FF)
private val SideSurface=Color(0xFF18233A)
private val SideWhite=Color(0xFFE9F2FF)

/** Default exploration mode: real continuous movement, jump physics,
 * backtracking, permanent mobility items, combat, capture and boss drops.
 * Kept separate from the older tap dungeon so old saves never disappear. */
@Composable
fun MetroidvaniaScreen(
    state:QuestState,store:MonsterArtworkStore,revision:Int,
    movement:Int,onMovement:(Int)->Unit,
    onJump:()->Unit,onDash:()->Unit,
    onAttack:()->Unit,onCapture:()->Unit,
    onUseSkill:(String,Boolean)->Unit,
    onInteract:()->Unit,onHeal:()->Unit,
    onLegacy:()->Unit
) {
    val run=state.side
    val place=MetroidRules.rooms[run.room]
    val hero=remember(state.companion,store,revision,
        RelicRules.visualKind(state,state.companion.id)) {
        val pet=WildMonster(
            id=state.companion.id,species=state.companion.name,
            element=state.companion.element,maxHp=100,
            attack=10+state.companion.level,
            level=state.companion.level,
            artFamily=state.companion.gameArtFamily(),
            stage=when {
                state.companion.level>=16->3
                state.companion.level>=7->2
                else->1
            })
        PixelAssetPipeline.toBitmap(GbaMonsterArtStandard.fitWithMargin(
            BossRelicPixels.compose(store.spriteFor(pet),
                RelicRules.visualKind(state,state.companion.id)),9))
            .asImageBitmap()
    }
    val foe=remember(run.room,store,revision) {
        PixelAssetPipeline.toBitmap(GbaMonsterArtStandard.fitWithMargin(
            store.spriteFor(MetroidRules.enemy(run.room)),9)).asImageBitmap()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF091323))
        .verticalScroll(rememberScrollState())
        .padding(horizontal=9.dp,vertical=7.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("▣ 横スクロール探索",color=SideGold,
                    style=MaterialTheme.typography.titleLarge,
                    fontWeight=FontWeight.Black)
                Text("エリア "+(run.room+1)+"/6　"+place.name,
                    color=SideBlue,style=MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick=onLegacy) {
                Text("旧探索に戻る",color=SideBlue,
                    style=MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Column(Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress={state.playerHp.toFloat()/
                        TalentRules.maxHp(state).coerceAtLeast(1)},
                    modifier=Modifier.fillMaxWidth().height(9.dp),
                    color=Color(0xFF73DDA3),trackColor=Color(0xFF27364F))
                Text("HP "+state.playerHp+"/"+TalentRules.maxHp(state)+
                    "　 "+state.tapRun.gold+"G",
                    style=MaterialTheme.typography.labelSmall,
                    color=SideWhite)
            }
            if(run.enemyHp>0) {
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                    LinearProgressIndicator(
                        progress={run.enemyHp.toFloat()/
                            MetroidRules.maxEnemyHp(run.room).coerceAtLeast(1)},
                        modifier=Modifier.fillMaxWidth().height(9.dp),
                        color=Color(0xFFFA819E),trackColor=Color(0xFF27364F))
                    Text(place.enemyName+" HP "+run.enemyHp,
                        style=MaterialTheme.typography.labelSmall,
                        color=SideWhite,maxLines=1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf("地下道","森","工房","回廊","深淵","王座")
                .forEachIndexed{index,name->
                    Surface(
                        shape=RoundedCornerShape(5.dp),
                        color=when {
                            index==run.room -> Color(0xFFB48B3D)
                            index in run.cleared -> Color(0xFF295A50)
                            else -> Color(0xFF203050)
                        }
                    ) {
                        Text((index+1).toString()+" "+name,
                            Modifier.padding(horizontal=7.dp,vertical=4.dp),
                            color=SideWhite,style=MaterialTheme.typography.labelSmall)
                    }
                }
        }
        Text(place.subtitle,color=SideBlue,
            style=MaterialTheme.typography.labelSmall)
        Box(Modifier.fillMaxWidth().aspectRatio(14f/11f)
            .background(Color(0xFF0B1730),RoundedCornerShape(6.dp))) {
            SideScene(run,hero,foe,Modifier.fillMaxSize())
            if(run.hurtCooldownMs>0) {
                Text("被弾！",color=Color(0xFFFFA2A7),
                    modifier=Modifier.align(Alignment.TopCenter)
                        .padding(top=34.dp),
                    style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Black)
            }
            if(run.room==1 && "double" in run.acquired) {
                Text("▲ 高台から回廊へ",
                    Modifier.align(Alignment.TopStart).padding(6.dp),
                    color=SideGold,style=MaterialTheme.typography.labelSmall)
            }
        }
        Surface(color=SideSurface,shape=RoundedCornerShape(7.dp)) {
            Text(run.message,
                Modifier.fillMaxWidth().padding(10.dp),
                color=SideWhite,style=MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            HoldMovement("◀", -1,movement,onMovement,Modifier.weight(1f))
            HoldMovement("▶", 1,movement,onMovement,Modifier.weight(1f))
            Button(onClick=onJump,Modifier.weight(1.15f).height(61.dp),
                colors=ButtonDefaults.buttonColors(
                    containerColor=Color(0xFF365C89))) {
                Text("↑\nジャンプ",textAlign=TextAlign.Center,
                    fontSize=12.sp,lineHeight=16.sp)
            }
            Button(onClick=onDash,Modifier.weight(1.15f).height(61.dp),
                enabled="dash" in run.acquired && run.dashCooldownMs==0,
                colors=ButtonDefaults.buttonColors(
                    containerColor=Color(0xFF664794))) {
                Text("➤\nダッシュ",textAlign=TextAlign.Center,
                    fontSize=11.sp,lineHeight=16.sp)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Button(onClick=onAttack,Modifier.weight(1.5f).height(52.dp),
                enabled=run.attackCooldownMs==0,
                colors=ButtonDefaults.buttonColors(
                    containerColor=Color(0xFFB38A41))) {
                Text("⚔ 攻撃",color=Color(0xFF0E1729),
                    fontWeight=FontWeight.Bold)
            }
            OutlinedButton(onClick=onCapture,Modifier.weight(1f).height(52.dp),
                enabled=run.enemyHp>0 && state.captureOrbs>0 &&
                    !place.boss) {
                Text("◉ 捕獲 "+state.captureOrbs,
                    fontSize=11.sp)
            }
            OutlinedButton(onClick=onInteract,Modifier.weight(1f).height(52.dp)) {
                Text("◆ 調べる",fontSize=11.sp)
            }
            OutlinedButton(onClick=onHeal,Modifier.weight(0.85f).height(52.dp),
                enabled=state.potions>0 &&
                    state.playerHp<TalentRules.maxHp(state)) {
                Text("✚ "+state.potions,fontSize=11.sp)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Text("能力：",color=SideGold,style=MaterialTheme.typography.labelSmall)
            Text(if("double" in run.acquired)"✦ 二段ジャンプ"
                else "🔒 二段ジャンプ",color=SideBlue,
                style=MaterialTheme.typography.labelSmall)
            Text(if("dash" in run.acquired)"✦ ダッシュ"
                else "🔒 ダッシュ",color=SideBlue,
                style=MaterialTheme.typography.labelSmall)
        }
        Text("攻略の流れ：工房で二段ジャンプを入手 → 森の高台へ戻る"+
            " → 回廊でダッシュを獲得 → 工房の封印門から深淵へ",
            style=MaterialTheme.typography.labelSmall,color=SideBlue)
        if(run.enemyHp>0) {
            PartySkillButtons(state,run.skillCooldowns,enabled=true,
                onUse=onUseSkill)
        }
        Text("移動ボタンを押し続けて進む。ジャンプ中にもう一度押すと二段ジャンプ。"+
            "経験値・捕獲・装備ドロップ・追加スキルは既存の育成に連動します。",
            color=Color(0xFFA5B3CB),style=MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HoldMovement(
    text:String,direction:Int,current:Int,
    onMovement:(Int)->Unit,modifier:Modifier
) {
    val latest=rememberUpdatedState(onMovement)
    Box(modifier.height(61.dp)
        .background(
            if(current==direction) Color(0xFF5D86C0) else Color(0xFF293E62),
            RoundedCornerShape(12.dp))
        .pointerInput(direction) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false)
                latest.value(direction)
                try {
                    do {
                        val event=awaitPointerEvent()
                    } while(event.changes.any {it.pressed})
                } finally { latest.value(0) }
            }
        },
        contentAlignment=Alignment.Center) {
        Text(text,color=SideWhite,fontWeight=FontWeight.Bold,
            style=MaterialTheme.typography.headlineMedium)
    }
}

/** Pixel-perfect imagery from the same full-body 64px sources, with guaranteed
 * 9px transparent margins after armor overlays. Camera shows 14 world tiles. */
@Composable
private fun SideScene(
    run:SideRun,hero:ImageBitmap,foe:ImageBitmap,modifier:Modifier=Modifier
) {
    val world=MetroidRules.rooms[run.room]
    Canvas(modifier) {
        val unit=size.width/MetroidRules.VIEW_WIDTH
        val camera=(run.x-5f).coerceIn(0f,
            MetroidRules.ROOM_WIDTH-MetroidRules.VIEW_WIDTH)
        fun xx(worldX:Float)= (worldX-camera)*unit
        fun yy(worldY:Float)=worldY*unit
        // Dark silhouettes and glowing clouds, moving with the camera.
        val background=when(run.room) {
            0 -> Color(0xFF0A1A31)
            1 -> Color(0xFF102D35)
            2 -> Color(0xFF111C40)
            3 -> Color(0xFF1A233B)
            4 -> Color(0xFF25182D)
            else -> Color(0xFF291B32)
        }
        drawRect(background)
        for(i in -1..12) {
            val x=i*unit*1.8f - camera*unit*.22f
            drawRect(Color(0xFF152E43).copy(alpha=.45f),
                topLeft=Offset(x,unit*(2+(i%3)*.65f)),
                size=Size(unit*.9f,unit*8f))
            drawRect(Color(0xFF92A9CF).copy(alpha=.12f),
                topLeft=Offset(x+unit*.31f,unit*.8f),
                size=Size(unit*.12f,unit*3f))
        }
        for(i in 0..19) {
            val x=((i*41+run.room*29)%113)/8f*unit
            val y=((i*23+run.room*11)%73)/10f*unit
            drawCircle(Color(0xFFB6D6FC).copy(alpha=.4f),
                radius=unit*.028f,center=Offset(x,y))
        }
        // Native platform-collision coordinates and their physical surfaces.
        drawRect(Color(0xFF172B42),
            topLeft=Offset(0f,yy(MetroidRules.FLOOR)),
            size=Size(size.width,size.height-yy(MetroidRules.FLOOR)))
        for(i in 0..24) {
            val x=xx(i.toFloat())
            drawRect(Color(0xFF2D4459),
                topLeft=Offset(x,yy(MetroidRules.FLOOR)),
                size=Size(unit*.92f,unit*.16f))
        }
        world.platforms.forEach { p->
            drawRect(Color(0xFF172C49),
                topLeft=Offset(xx(p.start),yy(p.y)),
                size=Size((p.end-p.start)*unit,unit*.38f))
            drawRect(Color(0xFF5A7896),
                topLeft=Offset(xx(p.start),yy(p.y)),
                size=Size((p.end-p.start)*unit,unit*.11f))
        }
        world.spikes.forEach { spike->
            var x=spike.start
            while(x<=spike.endInclusive) {
                drawLine(Color(0xFFE888A8),
                    start=Offset(xx(x-.2f),yy(9.98f)),
                    end=Offset(xx(x),yy(9.34f)),strokeWidth=unit*.10f)
                drawLine(Color(0xFFE888A8),
                    start=Offset(xx(x),yy(9.34f)),
                    end=Offset(xx(x+.2f),yy(9.98f)),strokeWidth=unit*.10f)
                x+=.55f
            }
        }
        // Room transitions; some require mobility abilities.
        val gateX=xx(23.5f)
        if(run.room!=5) {
            val blocked=run.room==2 && "dash" !in run.acquired
            drawRect(if(blocked)Color(0xFFCF678D)else Color(0xFF75B9DD),
                topLeft=Offset(gateX,yy(7.3f)),
                size=Size(unit*.22f,unit*2.7f))
            drawCircle(if(blocked)Color(0xFFCF678D)else Color(0xFF75B9DD),
                radius=unit*.23f,
                center=Offset(gateX+unit*.1f,yy(7.2f)))
        }
        if(run.room==1) {
            drawRect(Color(0xFFC2A1EB),
                topLeft=Offset(xx(4.1f),yy(5.9f)),
                size=Size(unit*1.5f,unit*.25f))
            drawCircle(Color(0xFFD7C0FF),radius=unit*.21f,
                center=Offset(xx(5f),yy(5.5f)))
        }
        val item=when {
            run.room==2 && "double" !in run.acquired -> 19f
            run.room==3 && "dash" !in run.acquired -> 19f
            else -> null
        }
        if(item!=null) {
            val bob=(run.elapsedMs%800)/800f*unit*.13f
            drawCircle(Color(0xFFABFFEC),radius=unit*.32f,
                center=Offset(xx(item),yy(8.8f)-bob))
            drawCircle(Color(0xFF223754),radius=unit*.14f,
                center=Offset(xx(item),yy(8.8f)-bob))
        }
        fun drawActor(bitmap:ImageBitmap,x:Float,feet:Float,
                      width:Float,flipped:Boolean=false) {
            val pixel=width*unit
            val xSafe=xx(x).coerceIn(pixel*.52f,size.width-pixel*.52f)
            val dstX=(xSafe-pixel*.5f).roundToInt()
            val dstY=(yy(feet)-pixel*.94f).roundToInt()
            val dst=IntOffset(dstX,dstY)
            val sz=IntSize(pixel.roundToInt().coerceAtLeast(1),
                pixel.roundToInt().coerceAtLeast(1))
            if(flipped) {
                withTransform({
                    scale(scaleX=-1f,scaleY=1f,
                        pivot=Offset(dstX+pixel/2,dstY+pixel/2))
                }) {
                    drawImage(bitmap,srcSize=IntSize(64,64),
                        dstOffset=dst,dstSize=sz,filterQuality=FilterQuality.None)
                }
            } else {
                drawImage(bitmap,srcSize=IntSize(64,64),
                    dstOffset=dst,dstSize=sz,filterQuality=FilterQuality.None)
            }
        }
        if(run.enemyHp>0) {
            val enemyBob=(run.elapsedMs%1100)/1100f*unit*.14f
            drawActor(foe,world.enemyX,
                9.9f-(enemyBob/unit),if(world.boss)3.45f else 2.75f,
                flipped=true)
            drawCircle(Color(0x66F45A80),radius=unit*.18f,
                center=Offset(xx(world.enemyX),yy(9.8f)))
        }
        // The entire hero always remains visible inside the camera viewport.
        if(run.hurtCooldownMs==0 || (run.hurtCooldownMs/90)%2==0) {
            drawActor(hero,run.x,run.y,2.9f,flipped=run.facing<0)
        }
        if(run.attackCooldownMs>85) {
            val side=run.facing.toFloat()
            drawLine(Color(0xFFFDE7A8),
                start=Offset(xx(run.x)+side*unit*.3f,yy(run.y-1.4f)),
                end=Offset(xx(run.x)+side*unit*2.1f,yy(run.y-2.4f)),
                strokeWidth=unit*.14f)
        }
    }
}
