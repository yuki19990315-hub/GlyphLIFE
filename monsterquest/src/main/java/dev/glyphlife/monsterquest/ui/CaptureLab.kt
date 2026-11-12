package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.FusionRules
import dev.glyphlife.monsterquest.model.EvolutionRules
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.ArmorSlot
import dev.glyphlife.monsterquest.pixel.ArmorStyle
import dev.glyphlife.monsterquest.pixel.MonsterArmor
import dev.glyphlife.monsterquest.pixel.ArmorOffset

/**
 * Two-material fusion screen. A confirmation is required because selected
 * captures are permanently consumed by an offline game operation.
 */
@Composable
fun CaptureLab(
    captured: List<WildMonster>,
    inBattle: Boolean,
    defeated: Int,
    artworkStore: MonsterArtworkStore,
    artworkRevision: Int,
    onEvolve: (String) -> Unit,
    onEquip: (String, Map<String,String>) -> Unit,
    onAiEquip: (WildMonster) -> Unit,
    allowAiEquip: Boolean,
    onGeneratePart: (WildMonster, ArmorSlot) -> Unit,
    onImportPart: (WildMonster, ArmorSlot) -> Unit,
    onRemovePart: (WildMonster, ArmorSlot) -> Unit,
    onMovePart: (WildMonster, ArmorSlot, Int, Int) -> Unit,
    onSendGlyph: (WildMonster) -> Unit,
    onExportPng: (WildMonster, Int) -> Unit,
    onExportSpriteSheet: (WildMonster) -> Unit,
    onFuse: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var firstId by remember { mutableStateOf<String?>(null) }
    var secondId by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var armorEditing by remember { mutableStateOf(false) }
    val first = captured.firstOrNull { it.id == firstId }
    val second = captured.firstOrNull { it.id == secondId }
    val fusionPreview = remember(first, second) {
        if (first != null && second != null && first.id != second.id &&
            !first.isBoss && !second.isBoss)
            FusionRules.preview(first, second)
        else null
    }
    val candidates = if (showAll) captured else captured.takeLast(12)

    HorizontalDivider()
    Text("モンスター研究所", style = MaterialTheme.typography.titleMedium)
    Text("捕獲した仲間は冒険で1戦につき1回援護でき、2匹を合成して新しい仲間も作れます。" +
        "合成時は選んだ2匹が消費されます。",
        style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "研究所を閉じる" else "捕獲図鑑・合成を開く（${captured.size}/100）")
    }
    if (expanded) {
        if (captured.isEmpty()) {
            Text("モンスターを捕獲すると、ここで図鑑と合成が使えます。")
        } else {
            if (captured.size > 12) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "最近12匹だけ表示" else "すべての仲間を表示")
                }
            }
            Text("素材Aを選択")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                candidates.forEach { monster ->
                    FilterChip(
                        selected = monster.id == firstId,
                        onClick = { firstId = monster.id },
                        label = { Text(monster.species) }
                    )
                }
            }
            Text("素材Bを選択")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                candidates.forEach { monster ->
                    FilterChip(
                        selected = monster.id == secondId,
                        onClick = { secondId = monster.id },
                        label = { Text(monster.species) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                first?.let {
                    Column {
                        MonsterArtworkView(it, artworkStore, artworkRevision, Modifier.size(116.dp))
                        Text("A：${it.species} / ${it.element} / 進化${it.stage}")
                    }
                }
                second?.let {
                    Column {
                        MonsterArtworkView(it, artworkStore, artworkRevision, Modifier.size(116.dp))
                        Text("B：${it.species} / ${it.element} / 進化${it.stage}")
                    }
                }
            }
            first?.let { monster ->
                Text("素体＋装備パーツ",style=MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick={armorEditing=!armorEditing}) {
                    Text(if(armorEditing) "装備編集を閉じる" else "選んだ仲間の装備を編集")
                }
                if(armorEditing) {
                    Text("スロットごとのドット絵パーツを合成します。未進化の装備は保存され、" +
                        "進化段階に到達すると表示されます。",
                        style=MaterialTheme.typography.bodySmall)
                    ArmorSlot.entries.forEach { slot ->
                        Text(slot.label + "（進化" + slot.minStage + "から）")
                        val imported = monster.customArmorParts.containsKey(slot.name)
                        Text(
                            if (imported) "PNGパーツ装着済み"
                            else "画像パーツ未装着",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(enabled = !inBattle,
                                onClick = { onImportPart(monster, slot) }) {
                                Text("画像パーツを選ぶ")
                            }
                            OutlinedButton(enabled = allowAiEquip && !inBattle,
                                onClick = { onGeneratePart(monster, slot) }) {
                                Text("AIでパーツ画像生成")
                            }
                            if (imported) TextButton(enabled = !inBattle,
                                onClick = { onRemovePart(monster, slot) }) {
                                Text("画像パーツ解除")
                            }
                        }
                        if (imported) {
                            val pos=monster.customArmorOffsets[slot.name] ?: ArmorOffset()
                            Text("画像の位置：横${pos.dx}px、縦${pos.dy}px（±12pxまで）",
                                style=MaterialTheme.typography.bodySmall)
                            Row(modifier=Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                OutlinedButton(enabled=!inBattle && pos.dx > -12,
                                    onClick={onMovePart(monster,slot,-2,0)}) {
                                    Text("← 左")
                                }
                                OutlinedButton(enabled=!inBattle && pos.dx < 12,
                                    onClick={onMovePart(monster,slot,2,0)}) {
                                    Text("右 →")
                                }
                                OutlinedButton(enabled=!inBattle && pos.dy > -12,
                                    onClick={onMovePart(monster,slot,0,-2)}) {
                                    Text("↑ 上")
                                }
                                OutlinedButton(enabled=!inBattle && pos.dy < 12,
                                    onClick={onMovePart(monster,slot,0,2)}) {
                                    Text("下 ↓")
                                }
                            }
                        }
                        Row(modifier=Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            FilterChip(selected=monster.armorParts[slot.name]==null,
                                enabled=!inBattle,
                                onClick={onEquip(monster.id,monster.armorParts-slot.name)},
                                label={Text("なし")})
                            ArmorStyle.entries.forEach { style ->
                                FilterChip(
                                    selected=monster.armorParts[slot.name]==style.name,
                                    enabled=!inBattle,
                                    onClick={onEquip(monster.id,
                                        monster.armorParts+(slot.name to style.name))},
                                    label={Text(style.label)})
                            }
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled=!inBattle,
                            onClick={onEquip(monster.id,MonsterArmor.suggestion(
                                monster.element,
                                dev.glyphlife.monsterquest.pixel.MonsterDesign.bodyType(monster)))}) {
                            Text("属性装備セット")
                        }
                        OutlinedButton(enabled=!inBattle,
                            onClick={onEquip(monster.id,emptyMap())}) {
                            Text("装備を全解除")
                        }
                    }
                    OutlinedButton(
                        enabled=allowAiEquip && !inBattle,
                        onClick={onAiEquip(monster)}
                    ) { Text("AIでこの仲間のアーマーを提案（任意）") }
                    Text("AI機能は画面下方のGemini APIキーを使い、ボタンを押した場合のみ通信します。",
                        style=MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (monster.stage >= 3) "最終進化に到達済み"
                    else "次の進化条件：この冒険で${EvolutionRules.requiredVictories(monster.stage)}体撃破（現在${defeated}体）",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !inBattle && monster.stage < 3 &&
                            defeated >= EvolutionRules.requiredVictories(monster.stage),
                        onClick = { onEvolve(monster.id) }
                    ) { Text("選択モンスターを進化") }
                    OutlinedButton(onClick = { onSendGlyph(monster) }) {
                        Text("Glyph用13×13へ変換")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onExportPng(monster, 64) }) {
                        Text("64px PNG")
                    }
                    OutlinedButton(onClick = { onExportPng(monster, 256) }) {
                        Text("256px PNG")
                    }
                }
                OutlinedButton(onClick = { onExportSpriteSheet(monster) }) {
                    Text("64px・4コマのアニメ画像")
                }
                Text("進化後も個体IDと体型・属性は継承。" +
                    " 元の進化段階用に保存した画像は保持されます。",
                    style = MaterialTheme.typography.bodySmall)
            }
            fusionPreview?.let { target ->
                Text("合成後の姿を確認", style = MaterialTheme.typography.titleMedium)
                Text("${target.species}　属性${target.element} / 進化${target.stage}" +
                    "　HP${target.maxHp}・攻撃${target.attack}")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("ゲーム：64×64")
                        MonsterStaticSprite(target, Modifier.size(160.dp),
                            artworkStore, artworkRevision)
                    }
                    Column {
                        Text("Glyph：13×13")
                        MonsterPortrait(
                            pixels = artworkStore.glyphFor(target),
                            element = target.element,
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }
                Text("確定後もこの形を維持します。画像パーツは親から継承します。" +
                    " 親の全身AI画像をそのまま合成するわけではありません。",
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(
                enabled = !inBattle && first != null && second != null &&
                    firstId != secondId,
                onClick = { confirm = true }
            ) { Text("2匹を合成") }
            Text(if (inBattle) "合成は戦闘中には行えません"
                 else "合成結果は端末に保存され、次の冒険でも援護に参加できます。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("合成しますか？") },
            text = {
                Text("${first?.species ?: "素材A"}と${second?.species ?: "素材B"}を消費し、" +
                    "1匹の新しいモンスターに合成します。元に戻すことはできません。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = firstId
                    val b = secondId
                    confirm = false
                    if (a != null && b != null) {
                        onFuse(a, b)
                        firstId = null
                        secondId = null
                    }
                }) { Text("合成を実行") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("キャンセル") }
            }
        )
    }
}
