package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.*

private val CGold=Color(0xFFFFD990)
private val CBlue=Color(0xFF91CBFF)
private val CSurface=Color(0xFF17243F)
private val CBorder=Color(0xFF5E7399)

/** A fourth functional tab rather than the former feature-locked placeholder. */
@Composable
fun QuestChallenge(
    state:QuestState,store:MonsterArtworkStore,revision:Int,
    onStartTower:()->Unit,onStartArena:()->Unit,
    onAttack:()->Unit,onGuard:()->Unit,
    onUseSkill:(String)->Unit,onRetreat:()->Unit
) {
    val c=state.challenge
    val active=c.mode!=ChallengeMode.NONE
    var askRetreat by remember { mutableStateOf(false) }
    val pet=remember(state.companion) {
        WildMonster(
            id=state.companion.id,species=state.companion.species,
            element=state.companion.element,
            maxHp=100,attack=10+state.companion.level,
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
            .padding(horizontal=12.dp,vertical=10.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Text("◆ 冒険者の挑戦",color=CGold,
            style=MaterialTheme.typography.headlineSmall,
            fontWeight=FontWeight.Black)
        if(!active) {
            Text("育てた相棒とパーティで2つのモードに挑戦。",
                color=CBlue,style=MaterialTheme.typography.bodyMedium)
            ModeCard(
                title="試練の塔",symbol="♜",tagline="全20階を踏破",
                info="連戦ごとに敵が強化。5階ごとの守護者を倒すと初回限定の召喚結晶。"+
                     "HPは階層をまたいで引き継ぎ、勝利時に16回復します。",
                status="最高 "+c.towerBest+" / 20F　｜　完全踏破 "+c.towerClears+"回",
                accent=Color(0xFFBA9CF2),
                enabled=!state.inBattle,onClick=onStartTower
            )
            ModeCard(
                title="模擬アリーナ",symbol="⚔",tagline="NPCチームとの3連戦",
                info="3体を連続撃破するとレート上昇。育成した最大3匹の"+
                    "出撃パーティが攻撃に参加。初めて昇格したときに結晶を獲得します。",
                status=ChallengeRules.tier(c.arenaRating)+
                    "  "+c.arenaRating+"pt　｜　"+c.arenaWins+"勝 "+c.arenaLosses+"敗",
                accent=Color(0xFF83D5B5),
                enabled=!state.inBattle,onClick=onStartArena
            )
            if(state.inBattle)
                Text("探検の戦闘を終えてから、挑戦モードへ入れます。",
                    color=CGold,style=MaterialTheme.typography.labelMedium)
            Card(
                colors=CardDefaults.cardColors(containerColor=CSurface),
                modifier=Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("挑戦の記録",color=CGold,
                        style=MaterialTheme.typography.titleMedium)
                    Text("塔 初回報酬："+
                        listOf(5,10,15,20).joinToString(" / ") { level ->
                            level.toString()+"F"+
                            (if(level in c.towerMilestones)" ✓" else " ○")
                        },style=MaterialTheme.typography.bodySmall)
                    Text("アリーナ最高 "+c.arenaBestRating+"pt / "+
                        ChallengeRules.tier(c.arenaBestRating),
                        style=MaterialTheme.typography.bodySmall)
                    Text("連勝記録（現在）："+c.arenaStreak,
                        style=MaterialTheme.typography.bodySmall)
                    Text(c.message,color=CBlue,
                        style=MaterialTheme.typography.bodySmall)
                }
            }
            Text("※アリーナは完全オフラインのNPC模擬戦です。"+
                "他プレイヤーとのオンライン対戦・ランキングはありません。",
                color=Color(0xFFBBC9DB),style=MaterialTheme.typography.bodySmall)
        } else {
            val tower=c.mode==ChallengeMode.TOWER
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically
            ) {
                Text(
                    if(tower)"♜ 試練の塔  "+c.towerFloor+"/20F"
                    else "⚔ アリーナ  ROUND "+c.arenaWave+"/3",
                    color=CGold,fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.titleLarge
                )
                Text(if(tower)"BEST "+c.towerBest+"F"
                    else ChallengeRules.tier(c.arenaRating)+" "+c.arenaRating,
                    color=CBlue,style=MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.fillMaxWidth().border(
                2.dp,CGold,RoundedCornerShape(10.dp))
                .background(CSurface,RoundedCornerShape(10.dp))
                .padding(7.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(state.companion.name+" / "+state.companion.level+"Lv",
                                color=CGold,style=MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(
                                progress={c.playerHp/100f},modifier=Modifier.fillMaxWidth(),
                                color=Color(0xFF7CE3AC),
                                trackColor=Color(0xFF38405A))
                            Text("HP "+c.playerHp+"/100",color=Color.White,
                                style=MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                            Text(c.opponent?.species ?: "敵",color=Color.White,
                                style=MaterialTheme.typography.labelMedium,
                                maxLines=1)
                            LinearProgressIndicator(
                                progress={
                                    c.opponentHp.toFloat()/
                                        (c.opponent?.maxHp ?: 1).coerceAtLeast(1)
                                },modifier=Modifier.fillMaxWidth(),
                                color=Color(0xFFF88394),
                                trackColor=Color(0xFF38405A))
                            Text(c.opponentHp.toString()+" / "+
                                (c.opponent?.maxHp ?: 0),color=Color.White,
                                style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(281.dp)
                        .background(Color(0xFF172844),RoundedCornerShape(6.dp))
                        .clickable(onClick=onAttack)) {
                        PixelDungeonBackdrop(
                            floor=if(tower)c.towerFloor+8 else 22+c.arenaWave,
                            boss=c.opponent?.isBoss==true,
                            steps=c.combo,
                            modifier=Modifier.fillMaxSize()
                        )
                        MonsterArtworkView(
                            monster=pet,store=store,revision=revision,
                            modifier=Modifier.size(143.dp)
                                .align(Alignment.BottomStart)
                                .padding(start=4.dp,bottom=18.dp),
                            facingRight=true
                        )
                        c.opponent?.let { enemy ->
                            MonsterArtworkView(
                                monster=enemy,store=store,revision=revision,
                                modifier=Modifier.size(if(enemy.isBoss)163.dp else 143.dp)
                                    .align(Alignment.BottomEnd)
                                    .padding(end=4.dp,bottom=18.dp),
                                facingRight=false
                            )
                        }
                        Text(c.combo.toString()+" HIT",
                            modifier=Modifier.align(Alignment.Center)
                                .offset(y=(-65).dp)
                                .background(Color(0xC4101932),RoundedCornerShape(5.dp))
                                .padding(horizontal=8.dp,vertical=4.dp),
                            color=CGold,fontWeight=FontWeight.Black,
                            style=MaterialTheme.typography.titleLarge)
                        Text(if(c.opponent?.isBoss==true)
                            "守護者の強攻撃まで "+
                                String.format("%.1f",c.attackTimerMs/1000f)+"秒"
                            else "敵の反撃まで "+
                                String.format("%.1f",c.attackTimerMs/1000f)+"秒",
                            modifier=Modifier.align(Alignment.TopCenter)
                                .padding(top=7.dp)
                                .background(Color(0xE01A2340),RoundedCornerShape(5.dp))
                                .padding(horizontal=8.dp,vertical=5.dp),
                            color=CGold,style=MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Card(colors=CardDefaults.cardColors(containerColor=CSurface)) {
                Text(c.message,Modifier.fillMaxWidth().padding(12.dp),
                    color=Color(0xFFF4E6CD),
                    style=MaterialTheme.typography.bodyMedium)
            }
            Button(onClick=onAttack,Modifier.fillMaxWidth().height(65.dp),
                shape=RoundedCornerShape(12.dp),
                colors=ButtonDefaults.buttonColors(
                    containerColor=Color(0xFFBC903D),
                    contentColor=Color(0xFF151B2C))) {
                Text("⚔  タップ連打で攻撃  ⚔",
                    fontWeight=FontWeight.Black,
                    style=MaterialTheme.typography.titleLarge)
            }
            PartySkillButtons(
                state,c.skillCooldowns,enabled=true,onUse=onUseSkill)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick=onGuard,modifier=Modifier.weight(1f),
                    enabled=!c.guardActive && c.guardCooldownMs==0) {
                    Text(if(c.guardActive)"障壁展開中"
                        else if(c.guardCooldownMs>0)
                            "障壁 "+String.format("%.1f",c.guardCooldownMs/1000f)+"秒"
                        else "◇ 守護障壁")
                }
                OutlinedButton(onClick={askRetreat=true},
                    modifier=Modifier.weight(1f)) {
                    Text("帰還・棄権")
                }
            }
            Text(
                if(tower)"塔はHPを引き継ぐ連戦。5Fごとに初回報酬。20F踏破で完全攻略！"
                else "NPCの3体に勝利するとレート上昇、敗北するとレート低下。",
                color=CBlue,style=MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
    }
    if(askRetreat && active) {
        AlertDialog(
            onDismissRequest={askRetreat=false},
            title={Text(if(c.mode==ChallengeMode.TOWER)"塔から帰還しますか？"
                       else "模擬アリーナを棄権しますか？")},
            text={Text(if(c.mode==ChallengeMode.TOWER)
                "今回の塔挑戦は終了します。到達記録と獲得済み報酬は残ります。"
                else "棄権は敗北扱いになり、レートが下がります。")},
            confirmButton={
                TextButton(onClick={askRetreat=false;onRetreat()}) {
                    Text("終了する")
                }
            },
            dismissButton={
                TextButton(onClick={askRetreat=false}) {Text("戻る")}
            }
        )
    }
}

@Composable
private fun ModeCard(
    title:String,symbol:String,tagline:String,info:String,status:String,
    accent:Color,enabled:Boolean,onClick:()->Unit
) {
    Card(
        modifier=Modifier.fillMaxWidth().border(
            1.dp,accent,RoundedCornerShape(11.dp)),
        colors=CardDefaults.cardColors(containerColor=CSurface)
    ) {
        Column(Modifier.padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(58.dp).background(Color(0xFF2D385E),
                    RoundedCornerShape(10.dp)),
                    contentAlignment=Alignment.Center) {
                    Text(symbol,color=accent,
                        style=MaterialTheme.typography.headlineMedium)
                }
                Column {
                    Text(title,color=CGold,fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.titleLarge)
                    Text(tagline,color=accent,
                        style=MaterialTheme.typography.labelLarge)
                }
            }
            Text(info,color=Color.White,
                style=MaterialTheme.typography.bodyMedium)
            Text(status,color=CBlue,
                style=MaterialTheme.typography.labelMedium)
            Button(onClick=onClick,enabled=enabled,
                modifier=Modifier.fillMaxWidth(),
                colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF3F5282))) {
                Text(title+"に挑戦")
            }
        }
    }
}
