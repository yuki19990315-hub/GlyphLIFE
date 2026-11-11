package dev.glyphlife.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.data.ArtworkSummary

/**
 * Independent collection of named Glyph animations.
 * Opening artwork replaces only the current pet's auto-saved working document.
 */
@Composable
fun GalleryScreen(
    items: List<ArtworkSummary>,
    modifier: Modifier = Modifier,
    onSave: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExportSheet: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<ArtworkSummary?>(null) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("作品ギャラリー", style = MaterialTheme.typography.headlineSmall)
        Text("ドット絵やアニメーションを名前を付けて最大30作品保存できます。")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("新しい作品名") },
            placeholder = { Text("例：眠るドラゴン") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = name.isNotBlank() && items.size < 30, onClick = {
                onSave(name)
                name = ""
            }) { Text("現在のアニメを作品保存") }
        }
        OutlinedButton(onClick = onExportSheet) {
            Text("現在のアニメをPNGスプライトシート保存")
        }
        HorizontalDivider()
        Text("保存済み ${items.size}/30", style = MaterialTheme.typography.titleMedium)
        if (items.isEmpty()) {
            Text("作品はまだありません。Creatorでアニメーションを作ってから保存できます。")
        }
        items.forEach { art ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(art.name, style = MaterialTheme.typography.titleMedium)
                    Text("${art.frameCount}フレーム", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(art.id) }) { Text("開く") }
                        OutlinedButton(onClick = { toDelete = art }) { Text("削除") }
                    }
                }
            }
        }
        Text(
            "保存した作品とペットごとの自動保存データは別管理です。" +
                "作品を開くと現在のペットの編集中アニメが置き換わるので、必要なら先に作品保存してください。",
            style = MaterialTheme.typography.bodySmall
        )
    }
    toDelete?.let { art ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("作品を削除しますか？") },
            text = { Text("「${art.name}」の保存済みコピーを削除します。編集中のペット画像は変更しません。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(art.id)
                    toDelete = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("キャンセル") }
            }
        )
    }
}
