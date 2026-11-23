package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
    canGenerateImage: Boolean,
    onSelect: (Int) -> Unit,
    onGenerateAlternative: (String) -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    var feedback by remember(draft.monster.id) { mutableStateOf("") }
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
                if (draft.variants.size > 1) {
                    Text("別案の比較（${draft.variants.size}/4枚）",
                        style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        draft.variants.forEachIndexed { index, variant ->
                            val thumbnail = remember(variant) {
                                PixelAssetPipeline.toBitmap(variant.sprite).asImageBitmap()
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(bitmap = thumbnail,
                                    contentDescription = "別案${index + 1}",
                                    modifier = Modifier.size(72.dp),
                                    filterQuality = FilterQuality.None)
                                FilterChip(
                                    selected = index == draft.selectedIndex,
                                    enabled = !busy,
                                    onClick = { onSelect(index) },
                                    label = { Text("案${index + 1}") }
                                )
                            }
                        }
                    }
                }
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
                HorizontalDivider()
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it.take(100) },
                    label = { Text("別案への指示（100文字以内・任意）") },
                    placeholder = { Text("例：角を長く、装甲を減らして") },
                    enabled = !busy && draft.canGenerateMore,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    enabled = !busy && canGenerateImage && draft.canGenerateMore,
                    onClick = { onGenerateAlternative(feedback) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (draft.variants.isEmpty()) "AI画像を生成して比較"
                         else "別案をAI生成（最大4候補）")
                }
                Text("生成するたびに外部APIを使用します。別案は既存画像の" +
                    "部分編集ではなく、新しく描いた候補です。" +
                    " 気に入った案は前の候補に戻して選べます。",
                    style = MaterialTheme.typography.bodySmall)
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
