package dev.glyphlife.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.data.ChatEntry
import dev.glyphlife.app.model.*
import kotlinx.coroutines.delay

@Composable
fun PetScreen(
    pet: PetState,
    animation: GlyphAnimation,
    chat: List<ChatEntry>,
    modifier: Modifier = Modifier,
    onPetChange: (PetState) -> Unit,
    onHatch: () -> Unit,
    onGeneratePetArt: () -> Unit,
    onChat: (String) -> Unit,
    aiKey: String,
    aiBusy: Boolean,
    onAiKeyChange: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var confirmHatch by remember { mutableStateOf(false) }
    var confirmGenerateArt by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(animation.frames) {
        var i = 0
        while (true) {
            previewIndex = i
            delay(animation.frames[i].durationMs.toLong())
            i = (i + 1) % animation.frames.size
        }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Glyph Pet", style = MaterialTheme.typography.headlineSmall)
        Text("${pet.name}  ·  Lv.${pet.level}  ·  ${pet.species} / ${pet.element}")
        PixelGrid(animation.frames[previewIndex.coerceIn(animation.frames.indices)].pixels,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Text("性格：${pet.personality}")
        Text("成長段階：${PetSpriteGenerator.stageName(pet.level)}（Lv5 / Lv12で外見を進化可能）")
        Text("仲良し度：${pet.happiness}/100")
        LinearProgressIndicator(progress = { pet.happiness / 100f }, modifier = Modifier.fillMaxWidth())
        Text("元気：${pet.energy}/100")
        LinearProgressIndicator(progress = { pet.energy / 100f }, modifier = Modifier.fillMaxWidth())
        Text("経験値：${pet.experience}/100")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(enabled = pet.energy >= 6, onClick = { onPetChange(pet.play()) }) { Text("遊ぶ") }
            Button(onClick = { onPetChange(pet.feed()) }) { Text("ごはん") }
            OutlinedButton(onClick = { onPetChange(pet.rest()) }) { Text("休む") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { confirmHatch = true }) { Text("新しい卵をかえす") }
            OutlinedButton(onClick = { confirmGenerateArt = true }) { Text("専用ドット絵を作る") }
        }
        HorizontalDivider()
        Text("ペットとおしゃべり", style = MaterialTheme.typography.titleMedium)
        Text("キー未入力ならローカルの簡易返答、入力するとGeminiへ会話内容を送信します。" +
            "APIキーはこの起動中だけ保持。無料枠・料金・データ取扱いは提供元の条件を確認してください。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = aiKey, onValueChange = onAiKeyChange,
            label = { Text("Gemini APIキー（任意・保存しません）") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        chat.takeLast(8).forEach {
            Text(if (it.fromUser) "あなた：${it.text}" else "${pet.name}：${it.text}")
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(200) },
            label = { Text("話しかける（200字まで）") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(enabled = !aiBusy && draft.isNotBlank(), onClick = {
            onChat(draft)
            draft = ""
        }) { Text(if (aiBusy) "AI応答待ち…" else "話しかける") }
    }
    if (confirmGenerateArt) AlertDialog(
        onDismissRequest = { confirmGenerateArt = false },
        title = { Text("このペットの専用アニメを作る？") },
        text = { Text("種類、成長段階、元気に応じた3コマを自動生成します。現在編集中のドット絵は上書きされるので、必要なら先にJSONで保存してください。") },
        confirmButton = { TextButton(onClick = {
            onGeneratePetArt()
            confirmGenerateArt = false
        }) { Text("専用アニメを作る") } },
        dismissButton = { TextButton(onClick = { confirmGenerateArt = false }) { Text("やめる") } }
    )

    if (confirmHatch) AlertDialog(
        onDismissRequest = { confirmHatch = false },
        title = { Text("新しい卵をかえす？") },
        text = { Text("現在のペットの育成データとおしゃべり履歴は置き換わります。ドット絵は残ります。") },
        confirmButton = { TextButton(onClick = {
            onHatch()
            confirmHatch = false
        }) { Text("新しいペットにする") } },
        dismissButton = { TextButton(onClick = { confirmHatch = false }) { Text("やめる") } }
    )
}
