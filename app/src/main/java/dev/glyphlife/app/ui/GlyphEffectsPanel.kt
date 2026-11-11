package dev.glyphlife.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.model.GlyphAnimation
import dev.glyphlife.app.model.GlyphEffect
import dev.glyphlife.app.model.GlyphEffects
import kotlinx.coroutines.delay

/**
 * Offline effect generator with a live draft preview.
 * Existing artwork is not overwritten until the user explicitly confirms.
 */
@Composable
fun GlyphEffectsPanel(
    onApply: (GlyphAnimation) -> Unit,
    onSaveToGallery: (String, GlyphAnimation) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var effect by remember { mutableStateOf(GlyphEffect.RAIN) }
    var seed by remember { mutableIntStateOf(42) }
    var speed by remember { mutableFloatStateOf(170f) }
    var count by remember { mutableIntStateOf(24) }
    var selectedFrame by remember { mutableIntStateOf(0) }
    var confirmReplace by remember { mutableStateOf(false) }
    val draft = remember(effect, seed, speed, count) {
        GlyphEffects.generate(effect, seed, speed.toInt(), count)
    }

    LaunchedEffect(expanded, draft) {
        selectedFrame = 0
        while (expanded) {
            delay(draft.frames[selectedFrame].durationMs.toLong())
            selectedFrame = (selectedFrame + 1) % draft.frames.size
        }
    }

    HorizontalDivider()
    Text("エフェクト工房（オフライン）", style = MaterialTheme.typography.titleMedium)
    Text("7種類の動くドット絵を自動生成。AIキー不要、元の絵は確認するまで変更しません。",
        style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "工房を閉じる" else "エフェクトを作る")
    }
    if (expanded) {
        Text(effect.description, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlyphEffect.entries.forEach { candidate ->
                FilterChip(
                    selected = effect == candidate,
                    onClick = { effect = candidate },
                    label = { Text(candidate.label) }
                )
            }
        }
        PixelGrid(
            pixels = draft.frames[selectedFrame.coerceIn(draft.frames.indices)].pixels,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
        )
        Text("コマ数：$count　速度：${speed.toInt()} ms　パターン：$seed")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(12, 16, 24, 32).forEach { length ->
                FilterChip(
                    selected = count == length,
                    onClick = { count = length },
                    label = { Text("${length}コマ") }
                )
            }
        }
        Slider(
            value = speed,
            valueRange = 100f..600f,
            onValueChange = { speed = it }
        )
        OutlinedButton(onClick = { seed = if (seed >= 9999) 0 else seed + 1 }) {
            Text("別のパターンにする")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { confirmReplace = true }) {
                Text("Creatorに読み込む")
            }
            OutlinedButton(onClick = {
                onSaveToGallery("${effect.label} エフェクト #$seed", draft)
            }) {
                Text("作品として保存")
            }
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("編集中のアニメを置き換えますか？") },
            text = { Text("エフェクト全体（${draft.frames.size}コマ）を読み込みます。元の作品が必要なら先に作品ギャラリーへ保存してください。") },
            confirmButton = {
                TextButton(onClick = {
                    onApply(draft)
                    confirmReplace = false
                }) { Text("読み込む") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("キャンセル") }
            }
        )
    }
}
