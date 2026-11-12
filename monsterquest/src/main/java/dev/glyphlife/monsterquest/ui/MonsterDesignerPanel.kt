package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.MonsterPrototypeFactory
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.MonsterBodyType

/** No key, no network: local anatomy/element/progression preview. */
@Composable
fun MonsterDesignerPanel(
    enabled:Boolean,
    store:MonsterArtworkStore,
    revision:Int,
    onSummon:(WildMonster)->Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(MonsterBodyType.DRAGON) }
    var element by remember { mutableStateOf("雷") }
    var stage by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }

    val labels=mapOf(
        MonsterBodyType.BEAST to "四足獣",
        MonsterBodyType.DRAGON to "竜",
        MonsterBodyType.BIPED to "二足",
        MonsterBodyType.GOLEM to "巨像",
        MonsterBodyType.SERPENT to "蛇",
        MonsterBodyType.AVIAN to "飛行",
        MonsterBodyType.INSECTOID to "昆虫",
        MonsterBodyType.AQUATIC to "水棲",
        MonsterBodyType.MECHA to "機械",
        MonsterBodyType.SPIRIT to "精霊"
    )

    HorizontalDivider()
    Text("モンスターデザイン工房（完全無料・オフライン）",
        style=MaterialTheme.typography.titleMedium)
    Text("体型10種類×属性7種類×進化3段階のドット絵を確認できます。" +
        " 背景を持たない64×64カラー素材をその場で生成し、AIへの送信は一切ありません。",
        style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick={expanded=!expanded}) {
        Text(if(expanded)"工房を閉じる" else "オリジナルモンスターを設計")
    }
    if(expanded) {
        OutlinedTextField(
            value=name,onValueChange={name=it.take(16)},
            modifier=Modifier.fillMaxWidth(),
            singleLine=true,label={Text("名前（16文字以内・任意）")}
        )
        Text("基本体型")
        Row(modifier=Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            MonsterBodyType.entries.forEach {candidate->
                FilterChip(selected=type==candidate,
                    onClick={type=candidate},label={Text(labels[candidate] ?: candidate.name)})
            }
        }
        Text("属性")
        Row(modifier=Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            MonsterPrototypeFactory.elements.forEach { candidate->
                FilterChip(selected=element==candidate,
                    onClick={element=candidate},label={Text(candidate)})
            }
        }
        Text("デザイン段階（プレビューのみ）")
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            (1..3).forEach { candidate->
                FilterChip(selected=stage==candidate,
                    onClick={stage=candidate},
                    label={Text("進化$candidate")})
            }
        }
        val model=remember(name,type,element,stage) {
            MonsterPrototypeFactory.preview(name,type,element,stage)
        }
        MonsterArtworkView(
            monster=model,
            store=store,
            revision=revision,
            modifier=Modifier.size(256.dp)
        )
        Text("64×64の原画をニアレスト4倍で256×256表示。画像から13×13を" +
            "先に作って拡大する方式ではありません。",
            style=MaterialTheme.typography.bodySmall)
        Button(enabled=enabled,onClick={onSummon(model)}) {
            Text("この体型で敵を召喚（進化1段階から）")
        }
        Text(if(enabled)"戦闘後に捕獲すると進化・融合・Glyph変換を試せます。"
             else "召喚するには通常階の探索中に戻ってください。",
            style=MaterialTheme.typography.bodySmall)
    }
}
