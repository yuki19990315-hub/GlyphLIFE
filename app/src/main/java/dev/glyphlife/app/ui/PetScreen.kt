package dev.glyphlife.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.model.PetState

@Composable
fun PetScreen(
    pet: PetState, pixels: List<Boolean>, modifier: Modifier = Modifier,
    onPetChange: (PetState) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("あなたのペット", style = MaterialTheme.typography.headlineSmall)
        Text("${pet.name}  ·  Lv.${pet.level}  ·  ${pet.species} / ${pet.element}")
        PixelGrid(pixels, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
        Text("性格：${pet.personality}")
        Text("なかよし度: ${pet.happiness} / 100")
        LinearProgressIndicator(progress = { pet.happiness / 100f }, modifier = Modifier.fillMaxWidth())
        Text("元気: ${pet.energy} / 100")
        LinearProgressIndicator(progress = { pet.energy / 100f }, modifier = Modifier.fillMaxWidth())
        Text("経験値: ${pet.experience} / 100")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPetChange(pet.play()) }, enabled = pet.energy >= 6) { Text("遊ぶ") }
            Button(onClick = { onPetChange(pet.feed()) }) { Text("ごはん") }
            OutlinedButton(onClick = { onPetChange(pet.rest()) }) { Text("休む") }
        }
        Text("※ 初期版の性格・会話・進化はAI未実装。育成値は端末内に保存されます。",
            style = MaterialTheme.typography.bodySmall)
    }
}
