package dev.glyphlife.app.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.model.*
import kotlinx.coroutines.delay

@Composable
fun GlyphSnakeScreen(
    modifier: Modifier = Modifier,
    glyphSupported: Boolean,
    glyphStatus: String,
    onConnect: () -> Unit,
    onFrame: (List<Boolean>) -> Unit,
    onEditReplay: (GlyphAnimation) -> Unit,
    onSaveReplay: (String, GlyphAnimation) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("glyph_life_minigames", Context.MODE_PRIVATE)
    }
    var best by remember { mutableIntStateOf(prefs.getInt("snake_best", 0)) }
    var round by remember { mutableIntStateOf(1) }
    var snake by remember { mutableStateOf(GlyphSnake.initial(round)) }
    var requestedDirection by remember { mutableStateOf(SnakeDirection.RIGHT) }
    var running by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var stepMs by remember { mutableFloatStateOf(320f) }
    var history by remember {
        mutableStateOf(listOf(GlyphFrame(GlyphSnake.pixels(snake), 320)))
    }
    var confirmCopy by remember { mutableStateOf(false) }

    fun doTick() {
        if (!snake.alive) return
        val next = GlyphSnake.step(snake, requestedDirection, round)
        snake = next
        val pixels = GlyphSnake.pixels(next)
        history = (history + GlyphFrame(pixels, stepMs.toInt())).takeLast(64)
        if (live && glyphSupported && glyphStatus == "接続済み") onFrame(pixels)
        if (!next.alive) {
            running = false
            if (next.score > best) {
                best = next.score
                prefs.edit().putInt("snake_best", best).apply()
            }
        }
    }

    LaunchedEffect(running, stepMs, live, glyphStatus) {
        while (running && snake.alive) {
            delay(stepMs.toLong())
            doTick()
        }
    }
    val replay = remember(history) { GlyphAnimation(history) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Glyph Snake", style = MaterialTheme.typography.headlineSmall)
        Text("円形137LEDの中をヘビが進みます。光るエサを食べて伸び、壁や自分に当たると終了。" +
            "スマホ画面のみでも遊べます。", style = MaterialTheme.typography.bodySmall)
        PixelGrid(
            pixels = GlyphSnake.pixels(snake),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        Text("スコア：${snake.score}　最高：$best　長さ：${snake.body.size}")
        if (!snake.alive) Text("ゲームオーバー！ リプレイを編集・保存できます。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = snake.alive, onClick = { running = !running }) {
                Text(if (running) "一時停止" else "スタート")
            }
            OutlinedButton(onClick = {
                round += 1
                snake = GlyphSnake.initial(round)
                requestedDirection = SnakeDirection.RIGHT
                history = listOf(GlyphFrame(GlyphSnake.pixels(snake), stepMs.toInt()))
                running = false
            }) { Text("新しいゲーム") }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.width(70.dp))
                Button(onClick = { requestedDirection = SnakeDirection.UP }) { Text("↑") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { requestedDirection = SnakeDirection.LEFT }) { Text("←") }
                Button(onClick = { requestedDirection = SnakeDirection.DOWN }) { Text("↓") }
                Button(onClick = { requestedDirection = SnakeDirection.RIGHT }) { Text("→") }
            }
        }
        Text("進む速さ：${stepMs.toInt()} ms/コマ")
        Slider(value = stepMs, onValueChange = { stepMs = it },
            valueRange = 180f..650f)
        HorizontalDivider()
        Text("リプレイ（直近${history.size}コマ）", style = MaterialTheme.typography.titleMedium)
        Text("最大64コマの直近プレイをCreatorや作品ギャラリーに保存できます。" +
            "リプレイの先頭からではなく直近のコマを保持します。",
            style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(enabled = history.size > 1, onClick = { confirmCopy = true }) {
                Text("Creatorでリプレイ編集")
            }
            OutlinedButton(enabled = history.size > 1, onClick = {
                onSaveReplay("Snake スコア${snake.score} #$round", replay)
            }) { Text("作品に保存") }
        }
        HorizontalDivider()
        Text("背面Glyphへライブ転送", style = MaterialTheme.typography.titleMedium)
        Text(if (glyphSupported) "公式SDK付きビルド（実機動作未検証）"
             else "SDKなしビルドでは画面プレビューのみ")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = glyphSupported, onClick = onConnect) { Text("Glyph接続") }
            Spacer(Modifier.width(10.dp))
            Text("転送")
            Spacer(Modifier.width(8.dp))
            Switch(checked = live, enabled = glyphSupported, onCheckedChange = { live = it })
        }
        Text("背面Glyphへ送れるのは前景でのプレイ中のみ。AODや端末の画面消灯中のゲーム動作は行いません。",
            style = MaterialTheme.typography.bodySmall)
    }

    if (confirmCopy) AlertDialog(
        onDismissRequest = { confirmCopy = false },
        title = { Text("SnakeのリプレイをCreatorに読み込む？") },
        text = { Text("現在編集中のドットアニメを置き換えます。必要なら先に作品ギャラリーに保存してください。") },
        confirmButton = {
            TextButton(onClick = {
                onEditReplay(replay)
                confirmCopy = false
            }) { Text("読み込む") }
        },
        dismissButton = {
            TextButton(onClick = { confirmCopy = false }) { Text("キャンセル") }
        }
    )
}

/** Fluid and Snake share one top-level navigation slot to keep navigation compact. */
@Composable
fun GlyphPlaygroundScreen(
    modifier: Modifier = Modifier,
    glyphSupported: Boolean,
    glyphStatus: String,
    onConnect: () -> Unit,
    onFrame: (List<Boolean>) -> Unit,
    onPreviewFluidFrame: (List<Boolean>) -> Unit,
    onCopyFluidToCreator: (List<Boolean>) -> Unit,
    onEditSnakeReplay: (GlyphAnimation) -> Unit,
    onSaveSnakeReplay: (String, GlyphAnimation) -> Unit
) {
    var choice by remember { mutableStateOf("Fluid") }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = choice == "Fluid",
                onClick = { choice = "Fluid" },
                label = { Text("≈  流体シミュレーション") }
            )
            FilterChip(
                selected = choice == "Snake",
                onClick = { choice = "Snake" },
                label = { Text("●  Glyph Snake") }
            )
        }
        if (choice == "Fluid") {
            FluidScreen(
                modifier = Modifier.weight(1f),
                glyphSupported = glyphSupported,
                glyphStatus = glyphStatus,
                onConnect = onConnect,
                onFrame = onFrame,
                onPreviewFrame = onPreviewFluidFrame,
                onCopyToCreator = onCopyFluidToCreator
            )
        } else {
            GlyphSnakeScreen(
                modifier = Modifier.weight(1f),
                glyphSupported = glyphSupported,
                glyphStatus = glyphStatus,
                onConnect = onConnect,
                onFrame = onFrame,
                onEditReplay = onEditSnakeReplay,
                onSaveReplay = onSaveSnakeReplay
            )
        }
    }
}
