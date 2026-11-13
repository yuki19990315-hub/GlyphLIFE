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
    HOME("ホーム","⌂"), EXPLORE("探検","⚔"),
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
    onBuyUpgrade:(String)->Unit,
    onBuySupply:(String)->Unit,
    onClaimAchievement:(String)->Unit,
    management:@Composable ()->Unit
) {
    var currentPanel by remember { mutableIntStateOf(0) }
    val companion=remember(state.companion) {
        WildMonster(
            id=state.companion.id,species=state.companion.species,
            element=state.companion.element,maxHp=100,
            attack=10+state.companion.level,
            artFamily=state.companion.gameArtFamily(),
            stage=when {
                state.companion.level>=16 -> 3
                state.companion.level>=7 -> 2
                else -> 1
            }
        )
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF091126))
            .verticalScroll(rememberScrollState())
            .padding(horizontal=10.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(253.dp)
            .border(2.dp,Color(0xFFBBA16E),RoundedCornerShape(10.dp))
            .background(Color(0xFF14253C),RoundedCornerShape(10.dp))) {
            PixelDungeonBackdrop(1,false,0,Modifier.fillMaxSize().padding(3.dp))
            MonsterArtworkView(companion,store,revision,
                Modifier.size(174.dp).align(Alignment.BottomStart)
                    .padding(start=12.dp,bottom=10.dp),facingRight=true)
            Column(
                modifier=Modifier.align(Alignment.TopCenter)
                    .background(Color(0xC90B1830),RoundedCornerShape(6.dp))
                    .padding(horizontal=10.dp,vertical=5.dp),
                horizontalAlignment=Alignment.CenterHorizontally
            ) {
                Text("GLYPH LIFE",color=Color(0xFFB2E6F6),
                    style=MaterialTheme.typography.labelMedium)
                Text("MONSTER QUEST",color=Gold,
                    fontWeight=FontWeight.Black,
                    style=MaterialTheme.typography.titleLarge)
            }
            Column(
                modifier=Modifier.align(Alignment.BottomEnd)
                    .padding(end=8.dp,bottom=11.dp)
                    .background(Color(0xCB101C32),RoundedCornerShape(7.dp))
                    .padding(horizontal=9.dp,vertical=7.dp),
                horizontalAlignment=Alignment.End
            ) {
                Text(state.companion.name,color=Gold,
                    fontWeight=FontWeight.Bold)
                Text("LV "+state.companion.level+"  HP "+state.playerHp+"/100",
                    color=Color.White,style=MaterialTheme.typography.labelMedium)
                Text("EXP "+state.companionXp+"/"+
                    (if(state.companion.level>=GrowthRules.LEVEL_CAP)"MAX"
                     else GrowthRules.xpNeeded(state.companion.level).toString()),
                    color=Gold,style=MaterialTheme.typography.labelSmall)
                Text("最高 "+state.tapRun.bestFloor+"F",
                    color=Blue,style=MaterialTheme.typography.labelSmall)
            }
        }
        val shortcuts=listOf(
            "♟" to "パーティ", "⚔" to "装備", "◈" to "アイテム",
            "▤" to "図鑑", "✦" to "進化", "★" to "育成"
        )
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                repeat(3) { col ->
                    val id=row*3+col
                    val (symbol,title)=shortcuts[id]
                    OutlinedButton(
                        onClick={currentPanel=id},
                        modifier=Modifier.weight(1f).height(75.dp),
                        contentPadding=PaddingValues(3.dp),
                        shape=RoundedCornerShape(7.dp),
                        border=androidx.compose.foundation.BorderStroke(
                            if(currentPanel==id)2.dp else 1.dp,
                            if(currentPanel==id)Gold else Color(0xFF526B91)),
                        colors=ButtonDefaults.outlinedButtonColors(
                            containerColor=if(currentPanel==id)Color(0xFF344569)
                                else Color(0xFF1A2945))
                    ) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.spacedBy(1.dp)) {
                            Text(symbol,color=if(currentPanel==id)Gold else Blue,
                                style=MaterialTheme.typography.headlineSmall)
                            Text(title,color=Color.White,
                                style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        when(currentPanel) {
            0 -> {
                SectionTitle("パーティ詳細　"+state.partyIds.size+"/3")
                Text("仲間をタップして編成を変更。戦闘時は選んだ仲間が援護します。",
                    color=Blue,style=MaterialTheme.typography.labelMedium)
                if(state.captures.isEmpty())
                    Text("まだ仲間がいません。探索で捕獲するか、召喚してください。")
                else state.captures.forEach { monster ->
                    val selected=monster.id in state.partyIds
                    Card(Modifier.fillMaxWidth().clickable {onSelectParty(monster.id)},
                        colors=CardDefaults.cardColors(containerColor=Color(0xFF1A2944))) {
                        Row(Modifier.padding(9.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            MonsterArtworkView(monster,store,revision,Modifier.size(86.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(monster.species,style=MaterialTheme.typography.titleMedium)
                                Text("Lv."+monster.level+" / 進化"+monster.stage+
                                    "  ATK "+monster.attack+"   "+monster.element,
                                    style=MaterialTheme.typography.bodySmall)
                                Text(if(selected)"✓ 出撃中" else "＋ タップで編成",
                                    color=if(selected)Gold else Blue)
                            }
                        }
                    }
                }
            }
            1,4 -> {
                SectionTitle(if(currentPanel==1)"装備・素材工房" else "進化・合成")
                Text("仲間の装備や進化を編集できます。原画は64×64ドットの全身素材。",
                    color=Blue,style=MaterialTheme.typography.bodySmall)
                management()
            }
            2 -> {
                SectionTitle("所持アイテム")
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    ItemPlate("◆","召喚結晶",state.summonStones,Modifier.weight(1f))
                    ItemPlate("◉","捕獲玉",state.captureOrbs,Modifier.weight(1f))
                    ItemPlate("✚","回復薬",state.potions,Modifier.weight(1f))
                }
                Text("所持金 "+state.tapRun.gold+" G",
                    color=Gold,style=MaterialTheme.typography.titleMedium)
                Text("召喚結晶は宝箱・稀な戦利品・ボス撃破で獲得できます。",
                    color=Blue,style=MaterialTheme.typography.bodySmall)
            }
            3 -> {
                SectionTitle("モンスター図鑑　発見"+state.catalog.size+"種")
                Text("AI生成で登録した種族も、以後の探索に出現します。",
                    color=Blue,style=MaterialTheme.typography.bodySmall)
                val all=state.catalog.takeLast(150)
                if(all.isEmpty()) Text("まだ発見したモンスターがいません。")
                else all.chunked(3).forEach { group ->
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        group.forEach { monster ->
                            Card(Modifier.weight(1f),
                                colors=CardDefaults.cardColors(
                                    containerColor=Color(0xFF19243D))) {
                                Column(Modifier.fillMaxWidth().padding(5.dp),
                                    horizontalAlignment=Alignment.CenterHorizontally) {
                                    MonsterArtworkView(monster,store,revision,
                                        Modifier.size(88.dp))
                                    Text(monster.species,maxLines=1,
                                        style=MaterialTheme.typography.labelSmall)
                                    Text(monster.element+" / 進化"+monster.stage,
                                        style=MaterialTheme.typography.labelSmall,
                                        color=Blue)
                                }
                            }
                        }
                        repeat(3-group.size){Spacer(Modifier.weight(1f))}
                    }
                }
            }
            5 -> {
                SectionTitle("育成・長期チャレンジ")
                Text("探索で敵を倒すと相棒と出撃中の仲間が経験値を獲得。" +
                    " 相棒はLv7／16で姿が変化。仲間は個別のLvと勝利数で進化できる。",
                    color=Blue,style=MaterialTheme.typography.bodySmall)
                Text("相棒 Lv."+state.companion.level+
                    "　EXP "+state.companionXp+"/"+GrowthRules.xpNeeded(state.companion.level),
                    color=Gold,style=MaterialTheme.typography.titleMedium)
                if(state.companion.level<GrowthRules.LEVEL_CAP)
                    LinearProgressIndicator(
                        progress={
                            state.companionXp.toFloat()/
                                GrowthRules.xpNeeded(state.companion.level).coerceAtLeast(1)
                        },
                        modifier=Modifier.fillMaxWidth(),color=Color(0xFF79D5EA))
                SectionTitle("恒久強化・ショップ")
                Text("所持金 "+state.tapRun.gold+"G。全滅しても修練効果と所持金は維持されます。",
                    color=Gold,style=MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick={onBuyUpgrade("attack")},
                    enabled=state.attackUpgrade<GrowthRules.UPGRADE_CAP &&
                        state.tapRun.gold>=GrowthRules.upgradeCost(state.attackUpgrade),
                    modifier=Modifier.fillMaxWidth()
                ) { Text("攻撃修練 Lv."+state.attackUpgrade+
                    "（連打威力 +"+(state.attackUpgrade*2)+
                    "）　"+GrowthRules.upgradeCost(state.attackUpgrade)+"G") }
                OutlinedButton(
                    onClick={onBuyUpgrade("defense")},
                    enabled=state.defenseUpgrade<GrowthRules.UPGRADE_CAP &&
                        state.tapRun.gold>=GrowthRules.upgradeCost(state.defenseUpgrade),
                    modifier=Modifier.fillMaxWidth()
                ) { Text("守護修練 Lv."+state.defenseUpgrade+
                    "（被ダメージ -"+state.defenseUpgrade+
                    "）　"+GrowthRules.upgradeCost(state.defenseUpgrade)+"G") }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={onBuySupply("potion")},
                        enabled=state.tapRun.gold>=35&&state.potions<99,
                        modifier=Modifier.weight(1f)) {
                        Text("回復薬 35G")
                    }
                    OutlinedButton(onClick={onBuySupply("orb")},
                        enabled=state.tapRun.gold>=45&&state.captureOrbs<99,
                        modifier=Modifier.weight(1f)) {
                        Text("捕獲玉 45G")
                    }
                }
                SectionTitle("実績と召喚結晶")
                Text("一度だけ受け取れる挑戦報酬。高階層・進化・収集を目指そう。",
                    color=Blue,style=MaterialTheme.typography.bodySmall)
                GrowthRules.achievements.forEach { goal ->
                    val claimed=goal.id in state.achievementsClaimed
                    val ready=goal.ready(state)
                    Card(Modifier.fillMaxWidth(),
                        colors=CardDefaults.cardColors(containerColor=Color(0xFF1A2944))) {
                        Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(goal.title,color=Gold,
                                    style=MaterialTheme.typography.titleSmall)
                                Text(goal.target+"　報酬：結晶+"+goal.reward,
                                    style=MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick={onClaimAchievement(goal.id)},
                                enabled=ready&&!claimed,
                                contentPadding=PaddingValues(horizontal=10.dp)
                            ) { Text(if(claimed)"受取済" else if(ready)"受取" else "未達成") }
                        }
                    }
                }
                SectionTitle("冒険記録")
                Text("撃破："+state.defeated+"　BOSS討伐："+state.bossWins+
                    "　仲間："+state.captures.size)
                Text("最高："+state.tapRun.bestFloor+"F　歩数："+state.tapRun.distance+
                    "　リトライ："+state.tapRun.deaths)
            }
        }
        Spacer(Modifier.height(6.dp))
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
        val sx=size.width/160f
        val sy=size.height/104f
        fun px(x:Int,y:Int,w:Int,h:Int,c:Color) {
            if(w>0 && h>0)drawRect(
                c,Offset(x*sx,y*sy),Size(w*sx,h*sy))
        }
        fun tree(anchor:Int,shade:Int) {
            val x=((anchor-steps*2)%200+200)%200-20
            px(x+12,24,7,65,Color(0xFF493F46))
            px(x+14,37,3,50,Color(0xFF896B52))
            val greens=if(shade==0)listOf(
                Color(0xFF205B48),Color(0xFF397D55),Color(0xFF6CAF62),
                Color(0xFFB8DC75)) else listOf(
                Color(0xFF1B423B),Color(0xFF346E4D),Color(0xFF579D64),
                Color(0xFF9CC96B))
            for(row in 0..4) for(col in 0..4) {
                val sizePx=9+((row*5+col*7)%7)
                val dx=x+col*9-(if(row%2==0)8 else 12)
                val dy=5+row*9+((col*3)%5)
                px(dx,dy,sizePx,sizePx,greens[(row+col)%4])
                px(dx+2,dy+1,3,2,Color(0xFFB0E889))
            }
        }
        when(((floor-1)/5)%3) {
            0 -> { // Bright enchanted forest, distant castle and waterfall.
                px(0,0,160,104,Color(0xFF92CFF3))
                px(0,0,160,11,Color(0xFF6CA6DA))
                for(x in 0..160 step 40) {
                    val dx=(x-steps+160)%160
                    px(dx,12,23,4,Color(0xFFE5F4F7))
                    px(dx+5,8,14,4,Color(0xFFF6FFF6))
                    px(dx+3,16,30,3,Color(0xFFC4E6EE))
                }
                // Blue mountain ranges and sunny distant fortress.
                for(x in 0..156 step 4) {
                    val ridge=24+((x*11+13)%15)
                    px(x,ridge,4,48-ridge,Color(0xFF7DB4C4))
                    px(x,ridge+10,4,36,Color(0xFF668FA9))
                }
                px(99,20,31,36,Color(0xFFCDD9C8))
                px(100,17,8,39,Color(0xFF7B9BAC))
                px(112,10,8,46,Color(0xFF89A8BA))
                px(124,24,8,32,Color(0xFF89A8BA))
                px(112,7,8,4,Color(0xFFE6EAD2))
                px(107,25,2,4,Color(0xFF2B587F))
                px(117,19,2,4,Color(0xFF2B587F))
                px(127,29,2,4,Color(0xFF2B587F))
                px(78,52,78,21,Color(0xFF4D9B97))
                px(83,50,28,24,Color(0xFFB2D6C9))
                for(i in 0..4) {
                    px(89+i*4,50,2,36,if(i%2==0)
                        Color(0xFFEBF6F1) else Color(0xFF80CFDF))
                }
                px(0,59,160,25,Color(0xFF75B75F))
                px(0,63,160,5,Color(0xFF3D8E64))
                tree(-8,0); tree(143,1)
                for(i in 0..30) {
                    val x=(i*29+steps*2)%160
                    px(x,69+(i*13)%10,5+(i%3),2,
                        if(i%3==0)Color(0xFFE4E692)
                        else Color(0xFF307D55))
                }
                // Golden walkable path with dotted stones and grass edges.
                px(0,82,160,22,Color(0xFFC4B37B))
                px(0,79,160,4,Color(0xFF79A861))
                for(y in 85..102 step 6) for(x in 0..159 step 17) {
                    px((x+steps*4)%160,y,11,2,Color(0xFF9F936E))
                    px((x+steps*4+4)%160,y-1,5,1,Color(0xFFE0D39A))
                }
            }
            1 -> { // Purple moonlit castle ruins.
                px(0,0,160,104,Color(0xFF353960))
                px(0,0,160,26,Color(0xFF26294C))
                px(116,9,20,20,Color(0xFFC1C7D9))
                px(122,8,20,18,Color(0xFF353960))
                for(x in 0..156 step 4) {
                    val h=32+(x*5)%14
                    px(x,h,4,84-h,Color(0xFF444A69))
                }
                for(i in 0..5) {
                    val x=(9+i*35-steps*2+210)%190-15
                    px(x,31,11,54,Color(0xFF77718A))
                    px(x-4,28,19,6,Color(0xFF988DA0))
                    px(x+2,36,6,42,Color(0xFF514865))
                    for(k in 0..5)px(x+2,38+k*7,5,2,Color(0xFF8E8293))
                    px(x,24,3,7,Color(0xFFB9A4C3))
                }
                px(0,78,160,26,Color(0xFF594B63))
                for(y in 82..103 step 7)for(x in 0..156 step 16) {
                    px((x+steps*3)%160,y,12,2,Color(0xFF827487))
                    px((x+steps*3)%160,y+4,5,1,Color(0xFF443F57))
                }
                for(i in 0..8) px((i*27+steps*3)%160,16+(i*17)%57,2,2,
                    Color(0xFFBBA2F3))
            }
            else -> { // Luminous blue crystal cave.
                px(0,0,160,104,Color(0xFF182B52))
                for(x in 0..156 step 4) {
                    val ridge=18+(x*7)%16
                    px(x,ridge,4,82-ridge,Color(0xFF24436A))
                }
                for(i in 0..9) {
                    val x=(i*23-steps*3+210)%180-10
                    val length=13+i%5*5
                    px(x,0,12,length,Color(0xFF2E6484))
                    px(x+3,5,6,length+9,Color(0xFF549DBE))
                    px(x+5,6,2,length-3,Color(0xFF9AE4EC))
                    px(x+4,61+i%4*3,9,29,Color(0xFF35749B))
                    px(x+5,62+i%4*3,3,22,Color(0xFF86DBE8))
                }
                px(0,82,160,22,Color(0xFF3E5A72))
                for(x in 0..159 step 11)px((x+steps*2)%160,87+(x%9),7,2,
                    Color(0xFF4E8A9D))
            }
        }
        if(boss) {
            // Boss arena adds violet warning pillars, without obscuring players.
            px(0,81,160,3,Color(0xFF9B5B9D))
            for(i in 0..5) {
                val x=(i*37+11)%154
                px(x,13+(i*17)%39,3,7,Color(0xFFDB76C0))
                px(x+1,9+(i*17)%39,1,4,Color(0xFFF5C9F0))
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
                state.companion.level>=16 -> 3
                state.companion.level>=7 -> 2
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
                "✧ 探検  "+run.floor+"F",
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

/** Small offline pixel shrine art for the summon tab; no bundled stock image. */
@Composable
private fun SummonPortalScene(modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val sx=size.width/160f
        val sy=size.height/95f
        fun p(x:Int,y:Int,w:Int,h:Int,c:Color) {
            if(w>0&&h>0)drawRect(c,Offset(x*sx,y*sy),Size(w*sx,h*sy))
        }
        p(0,0,160,95,Color(0xFF191832))
        p(0,0,160,15,Color(0xFF29274F))
        for(y in 10..84 step 12)for(x in 0..159 step 21) {
            p((x+if(y%24==0)10 else 0)%160,y,18,9,Color(0xFF29264B))
            p((x+if(y%24==0)10 else 0)%160,y,17,1,Color(0xFF3E3963))
        }
        for(x in listOf(10,136)) {
            p(x,13,14,67,Color(0xFF3A3A65))
            p(x-4,11,22,6,Color(0xFF67638B))
            p(x-5,76,24,6,Color(0xFF656384))
            p(x+4,20,4,49,Color(0xFF171F48))
            p(x+5,26,2,21,Color(0xFF9974D0))
        }
        p(0,81,160,14,Color(0xFF303055))
        for(x in 20..130 step 9) {
            p(x,83,7,2,Color(0xFF7468A4))
            p(x+3,87,2,3,Color(0xFFAD88F5))
        }
        // Concentric rune circle formed with square segments.
        p(41,74,78,3,Color(0xFF8C64EC))
        p(48,70,64,2,Color(0xFFB390FF))
        p(56,67,48,2,Color(0xFF544FDD))
        p(64,64,32,2,Color(0xFFAF9CFF))
        p(52,79,56,2,Color(0xFFD0A9FF))
        // Central levitating diamond, true pixel-aligned stepped geometry.
        for(y in 0..42) {
            val width=if(y<20)1+y/3 else 1+(42-y)/3
            val px0=80-width
            val py0=16+y
            p(px0-2,py0,2+width*2+2,1,Color(0xFF6A38C6))
            p(px0,py0,width*2+1,1,
                if(y%5<2)Color(0xFFD9B7FF) else Color(0xFF9A6AF0))
            p(79,py0,2,1,Color(0xFFE5FBFF))
        }
        for(i in 0..15) {
            val x=(i*29+13)%150+5
            val y=(i*17+7)%58+5
            p(x,y,2,2,if(i%3==0)Color(0xFFE8C6FF) else Color(0xFF7958C6))
        }
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
        Box(Modifier.fillMaxWidth().height(228.dp)
            .border(2.dp,Color(0xFFAA8EDB),RoundedCornerShape(10.dp))
            .background(Color(0xFF181733),RoundedCornerShape(10.dp))) {
            SummonPortalScene(Modifier.fillMaxSize().padding(4.dp))
            Text("◆ 召喚結晶 ×"+state.summonStones,
                modifier=Modifier.align(Alignment.TopCenter).padding(top=12.dp)
                    .background(Color(0xCA141530),RoundedCornerShape(6.dp))
                    .padding(horizontal=12.dp,vertical=4.dp),
                color=Gold,style=MaterialTheme.typography.titleMedium)
            Text("クリスタルの祭壇",
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=10.dp)
                    .background(Color(0xCA131428),RoundedCornerShape(6.dp))
                    .padding(horizontal=9.dp,vertical=4.dp),
                color=Color.White,style=MaterialTheme.typography.labelMedium)
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
