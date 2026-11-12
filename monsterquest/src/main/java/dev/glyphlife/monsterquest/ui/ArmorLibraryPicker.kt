package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline

/**
 * An offline catalog of existing AI/imported armor assets. Showing a part
 * never triggers an API call, and selecting it only requests a preview.
 */
@Composable
fun ArmorLibraryPicker(
    ids: List<String>,
    store: MonsterArtworkStore,
    revision: Int,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    val pageCount = maxOf(1, (ids.size + 7) / 8)
    val safePage = page.coerceIn(0, pageCount - 1)
    Text("保存済みパーツ：${ids.size}件（端末内・AI通信なし）",
        style = MaterialTheme.typography.bodyMedium)
    if (ids.isEmpty()) {
        Text("まだパーツがありません。先に画像を取り込むかAIで作成してください。",
            style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = enabled && safePage > 0,
            onClick = { page = safePage - 1 }) { Text("前へ") }
        Text("${safePage + 1} / $pageCount",
            modifier = Modifier.padding(top = 12.dp))
        OutlinedButton(enabled = enabled && safePage + 1 < pageCount,
            onClick = { page = safePage + 1 }) { Text("次へ") }
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ids.drop(safePage * 8).take(8).forEachIndexed { index, id ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val image = remember(store, id, revision) {
                    store.loadArmorLayer(id)?.let {
                        PixelAssetPipeline.toBitmap(it).asImageBitmap()
                    }
                }
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = "保存済み装備パーツ ${safePage * 8 + index + 1}",
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None
                    )
                } else {
                    Text("画像読込不可", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(enabled = enabled && image != null,
                    onClick = { onSelect(id) }) {
                    Text("使う ${safePage * 8 + index + 1}")
                }
            }
        }
    }
    Text("選択後に64×64の完成形を確認できます。パーツ本体は変更せず、" +
        "現在の仲間に画像IDを共有します。",
        style = MaterialTheme.typography.bodySmall)
}
