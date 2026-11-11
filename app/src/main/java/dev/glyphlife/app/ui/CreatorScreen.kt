package dev.glyphlife.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.model.*
import kotlinx.coroutines.delay

@Composable
fun CreatorScreen(
    animation: GlyphAnimation,
    modifier: Modifier = Modifier,
    glyphStatus: String,
    isGlyphSupported: Boolean,
    onAnimationChange: (GlyphAnimation) -> Unit,
    onConnect: () -> Unit,
    onShowFrame: (List<Boolean>) -> Unit,
    onImportImage: (Uri) -> Unit,
    onImportAnimation: (Uri) -> Unit,
    onExportAnimation: () -> Unit,
    onSavePng: () -> Unit,
    aiKey: String,
    aiBusy: Boolean,
    onAiKeyChange: (String) -> Unit,
    onAiGenerate: (String) -> Unit
) {
    var tool by remember { mutableStateOf("描く") }
    var aiPrompt by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var visibleFrame by remember { mutableIntStateOf(animation.selected) }
    val undo = remember { mutableStateListOf<GlyphAnimation>() }
    val redo = remember { mutableStateListOf<GlyphAnimation>() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { playing = false; onImportImage(uri) }
    }
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { playing = false; onImportAnimation(uri); undo.clear(); redo.clear() }
    }

    fun change(next: GlyphAnimation) {
        if (next == animation) return
        if (undo.size >= 40) undo.removeAt(0)
        undo.add(animation)
        redo.clear()
        playing = false
        onAnimationChange(next)
    }

    LaunchedEffect(playing, animation.frames) {
        if (!playing) {
            visibleFrame = animation.selected
        } else {
            var index = animation.selected
            while (true) {
                visibleFrame = index
                delay(animation.frames[index].durationMs.toLong())
                index = (index + 1) % animation.frames.size
            }
        }
    }
    LaunchedEffect(animation.selected, playing) {
        if (!playing) visibleFrame = animation.selected
    }
    LaunchedEffect(live, visibleFrame, animation.frames, glyphStatus) {
        if (live && glyphStatus == "接続済み") {
            delay(75) // Debounce rapid screen edits and avoid spamming the SDK.
            onShowFrame(animation.frames[visibleFrame.coerceIn(animation.frames.indices)].pixels)
        }
    }

    val drawing = if (playing) animation.frames[visibleFrame.coerceIn(animation.frames.indices)].pixels
                  else animation.current.pixels

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Glyph Creator", style = MaterialTheme.typography.headlineSmall)
        Text("13×13のアニメーション編集。画像・JSONの読み込みと自動保存に対応。")
        PixelGrid(
            pixels = drawing,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            onClick = if (playing) null else { index ->
                val pixels = animation.current.pixels
                val next = when (tool) {
                    "消す" -> GlyphOps.set(pixels, index, false)
                    "塗りつぶす" -> GlyphOps.floodFill(pixels, index, true)
                    else -> GlyphOps.set(pixels, index, true)
                }
                change(animation.replace(next))
            }
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("描く", "消す", "塗りつぶす").forEach { label ->
                FilterChip(selected = tool == label, onClick = { tool = label }, label = { Text(label) })
            }
            OutlinedButton(enabled = undo.isNotEmpty(), onClick = {
                redo.add(animation)
                playing = false
                onAnimationChange(undo.removeAt(undo.lastIndex))
            }) { Text("元に戻す") }
            OutlinedButton(enabled = redo.isNotEmpty(), onClick = {
                undo.add(animation)
                playing = false
                onAnimationChange(redo.removeAt(redo.lastIndex))
            }) { Text("やり直す") }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(onClick = { change(animation.transform(GlyphOps::mirrorHorizontal)) }) { Text("左右反転") }
            OutlinedButton(onClick = { change(animation.transform(GlyphOps::mirrorVertical)) }) { Text("上下反転") }
            OutlinedButton(onClick = { change(animation.transform(GlyphOps::rotateRight)) }) { Text("90°回転") }
            OutlinedButton(onClick = { change(animation.transform(GlyphOps::invert)) }) { Text("反転") }
            OutlinedButton(onClick = { change(animation.replace(PixelArt.empty())) }) { Text("全消去") }
        }
        Text("フレーム（${animation.frames.size}/64）", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            animation.frames.forEachIndexed { index, frame ->
                FilterChip(
                    selected = animation.selected == index,
                    onClick = {
                        playing = false
                        onAnimationChange(animation.choose(index))
                    },
                    label = { Text("${index + 1}：${frame.durationMs}ms") }
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(enabled = !playing, onClick = { change(animation.addFrame()) }) { Text("追加") }
            OutlinedButton(enabled = !playing, onClick = { change(animation.addFrame(duplicate = true)) }) {
                Text("複製")
            }
            OutlinedButton(enabled = !playing && animation.frames.size > 1,
                onClick = { change(animation.deleteFrame()) }) { Text("削除") }
            Button(onClick = { playing = !playing }) { Text(if (playing) "停止" else "再生") }
        }
        Text("選択フレームの表示時間：${animation.current.durationMs} ms")
        Slider(
            value = animation.current.durationMs.toFloat(),
            valueRange = 100f..2000f,
            steps = 18,
            onValueChange = { onAnimationChange(animation.setDuration(it.toInt())) },
            enabled = !playing
        )
        HorizontalDivider()
        Text("画像とデータ", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }) { Text("画像から変換") }
            OutlinedButton(onClick = onSavePng) { Text("PNG保存") }
            OutlinedButton(onClick = onExportAnimation) { Text("JSON共有") }
            OutlinedButton(onClick = { jsonPicker.launch("*/*") }) { Text("JSON読込") }
        }
        HorizontalDivider()
        Text("AIからアニメーション生成（任意）", style = MaterialTheme.typography.titleMedium)
        Text("Gemini APIキーがある場合のみ実際のAIを使用。キーは保存しません。送信内容には指示文を含みます。")
        OutlinedTextField(value = aiKey, onValueChange = onAiKeyChange,
            label = { Text("Gemini APIキー（この起動中だけ）") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = aiPrompt, onValueChange = { aiPrompt = it.take(250) },
            label = { Text("例：小さなドラゴンがジャンプ") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !aiBusy && aiKey.isNotBlank() && aiPrompt.isNotBlank(),
            onClick = { playing = false; onAiGenerate(aiPrompt) }) {
            Text(if (aiBusy) "AI生成中…" else "AIでドット絵を生成")
        }
        HorizontalDivider()
        Text("背面Glyphプレビュー", style = MaterialTheme.typography.titleMedium)
        Text(if (isGlyphSupported) "Nothing SDK組み込みビルド" else "SDKなし：背面表示は利用できません")
        Text("状態：$glyphStatus", style = MaterialTheme.typography.bodySmall)
        Text(
            if (isGlyphSupported)
                "Flip to GlyphのAOD Toyは端末の「設定 → Glyph Interface → Flip to Glyph → Always-on Glyph Toy」でGlyph Life Petを選択。AOD更新は約1分に1コマです。"
            else
                "このAPKはNothing SDKを含みません。AOD Toyは無効で、背面点灯はできません。",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = isGlyphSupported, onClick = onConnect) { Text("接続") }
            Button(enabled = isGlyphSupported && glyphStatus == "接続済み",
                onClick = { onShowFrame(drawing) }) { Text("現在のコマを表示") }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("編集・再生を背面へライブ表示")
            Spacer(Modifier.width(8.dp))
            Switch(checked = live, onCheckedChange = { live = it }, enabled = isGlyphSupported)
        }
        Text(
            "前景アプリ内のライブ再生とAODの1分ごとの更新は別機能です。" +
                "AI生成はAPIキー入力時のみ使用できます（実ネットワーク通信は未検証）。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
