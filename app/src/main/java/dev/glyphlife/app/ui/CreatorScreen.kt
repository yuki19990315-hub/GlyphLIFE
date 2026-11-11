package dev.glyphlife.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.model.PixelArt

@Composable
fun CreatorScreen(
    pixels: List<Boolean>, modifier: Modifier = Modifier,
    glyphStatus: String, isGlyphSupported: Boolean,
    onPixelsChange: (List<Boolean>) -> Unit,
    onConnect: () -> Unit, onSend: () -> Unit
) {
    var erase by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Glyph Creator", style = MaterialTheme.typography.headlineSmall)
        Text("13×13ドット絵エディター（MVP）")
        PixelGrid(pixels, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { index ->
            onPixelsChange(pixels.toMutableList().apply { this[index] = !erase })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !erase, onClick = { erase = false }, label = { Text("描く") })
            FilterChip(selected = erase, onClick = { erase = true }, label = { Text("消す") })
            OutlinedButton(onClick = { onPixelsChange(PixelArt.empty()) }) { Text("全消去") }
        }
        Text("編集内容は自動保存されます。")
        HorizontalDivider()
        Text("背面Glyph表示", style = MaterialTheme.typography.titleMedium)
        Text(if (isGlyphSupported) "公式SDKが組み込まれたビルドです"
             else "SDKなしビルドです（画面プレビューのみ）")
        Text("状態: $glyphStatus", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConnect, enabled = isGlyphSupported) { Text("Glyphに接続") }
            OutlinedButton(onClick = onSend, enabled = isGlyphSupported) { Text("背面へ送信") }
        }
        Text("AIドット絵生成・フレームアニメーションは次フェーズで実装。",
            style = MaterialTheme.typography.bodySmall)
    }
}
