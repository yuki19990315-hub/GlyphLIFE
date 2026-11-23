package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.ai.MonsterWorkshopDraft
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline

@Composable
fun MonsterWorkshopReview(
    draft: MonsterWorkshopDraft,
    busy: Boolean,
    canAccept: Boolean,
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    val image = remember(draft.art) {
        draft.art?.let { PixelAssetPipeline.toBitmap(it.sprite).asImageBitmap() }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("AIモンスター工房・候補確認") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${draft.idea.name} / ${draft.idea.element} / 進化1")
                Text(draft.idea.description, style = MaterialTheme.typography.bodySmall)
                if (image != null) {
                    Image(bitmap = image, contentDescription = "64×64の候補画像",
                        modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally),
                        filterQuality = FilterQuality.None)
                    val audit = draft.art?.audit
                    if (audit != null) {
                        Text("素材監査：64×64 / ${audit.totalColors}色（透過込み） / " +
                            (if (audit.hasTransparency) "透過あり" else "透過なし") + " / " +
                            (if (audit.croppedAtEdge) "端に接触" else "端に接触なし"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (!draft.readyForApproval)
                        Text("規格外のため採用できません。破棄して再生成してください。",
                            color = MaterialTheme.colorScheme.error)
                    Text("見た目・骨格・輪郭・AI感の有無は実画像を見て判断してください。",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    MonsterStaticSprite(draft.monster, Modifier.size(168.dp))
                    Text("これはAIが描いた画像ではなく既存の仮素材です。" +
                        " 画像生成をONにして再生成すると全身画像を作れます。",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("採用時のみ召喚結晶を1個消費。API料金は不採用でも戻りません。",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !busy && canAccept && draft.readyForApproval,
                onClick = onAccept) {
                Text(if (busy) "保存中…" else "採用して図鑑登録")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onCancel) { Text("不採用・破棄") }
        }
    )
}
