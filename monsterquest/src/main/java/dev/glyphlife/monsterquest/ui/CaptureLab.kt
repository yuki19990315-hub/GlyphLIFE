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
import dev.glyphlife.monsterquest.model.WildMonster

/**
 * Two-material fusion screen. A confirmation is required because selected
 * captures are permanently consumed by an offline game operation.
 */
@Composable
fun CaptureLab(
    captured: List<WildMonster>,
    inBattle: Boolean,
    artworkStore: MonsterArtworkStore,
    artworkRevision: Int,
    onFuse: (String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var firstId by remember { mutableStateOf<String?>(null) }
    var secondId by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val first = captured.firstOrNull { it.id == firstId }
    val second = captured.firstOrNull { it.id == secondId }
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
                        Text("A：${it.species} ${it.element}")
                    }
                }
                second?.let {
                    Column {
                        MonsterArtworkView(it, artworkStore, artworkRevision, Modifier.size(116.dp))
                        Text("B：${it.species} ${it.element}")
                    }
                }
            }
            if (first != null && second != null && first.id != second.id) {
                Text("合成予想：${FusionRules.resultName(first, second)}　" +
                    "属性：${FusionRules.resultElement(first, second)}")
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
