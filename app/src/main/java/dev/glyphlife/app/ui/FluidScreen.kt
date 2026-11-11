package dev.glyphlife.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.model.*
import kotlinx.coroutines.delay

/**
 * Screen-side pixel fluid experiment. Fully offline; optional Glyph live
 * output is foreground-only and requires the separately installed vendor AAR.
 */
@Composable
fun FluidScreen(
    modifier: Modifier = Modifier,
    glyphSupported: Boolean,
    glyphStatus: String,
    onConnect: () -> Unit,
    onFrame: (List<Boolean>) -> Unit,
    onPreviewFrame: (List<Boolean>) -> Unit,
    onCopyToCreator: (List<Boolean>) -> Unit
) {
    var pixels by remember { mutableStateOf(GlyphFluid.initial()) }
    var running by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var gravity by remember { mutableStateOf(FluidGravity.DOWN) }
    var frame by remember { mutableIntStateOf(0) }
    var stepMs by remember { mutableFloatStateOf(160f) }
    var confirmCopy by remember { mutableStateOf(false) }

    LaunchedEffect(pixels) { onPreviewFrame(pixels) }

    LaunchedEffect(running, gravity, stepMs, live, glyphStatus) {
        while (running) {
            delay(stepMs.toLong().coerceIn(80, 500))
            frame += 1
            val next = GlyphFluid.step(pixels, gravity, frame)
            pixels = next
            if (live && glyphSupported && glyphStatus == "接続済み") {
                onFrame(next)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Glyph Fluid Lab", style = MaterialTheme.typography.headlineSmall)
        Text(
            "13×13の画素で水のように動く粒子を再現。Nothingの背面LED137点の範囲に合わせて計算します。" +
                "厳密な流体方程式ではなく、粒子のセル移動で流れを近似しています。",
            style = MaterialTheme.typography.bodyMedium
        )
        PixelGrid(
            pixels = pixels,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            onClick = { index -> pixels = GlyphFluid.tap(pixels, index) }
        )
        Text("粒子数：${pixels.count { it }} / 137")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { running = !running }) {
                Text(if (running) "一時停止" else "再生")
            }
            OutlinedButton(onClick = { pixels = GlyphFluid.initial(); frame = 0 }) {
                Text("リセット")
            }
            OutlinedButton(onClick = { pixels = GlyphFluid.addDrop(pixels, frame++) }) {
                Text("水を追加")
            }
        }
        Text("重力の向き")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf(
                FluidGravity.DOWN to "↓ 下",
                FluidGravity.UP to "↑ 上",
                FluidGravity.LEFT to "← 左",
                FluidGravity.RIGHT to "→ 右"
            )
            options.forEach { (direction, label) ->
                FilterChip(
                    selected = gravity == direction,
                    onClick = { gravity = direction },
                    label = { Text(label) }
                )
            }
        }
        Text("更新間隔：${stepMs.toInt()}ms")
        Slider(
            value = stepMs,
            onValueChange = { stepMs = it },
            valueRange = 80f..500f
        )
        HorizontalDivider()
        Text("Nothing Glyphへのライブ出力", style = MaterialTheme.typography.titleMedium)
        Text(
            if (glyphSupported) "Glyph接続可能ビルド（実機での表示は別途検証が必要）"
            else "公式SDKなしのAPKでは背面Glyphへ出力できません"
        )
        Text("接続：$glyphStatus")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = glyphSupported, onClick = onConnect) { Text("Glyphに接続") }
            Spacer(Modifier.width(10.dp))
            Text("背面に出力")
            Spacer(Modifier.width(6.dp))
            Switch(checked = live, enabled = glyphSupported, onCheckedChange = { live = it })
        }
        OutlinedButton(onClick = { confirmCopy = true }) {
            Text("現在の水の形をCreatorへコピー")
        }
        Text(
            "背面出力はアプリの画面を開いている間だけ行います。" +
                "AODの1分ごとの更新とは別機能です。連続点灯の消費電力にご注意ください。",
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (confirmCopy) AlertDialog(
        onDismissRequest = { confirmCopy = false },
        title = { Text("Creatorに転送しますか？") },
        text = { Text("現在のペットで選択中のフレームを、この水の形で上書きします。必要なら先に作品ギャラリーへ保存してください。") },
        confirmButton = {
            TextButton(onClick = {
                onCopyToCreator(pixels)
                confirmCopy = false
            }) { Text("転送する") }
        },
        dismissButton = {
            TextButton(onClick = { confirmCopy = false }) { Text("キャンセル") }
        }
    )
}
