package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.*
import kotlin.math.roundToInt

private val Ink=Color(0xFF0F172A)
private val Gold=Color(0xFFFFD990)
private val Blue=Color(0xFF91CBFF)

enum class QuestTab(val label:String,val symbol:String) {
    HOME("ホーム","⌂"), EXPLORE("探索","⚔"),
    SUMMON("召喚","✦"), FUTURE("？","♧")
}

@Composable
fun QuestBottomNavigation(tab:QuestTab,onTab:(QuestTab)->Unit) {
    NavigationBar(containerColor=Color(0xFF131D33),tonalElevation=5.dp) {
        QuestTab.entries.forEach { item ->
            NavigationBarItem(
                selected=tab==item,
                onClick={onTab(item)},
                icon={ Text(item.symbol,style=MaterialTheme.typography.titleLarge) },
                label={Text(item.label)},
                colors=NavigationBarItemDefaults.colors(
                    selectedIconColor=Gold, selectedTextColor=Gold,
                    indicatorColor=Color(0xFF3E4266),
                    unselectedIconColor=Color(0xFFABB8CC),
                    unselectedTextColor=Color(0xFFABB8CC))
            )
        }
    }
}

@Composable
fun QuestHome(
    state:QuestState,store:MonsterArtworkStore,revision:Int,
    onSelectParty:(String)->Unit,
    management:@Composable ()->Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Text("冒険者の拠点",style=MaterialTheme.typography.headlineSmall,
            color=Gold,fontWeight=FontWeight.Bold)
        val companion=remember(state.companion) {
            WildMonster(
                id=state.companion.id,species=state.companion.species,
                element=state.companion.element,maxHp=100,
                attack=10+state.companion.level,
                artFamily=state.companion.gameArtFamily(),
                stage=when {
                    state.companion.level>=12 -> 3
                    state.companion.level>=5 -> 2
                    else -> 1
                }
            )
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                MonsterArtworkView(companion,store,revision,Modifier.size(118.dp))
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(state.companion.name,style=MaterialTheme.typography.titleLarge)
                    Text(state.companion.species+" / "+state.companion.element+
                        "　Lv."+state.companion.level)
                    Text("HP "+state.playerHp+"/100　最高 "+state.tapRun.bestFloor+"F")
                    Text("総撃破 "+state.defeated+" / 捕獲 "+state.captures.size,
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        SectionTitle("アイテム・所持品")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ItemPlate("✦", "召喚結晶",state.summonStones,Modifier.weight(1f))
            ItemPlate("◉", "捕獲玉",state.captureOrbs,Modifier.weight(1f))
            ItemPlate("✚", "回復薬",state.potions,Modifier.weight(1f))
        }
        Text("所持金： "+state.tapRun.gold+" G　｜　探索総歩数："+
            state.tapRun.distance+"　｜　リトライ："+state.tapRun.deaths,
            style=MaterialTheme.typography.bodySmall)
        SectionTitle("パーティ（最大3匹）")
        Text("選んだ仲間は探索中のタップ攻撃を強化します。もう一度押すと解除できます。",
            style=MaterialTheme.typography.bodySmall)
        if(state.captures.isEmpty()) {
            Text("探索で捕獲するか、召喚で仲間を入手してください。")
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                state.captures.forEach { monster ->
                    val selected=monster.id in state.partyIds
                    ElevatedCard(Modifier.width(124.dp).clickable { onSelectParty(monster.id) }) {
                        Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            MonsterArtworkView(monster,store,revision,Modifier.size(90.dp))
                            Text(monster.species,maxLines=1,
                                style=MaterialTheme.typography.labelLarge)
                            Text("進化"+monster.stage+" / ATK "+monster.attack,
                                style=MaterialTheme.typography.labelSmall)
                            Text(if(selected)"✓ 出撃中" else "＋ 編成する",
                                color=if(selected)Gold else Blue,
                                style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        SectionTitle("図鑑")
        Text("発見 "+state.catalog.size+"種 / 仲間 "+state.captures.size+"匹。" +
            " AI召喚で登録した種族は、探索中に低確率で出現します。",
            style=MaterialTheme.typography.bodyMedium)
        if(state.catalog.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                state.catalog.takeLast(16).asReversed().forEach { monster ->
                    Column(Modifier.width(96.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        MonsterArtworkView(monster,store,revision,Modifier.size(86.dp))
                        Text(monster.species,maxLines=1,
                            style=MaterialTheme.typography.labelSmall)
                        if(monster.lineage=="ai-summoned")
                            Text("AI登録",color=Gold,
                                style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        SectionTitle("装備・進化・合成")
        Text("仲間を選んで、頭・肩・背中・尾・翼の装備パーツを編集できます。",
            style=MaterialTheme.typography.bodySmall)
        management()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ItemPlate(symbol:String,name:String,count:Int,modifier:Modifier=Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical=12.dp,horizontal=3.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Text(symbol,color=Gold,style=MaterialTheme.typography.headlineSmall)
            Text(name,style=MaterialTheme.typography.labelSmall)
            Text("×"+count,style=MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SectionTitle(title:String) {
    Text(title,style=MaterialTheme.typography.titleMedium,
        fontWeight=FontWeight.Bold,color=Gold)
}

/**
 * Tap-to-walk dungeon with three thematic pixel backgrounds:
 * enchanted forest, stone ruins and crystal cavern.
 * Canvas rectangles intentionally snap to a GBA-like low-resolution grid.
 */
@Composable
fun PixelDungeonBackdrop(floor:Int,boss:Boolean,steps:Int=0,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val scaleX=size.width/160f
        val scaleY=size.height/104f
        fun px(x:Int,y:Int,w:Int,h:Int,color:Color) {
            drawRect(color,Offset(x*scaleX,y*scaleY),
                Size(w*scaleX,h*scaleY))
        }
        val mode=((floor-1)/5)%3
        val sky=when(mode) {
            0 -> Color(0xFF24395B)
            1 -> Color(0xFF332F56)
            else -> Color(0xFF182E4C)
        }
        px(0,0,160,104,sky)
        for(y in 0..56 step 8) px(0,y,160,4,
            when(mode){0->Color(0xFF2D4F69);1->Color(0xFF3D3B61);else->Color(0xFF254468)})
        // Far parallax mountains: deterministic steps rather than smooth paths.
        for(x in 0..159 step 4) {
            val rise=((x+steps*5)*7+floor*11)%24
            px(x,31+rise,4,62-rise,
                if(mode==0)Color(0xFF1C3A4C) else Color(0xFF272F4D))
        }
        if(mode==0) {
            // Forest: silhouettes of angular pines and moss-covered trunks.
            for(baseX in 4..156 step 31) {
                val x=(baseX-steps*5+160)%160
                px(x,25,5,65,Color(0xFF1A2E36))
                for(k in 0..3) {
                    val w=16-k*3
                    px(x+2-w/2,18+k*9,w,8,
                        if(k%2==0)Color(0xFF264D3B) else Color(0xFF315943))
                }
            }
            px(13,8,9,9,Color(0xFFE3CF8E))
        } else if(mode==1) {
            // Ruins: crenellations, columns, and broken arches.
            for(baseX in 2..155 step 35) {
                val x=(baseX-steps*3+160)%160
                px(x,29,9,62,Color(0xFF46445B))
                px(x-3,27,15,6,Color(0xFF6D657A))
                for(y in 35..81 step 9)px(x+2,y,5,1,Color(0xFF777083))
            }
            px(94,7,13,13,Color(0xFFB5A9CF))
        } else {
            // Crystal cavern: large clustered polygonal stalactites.
            for(baseX in 8..150 step 27) {
                val x=(baseX-steps*4+160)%160
                px(x,0,10,18+(x%17),Color(0xFF385E81))
                px(x+3,12,4,17+(x%9),Color(0xFF6CB5D8))
                px(x+6,63,7,23,Color(0xFF306680))
                px(x+8,68,3,13,Color(0xFF8BE1EB))
            }
        }
        // Walkable foreground, stone path, alternating low-resolution highlights.
        px(0,83,160,21,if(boss)Color(0xFF443148) else Color(0xFF374C48))
        px(0,82,160,3,if(boss)Color(0xFF89637B) else Color(0xFF789080))
        for(y in 87..102 step 6)for(x in 0..158 step 14) {
            px((x+(if(y%2==0)0 else 7)+steps*3)%160,y,12,1,
                if(boss)Color(0xFF675066) else Color(0xFF597367))
        }
        if(boss) {
            for(y in 10..77 step 7)for(x in 4..150 step 23) {
                if((x+y)%4==0)px(x,y,2,3,Color(0xFFC15C88))
            }
        } else {
            for(y in 12..70 step 13)for(x in 5..152 step 17) {
                if((x+y+floor)%3==0)px(x,y,2,2,Color(0xFFB9D99A))
            }
        }
    }
}

/**
 * Companion-to-enemy battle composition: the player's selected main monster is
 * always on the LEFT facing RIGHT, while the enemy is RIGHT facing LEFT.
 * No human-avatar fallback is drawn.
 */
@Composable
fun TapExplorationScreen(
    state:QuestState,store:MonsterArtworkStore,revision:Int,
    onTap:()->Unit,onDodge:(Int)->Unit,onCapture:()->Unit,
    onHeal:()->Unit,onBranch:(Int)->Unit
) {
    val run=state.tapRun
    val boss=run.phase==TapPhase.BOSS
    val fighting=run.phase==TapPhase.BOSS || run.phase==TapPhase.FIGHT
    val companion=remember(state.companion) {
        WildMonster(
            id=state.companion.id,species=state.companion.species,
            element=state.companion.element,
            artFamily=state.companion.gameArtFamily(),
            bodyType=if(state.companion.species=="精霊") "SPIRIT" else "",
            maxHp=100,attack=10+state.companion.level,
            stage=when {
                state.companion.level>=12 -> 3
                state.companion.level>=5 -> 2
                else -> 1
            }
        )
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF090F24))
            .verticalScroll(rememberScrollState())
            .padding(horizontal=10.dp,vertical=7.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)
    ) {
        // GBA RPG style: compact counters and a framed battle vista.
        Row(
            modifier=Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween
        ) {
            Text(
                "✧ 探索  "+run.floor+"F",
                style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.ExtraBold,color=Gold
            )
            Text("◆ "+state.summonStones+"  ◉ "+state.captureOrbs+
                "   BEST "+run.bestFloor+"F",
                color=Blue,style=MaterialTheme.typography.labelMedium)
        }
        Box(
            Modifier.fillMaxWidth()
                .border(
                    2.dp,Color(0xFFBDA66D),RoundedCornerShape(10.dp))
                .background(Color(0xFF101C34),RoundedCornerShape(10.dp))
                .padding(4.dp)
        ) {
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=3.dp),
                    horizontalArrangement=Arrangement.SpaceBetween
                ) {
                    Text("⚔ "+companion.species+"  Lv."+state.companion.level,
                        style=MaterialTheme.typography.labelMedium,color=Gold)
                    Text(if(fighting)"ENCOUNTER" else "歩数 "+
                        run.steps+"/"+TapAdventure.STEPS_PER_FLOOR,
                        style=MaterialTheme.typography.labelMedium,color=Color.White)
                }
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        LinearProgressIndicator(
                            progress={state.playerHp/100f},
                            modifier=Modifier.fillMaxWidth().height(8.dp),
                            color=Color(0xFF67D49C),
                            trackColor=Color(0xFF283953))
                        Text("HP "+state.playerHp+"/100",
                            color=Color.White,style=MaterialTheme.typography.labelSmall)
                    }
                    if(run.enemy!=null) {
                        Column(Modifier.weight(1f),
                            horizontalAlignment=Alignment.End) {
                            LinearProgressIndicator(
                                progress={run.enemyHp.toFloat()/
                                    run.enemy.maxHp.coerceAtLeast(1)},
                                modifier=Modifier.fillMaxWidth().height(8.dp),
                                color=Color(0xFFF58596),
                                trackColor=Color(0xFF283953))
                            Text(run.enemy.species+"  "+
                                run.enemyHp+"/"+run.enemy.maxHp,
                                maxLines=1,color=Color.White,
                                style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(346.dp)
                        .background(Color(0xFF132742),RoundedCornerShape(6.dp))
                        .clickable(enabled=run.phase!=TapPhase.BRANCH,onClick=onTap)
                ) {
                    PixelDungeonBackdrop(run.floor,boss,run.steps,Modifier.fillMaxSize())
                    // Stage characters are big, full-body 64x64 pixel sprites.
                    MonsterArtworkView(
                        monster=companion,store=store,revision=revision,
                        modifier=Modifier.size(169.dp)
                            .align(Alignment.BottomStart)
                            .offset(
                                x=(if(fighting)run.combo%2*9 else run.steps%2*4).dp,
                                y=(if(run.steps%2==0)0 else -3).dp
                            )
                            .padding(start=4.dp,bottom=19.dp),
                        facingRight=true
                    )
                    run.enemy?.let { foe ->
                        MonsterArtworkView(
                            monster=foe,store=store,revision=revision,
                            modifier=Modifier.size(if(boss)199.dp else 161.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x=(if(run.combo%2==0)0 else -3).dp)
                                .padding(end=4.dp,bottom=18.dp),
                            facingRight=false
                        )
                    }
                    if(!fighting) {
                        Box(Modifier.align(Alignment.TopCenter)
                            .padding(top=9.dp)
                            .background(Color(0xB9142342),RoundedCornerShape(6.dp))
                            .padding(horizontal=10.dp,vertical=4.dp)) {
                            Text("タップで前進 ▶",
                                color=Gold,style=MaterialTheme.typography.labelLarge)
                        }
                    } else if(boss) {
                        Box(Modifier.align(Alignment.TopCenter).padding(top=8.dp)
                            .background(Color(0xE2201C35),RoundedCornerShape(5.dp))
                            .padding(horizontal=8.dp,vertical=5.dp)) {
                            Text(
                                if(run.weakpointTaps>0)
                                    "WEAK POINT ×"+run.weakpointTaps+"  攻撃3倍"
                                else (if(run.bossTarget<0)"左に攻撃！ 右へ回避 →"
                                      else "← 左へ回避！ 右に攻撃")+
                                    "  "+String.format("%.1f",run.attackTimerMs/1000f)+"秒",
                                color=Gold,style=MaterialTheme.typography.labelMedium
                            )
                        }
                    } else {
                        Box(Modifier.align(Alignment.TopCenter).padding(top=8.dp)
                            .background(Color(0xD51A2340),RoundedCornerShape(5.dp))
                            .padding(horizontal=10.dp,vertical=5.dp)) {
                            Text("敵の次の攻撃まで "+
                                String.format("%.1f",run.attackTimerMs/1000f)+"秒",
                                color=Color.White,
                                style=MaterialTheme.typography.labelMedium)
                        }
                    }
                    if(fighting) {
                        Text(run.combo.toString()+" HIT",
                            Modifier.align(Alignment.Center)
                                .offset(y=(-85).dp)
                                .background(Color(0xB7141A2D),RoundedCornerShape(6.dp))
                                .padding(horizontal=9.dp,vertical=3.dp),
                            color=Gold,fontWeight=FontWeight.Black,
                            style=MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        // Compact battle log. Actions remain above the fixed bottom navigation.
        Box(Modifier.fillMaxWidth()
            .border(
                1.dp,Color(0xFF4E638A),RoundedCornerShape(7.dp))
            .background(Color(0xFF182541),RoundedCornerShape(7.dp))
            .padding(horizontal=12.dp,vertical=9.dp)) {
            Text(run.message,color=Color(0xFFF6E8CB),
                style=MaterialTheme.typography.bodyMedium,
                maxLines=2)
        }
        if(run.phase==TapPhase.BRANCH) {
            Text("◇ 分かれ道：進むルートを選択",
                color=Gold,style=MaterialTheme.typography.titleMedium)
            listOf(
                "✚ 精霊の泉　HP回復",
                "◆ 宝箱の小道　アイテム・結晶",
                "⚔ 危険な近道　強敵・レア報酬"
            ).forEachIndexed { index,title ->
                Button(
                    modifier=Modifier.fillMaxWidth().height(52.dp),
                    onClick={onBranch(index)},
                    colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF283659))
                ) { Text(title,color=Color.White) }
            }
        } else {
            Button(
                onClick=onTap,
                modifier=Modifier.fillMaxWidth().height(68.dp),
                shape=RoundedCornerShape(14.dp),
                colors=ButtonDefaults.buttonColors(
                    containerColor=Color(0xFFBA883A),
                    contentColor=Color(0xFF11182B))
            ) {
                Text(
                    if(fighting)"⚔  連打して攻撃  ⚔" else "▶  タップして前進",
                    style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black
                )
            }
            if(boss) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={onDodge(-1)},modifier=Modifier.weight(1f)) {
                        Text(if(run.lane==-1)"◀ 左：選択中" else "◀ 左へ回避")
                    }
                    OutlinedButton(onClick={onDodge(1)},modifier=Modifier.weight(1f)) {
                        Text(if(run.lane==1)"右：選択中 ▶" else "右へ回避 ▶")
                    }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onHeal,
                    enabled=state.potions>0 && state.playerHp<100,
                    modifier=Modifier.weight(1f)) {
                    Text("✚ 回復 "+state.potions)
                }
                OutlinedButton(onClick=onCapture,
                    enabled=run.phase==TapPhase.FIGHT && state.captureOrbs>0,
                    modifier=Modifier.weight(1f)) {
                    Text("◉ 捕獲 "+state.captureOrbs)
                }
            }
            if(fighting) {
                Text(
                    if(boss)"BOSS：攻撃は時間経過。予告方向の逆に回避して弱点を狙おう"
                    else "タップだけで連続攻撃！ 敵は時間経過で反撃します",
                    style=MaterialTheme.typography.labelMedium,color=Blue
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun QuestSummon(
    state:QuestState,
    aiKey:String,aiDescription:String,aiBusy:Boolean,
    aiArtEnabled:Boolean,onAiArtEnabled:(Boolean)->Unit,
    onAiKey:(String)->Unit,onDescription:(String)->Unit,
    onAiText:()->Unit,onAiPhoto:()->Unit,onOffline:()->Unit,
    studio:@Composable ()->Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("召喚の祭壇",style=MaterialTheme.typography.headlineSmall,
            color=Gold,fontWeight=FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(160.dp)
            .background(Color(0xFF1D1D47),RoundedCornerShape(12.dp)),
            contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("✧",color=Color(0xFFBBA3FF),
                    style=MaterialTheme.typography.displayLarge)
                Text("召喚結晶 ×"+state.summonStones,
                    style=MaterialTheme.typography.titleLarge)
            }
        }
        Text("探索でまれに、ボス撃破で必ず召喚結晶を獲得。" +
            " 召喚成功時だけ結晶を1個消費します。",
            style=MaterialTheme.typography.bodySmall)
        Button(onClick=onOffline,
            enabled=state.summonStones>0 && state.captures.size<100,
            modifier=Modifier.fillMaxWidth()) { Text("✦ オフライン召喚（結晶1個）") }
        Text("オフライン召喚は既存のローカル素材から作る方式で、AI生成ではありません。",
            style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        SectionTitle("AIモンスター召喚（任意）")
        Text("Gemini APIキーが必要です。AIが名前・属性・モチーフを考え、" +
            "本編64×64ドット素材はローカル生成を使用します。" +
            " AI画像を作る場合は別の画像生成APIが必要です。" +
            " 料金・無料枠はGoogleの提供条件によります。",
            style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(value=aiKey,onValueChange=onAiKey,
            modifier=Modifier.fillMaxWidth(),singleLine=true,
            label={Text("Gemini APIキー（保存しません）")},
            visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
        OutlinedTextField(value=aiDescription,onValueChange=onDescription,
            modifier=Modifier.fillMaxWidth(),
            label={Text("召喚したいモンスターのイメージ")},
            placeholder={Text("例：氷結晶をまとった小さな雷竜")})
        Row(verticalAlignment=Alignment.CenterVertically,
            modifier=Modifier.fillMaxWidth()) {
            Checkbox(checked=aiArtEnabled,onCheckedChange=onAiArtEnabled)
            Text("AI画像も生成（別のAPI呼び出し・有料の場合あり）",
                style=MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=onAiText,
                enabled=!aiBusy && aiKey.isNotBlank() && aiDescription.isNotBlank() &&
                    state.summonStones>0 && state.captures.size<100 &&
                    state.tapRun.phase !in listOf(TapPhase.FIGHT,TapPhase.BOSS),
                modifier=Modifier.weight(1f)) { Text("AI設定で召喚") }
            OutlinedButton(onClick=onAiPhoto,
                enabled=!aiBusy && aiKey.isNotBlank() &&
                    state.summonStones>0 && state.captures.size<100,
                modifier=Modifier.weight(1f)) { Text("写真から召喚") }
        }
        if(aiBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("召喚した仲間はホームで編成可能。AI召喚した種族は" +
            "図鑑に登録され、今後の探索にも低確率で登場します。",
            color=Blue,style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        SectionTitle("ドット絵工房")
        Text("外見を試作し、結晶を使って召喚。好きな64×64デザインを確認できます。",
            style=MaterialTheme.typography.bodySmall)
        studio()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun QuestFuture() {
    Box(Modifier.fillMaxSize().background(Ink),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,
            modifier=Modifier.padding(28.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("🔒",style=MaterialTheme.typography.displayLarge)
            Text("未開放エリア",style=MaterialTheme.typography.headlineSmall,
                color=Gold)
            Text("この機能は今後のアップデートで追加予定です。",
                textAlign=TextAlign.Center)
            Text("現在はホーム・探索・召喚をお楽しみください。",
                style=MaterialTheme.typography.bodySmall)
        }
    }
}
