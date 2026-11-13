package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.model.*

private val RelicGold=Color(0xFFFFD990)
private val RelicBlue=Color(0xFF91CBFF)

@Composable
fun BossRelicPanel(
    state:QuestState,
    onEquip:(String,String)->Unit,
    onUnequip:(String)->Unit
) {
    var selectedId by remember(state.companion.id) {
        mutableStateOf(state.companion.id)
    }
    var showAll by remember { mutableStateOf(false) }
    val owners=listOf(state.companion.id to state.companion.name)+
        state.captures.map { it.id to it.species+" Lv"+it.level }
    if(selectedId !in owners.map {it.first})selectedId=state.companion.id
    val current=state.relicEquipment[selectedId]
    val wearing=state.bossRelics.firstOrNull{it.id==current}
    val bonus=RelicRules.stats(state)
    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("♜ ボス限定装備",color=RelicGold,
            style=MaterialTheme.typography.titleMedium,
            fontWeight=FontWeight.Bold)
        Text("探索のBOSS・試練の塔の守護者のみがドロップ。" +
            " 入手率35%、4回連続で出なければ次回確定。" +
            " 1匹につき1装備。出撃中の仲間と相棒の装備のみ戦闘に適用。",
            color=RelicBlue,style=MaterialTheme.typography.bodySmall)
        Text("所持 "+state.bossRelics.size+"/"+RelicRules.INVENTORY_LIMIT+
            "　次の確定まで最大 "+
            (RelicRules.PITY_LIMIT-state.relicDryBosses)+"体",
            style=MaterialTheme.typography.labelMedium)
        Text("現在のチーム効果：攻撃 +"+bonus.attack+
            " / 防御 +"+bonus.defense+" / 会心 +"+bonus.critical+"%"+
            " / 戦闘勝利HP +"+bonus.heal+
            " / スキル再使用加速 +"+bonus.charge,
            color=RelicGold,style=MaterialTheme.typography.bodySmall)

        Text("装備するモンスターを選択",style=MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)
        ) {
            owners.forEach { (id,label) ->
                FilterChip(
                    selected=selectedId==id,
                    onClick={selectedId=id},
                    label={ Text(label,maxLines=1) }
                )
            }
        }
        Card(colors=CardDefaults.cardColors(
            containerColor=Color(0xFF223352))) {
            Row(Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("装備中："+(wearing?.let(RelicRules::relicName) ?: "なし"),
                        color=RelicGold,style=MaterialTheme.typography.bodyMedium)
                    if(wearing!=null)
                        Text(RelicRules.spec(wearing.kind)?.effect.orEmpty(),
                            color=RelicBlue,style=MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick={onUnequip(selectedId)},
                    enabled=wearing!=null && !state.inBattle
                ) {Text("外す")}
            }
        }
        if(state.bossRelics.isEmpty()) {
            Text("まだボス装備を持っていません。"+
                "探検5Fのボス、または試練の塔5Fの守護者から集めよう。",
                color=RelicBlue,style=MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Text("戦利品一覧",style=MaterialTheme.typography.titleSmall)
                TextButton(onClick={showAll=!showAll}) {
                    Text(if(showAll)"最近12件だけ" else "全"+state.bossRelics.size+"件を表示")
                }
            }
            (if(showAll)state.bossRelics else state.bossRelics.takeLast(12))
                .asReversed().forEach { drop ->
                    val spec=RelicRules.spec(drop.kind)
                    val holder=state.relicEquipment.entries.firstOrNull {
                        it.value==drop.id
                    }?.key
                    val holderName=owners.firstOrNull{it.first==holder}?.second
                    Card(colors=CardDefaults.cardColors(
                        containerColor=Color(0xFF192944))) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(8.dp)
                        ) {
                            Box(Modifier.size(43.dp).background(Color(0xFF34416C),
                                RoundedCornerShape(7.dp)),
                                contentAlignment=Alignment.Center) {
                                Text("✦",color=RelicGold,
                                    style=MaterialTheme.typography.titleLarge)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(spec?.name ?: "未知の装備",
                                    color=RelicGold,style=MaterialTheme.typography.titleSmall)
                                Text((spec?.effect ?: "")+" / "+drop.source+
                                    " "+drop.floor+"F",
                                    style=MaterialTheme.typography.labelSmall)
                                if(holderName!=null)
                                    Text("装備中："+holderName,color=RelicBlue,
                                        style=MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick={onEquip(selectedId,drop.id)},
                                enabled=!state.inBattle && current!=drop.id,
                                contentPadding=PaddingValues(horizontal=9.dp)
                            ) {Text(if(current==drop.id)"装備中" else "装着")}
                        }
                    }
                }
        }
    }
}

/** Each captured individual has innate + independently rolled bonus ability. */
@Composable
fun PartySkillButtons(
    state:QuestState,cooldowns:Map<String,Int>,
    enabled:Boolean,onUse:(String,Boolean)->Unit
) {
    val members=SkillRules.party(state)
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("✦ 固有＋ランダムスキル",
            color=RelicGold,style=MaterialTheme.typography.titleSmall)
        Text("各個体2種類。追加スキルは捕獲・召喚時に決まります。"+
            "それぞれ通常攻撃で再使用までのカウントを減らせます。",
            color=RelicBlue,style=MaterialTheme.typography.labelSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            members.forEach { monster ->
                Column(
                    Modifier.width(150.dp)
                        .background(Color(0xFF1B2A47),RoundedCornerShape(9.dp))
                        .padding(7.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(4.dp)
                ) {
                    Text(monster.species,maxLines=1,
                        color=RelicGold,style=MaterialTheme.typography.labelSmall)
                    listOf(false,true).forEach { bonus ->
                        val skill=if(bonus)SkillRules.extraFor(monster)
                            else SkillRules.forMonster(monster)
                        val key=SkillRules.cooldownKey(monster.id,bonus)
                        val left=cooldowns[key]?:0
                        OutlinedButton(
                            onClick={onUse(monster.id,bonus)},
                            enabled=enabled && left==0,
                            modifier=Modifier.fillMaxWidth(),
                            contentPadding=PaddingValues(horizontal=4.dp,vertical=5.dp)
                        ) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                Text((if(bonus)"◆ " else "✦ ")+skill.name,
                                    style=MaterialTheme.typography.labelSmall,
                                    maxLines=1)
                                Text(if(left==0)"使用可能" else "あと"+left+"打",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=if(left==0)RelicGold else RelicBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The 3 independent roll slots are always visible, even when locked. */
@Composable
fun MonsterPotentialPanel(monster:WildMonster) {
    val bonus=SkillRules.extraFor(monster)
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("ランダム追加スキル：◆ "+bonus.name+
            "　"+bonus.detail,
            color=RelicGold,style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            TalentRules.levels.forEachIndexed { index,level ->
                val slot=monster.statSlots.getOrNull(index)
                Card(Modifier.weight(1f),colors=CardDefaults.cardColors(
                    containerColor=if(slot==null)Color(0xFF242B3D)
                    else Color(0xFF344268))) {
                    Column(
                        Modifier.padding(horizontal=3.dp,vertical=6.dp),
                        horizontalAlignment=Alignment.CenterHorizontally
                    ) {
                        Text("SLOT "+(index+1),
                            color=RelicBlue,style=MaterialTheme.typography.labelSmall)
                        Text(if(slot==null)"Lv"+level+"で解放" else TalentRules.label(slot),
                            color=if(slot==null)Color(0xFF97A6BA) else RelicGold,
                            style=MaterialTheme.typography.labelSmall,
                            maxLines=2)
                    }
                }
            }
        }
    }
}
