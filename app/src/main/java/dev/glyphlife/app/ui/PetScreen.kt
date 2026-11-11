package dev.glyphlife.app.ui

import androidx.compose.foundation.layout.*
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
    onChat: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var confirmHatch by remember { mutableStateOf(false) }
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
        OutlinedButton(onClick = { confirmHatch = true }) { Text("新しい卵をかえす") }
        HorizontalDivider()
        Text("ペットとおしゃべり", style = MaterialTheme.typography.titleMedium)
        Text("現在は通信不要の簡易返答です。AI言語モデルとの会話はまだ未実装です。",
            style = MaterialTheme.typography.bodySmall)
        chat.takeLast(8).forEach {
            Text(if (it.fromUser) "あなた：${it.text}" else "${pet.name}：${it.text}")
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(200) },
            label = { Text("話しかける（200字まで）") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(enabled = draft.isNotBlank(), onClick = {
            onChat(draft)
            draft = ""
        }) { Text("話しかける") }
    }
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
