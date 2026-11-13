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

/** Each member has its own action and per-encounter tap cooldown. */
@Composable
fun PartySkillButtons(
    state:QuestState,cooldowns:Map<String,Int>,
    enabled:Boolean,onUse:(String)->Unit
) {
    val members=SkillRules.party(state)
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("✦ 固有スキル",
            color=RelicGold,style=MaterialTheme.typography.titleSmall)
        Text("ボタンで発動。再使用には通常攻撃が必要。"+
            " 編成によって使えるスキルが変わります。",
            color=RelicBlue,style=MaterialTheme.typography.labelSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)
        ) {
            members.forEach { monster ->
                val skill=SkillRules.forMonster(monster)
                val remaining=cooldowns[monster.id] ?: 0
                OutlinedButton(
                    onClick={onUse(monster.id)},
                    enabled=enabled && remaining==0,
                    modifier=Modifier.widthIn(min=124.dp)
                ) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(monster.species,maxLines=1,
                            style=MaterialTheme.typography.labelSmall)
                        Text(skill.name,
                            style=MaterialTheme.typography.labelMedium,
                            fontWeight=FontWeight.Bold)
                        Text(if(remaining==0)"使用可能" else "あと"+remaining+"打",
                            color=if(remaining==0)RelicGold else RelicBlue,
                            style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
