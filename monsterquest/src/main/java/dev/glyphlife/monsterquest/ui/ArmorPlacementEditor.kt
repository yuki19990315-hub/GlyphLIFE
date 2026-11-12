package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.ArmorOffset
import dev.glyphlife.monsterquest.pixel.ArmorPlacementDraft
import dev.glyphlife.monsterquest.pixel.ArmorSlot

/**
 * Reusable bounded 1px/5% placement controls. All callbacks are in-memory.
 * No save is performed until the surrounding confirmation action.
 */
@Composable
fun ArmorPlacementControls(
    value: ArmorOffset,
    onChange: (ArmorOffset) -> Unit,
    enabled: Boolean
) {
    Text("位置：横 ${value.dx}px / 縦 ${value.dy}px　大きさ：${value.scalePercent}%",
        style = MaterialTheme.typography.bodySmall)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(enabled = enabled && value.dx > -12,
            onClick = { onChange(value.moved(-1, 0)) }) { Text("← 1px") }
        OutlinedButton(enabled = enabled && value.dx < 12,
            onClick = { onChange(value.moved(1, 0)) }) { Text("1px →") }
        OutlinedButton(enabled = enabled && value.dy > -12,
            onClick = { onChange(value.moved(0, -1)) }) { Text("↑ 1px") }
        OutlinedButton(enabled = enabled && value.dy < 12,
            onClick = { onChange(value.moved(0, 1)) }) { Text("↓ 1px") }
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(enabled = enabled && value.scalePercent > 50,
            onClick = { onChange(value.scaled(-5)) }) { Text("− 5%") }
        OutlinedButton(enabled = enabled && value.scalePercent < 150,
            onClick = { onChange(value.scaled(5)) }) { Text("+ 5%") }
        TextButton(enabled = enabled && value != ArmorOffset(),
            onClick = { onChange(ArmorOffset()) }) { Text("位置・倍率を初期化") }
    }
}

/** Edits an existing attached part without mutating the captured monster. */
@Composable
fun ArmorPlacementEditor(
    monster: WildMonster,
    slot: ArmorSlot,
    store: MonsterArtworkStore,
    revision: Int,
    enabled: Boolean,
    onSave: (ArmorOffset) -> Unit,
    onCancel: () -> Unit
) {
    val initial = monster.customArmorOffsets[slot.name] ?: ArmorOffset()
    var draft by remember(
        monster.id, slot, monster.customArmorParts[slot.name], initial
    ) { mutableStateOf(initial) }
    val candidate = remember(monster, slot, draft) {
        ArmorPlacementDraft.visiblePreview(
            ArmorPlacementDraft.reposition(monster, slot, draft), slot)
    }
    Text("装備の位置・大きさを調整（保存するまで元の姿は変わりません）",
        style = MaterialTheme.typography.titleSmall)
    MonsterStaticSprite(candidate, Modifier.size(180.dp), store, revision)
    if (monster.stage < slot.minStage) {
        Text("進化${slot.minStage}で表示される予想図です。今はまだ姿が変わりません。",
            style = MaterialTheme.typography.bodySmall)
    }
    ArmorPlacementControls(draft, onChange = { draft = it }, enabled = enabled)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = enabled && draft != initial,
            onClick = { onSave(draft) }) { Text("この配置を保存") }
        TextButton(onClick = onCancel) { Text("キャンセル") }
    }
}
