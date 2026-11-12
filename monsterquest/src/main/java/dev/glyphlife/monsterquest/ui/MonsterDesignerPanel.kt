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
    onSummon:(WildMonster)->Unit,
    onExportPng:(WildMonster,Int)->Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(MonsterBodyType.DRAGON) }
    var element by remember { mutableStateOf("雷") }
    var stage by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var variantPage by remember { mutableIntStateOf(0) }
    var selectedVariant by remember { mutableIntStateOf(0) }

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
    Text("モンスターデザイン工房（無料・オフライン対応／AI任意）",
        style=MaterialTheme.typography.titleMedium)
    Text("体型10種類×属性7種類×進化3段階のドット絵を確認できます。" +
        " 背景を持たない64×64カラー素材をその場で生成し、AIへの送信は一切ありません。",
        style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick={expanded=!expanded}) {
        Text(if(expanded)"工房を閉じる" else "オリジナルモンスターを設計")
    }
    if(expanded) {
        OutlinedButton(onClick={
            name="ライゼル"
            type=MonsterBodyType.BEAST
            element="雷"
            stage=1
            variantPage=0
            selectedVariant=0
        }) {
            Text("固有ドット絵「ライゼル」を見る")
        }
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
        val featured=(name.trim()=="ライゼル" && element=="雷" &&
            type==MonsterBodyType.BEAST)
        if(featured) {
            Text("ライゼルはオリジナルの固定64×64ドット絵です。" +
                " 進化段階を変えて3つの姿を比べられます。")
        } else {
        HorizontalDivider()
        Text("外見比較（全32案・4案ずつ）",style=MaterialTheme.typography.titleMedium)
        Text("同じ体型・属性でも角、装甲、尾、頭部の輪郭が変わります。" +
            " 選んだ外見は実際の召喚後にも維持されます。",
            style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=variantPage>0,onClick={
                variantPage--
                selectedVariant=variantPage*4
            }) { Text("前の4案") }
            Text("${variantPage+1}/8",modifier=Modifier.padding(top=12.dp))
            OutlinedButton(enabled=variantPage<7,onClick={
                variantPage++
                selectedVariant=variantPage*4
            }) { Text("次の4案") }
        }
        val candidates=remember(name,type,element,stage,variantPage) {
            (variantPage*4 until variantPage*4+4).map { number ->
                number to MonsterPrototypeFactory.preview(name,type,element,stage,number)
            }
        }
        Row(
            modifier=Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            candidates.forEach { (number,candidate) ->
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    MonsterStaticSprite(candidate,Modifier.size(88.dp))
                    FilterChip(
                        selected=selectedVariant==number,
                        onClick={selectedVariant=number},
                        label={Text("案${number+1}")}
                    )
                }
            }
        }
        }
        val model=remember(name,type,element,stage,selectedVariant) {
            MonsterPrototypeFactory.preview(name,type,element,stage,selectedVariant)
        }
        Text("選択中：案${selectedVariant+1} / 進化$stage / ${model.species}",
            style=MaterialTheme.typography.titleMedium)
        MonsterArtworkView(
            monster=model,
            store=store,
            revision=revision,
            modifier=Modifier.size(256.dp)
        )
        Text("ゲーム画面：ネイティブ64×64を256×256に整数拡大。" +
            " 13×13の拡大画像ではありません。",
            style=MaterialTheme.typography.bodySmall)
        Text("Glyphに変換した結果",style=MaterialTheme.typography.titleSmall)
        MonsterPortrait(
            pixels=model.pixels, element=model.element,
            modifier=Modifier.size(138.dp)
        )
        if(stage>1) {
            Text("召喚は進化1から開始。実際に出現する姿は次のとおりです。",
                style=MaterialTheme.typography.bodySmall)
            val initial=remember(name,type,element,selectedVariant) {
                MonsterPrototypeFactory.preview(name,type,element,1,selectedVariant)
            }
            MonsterStaticSprite(initial,Modifier.size(116.dp))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={onExportPng(model,64)}) {
                Text("選んだ64px画像を保存")
            }
            OutlinedButton(onClick={onExportPng(model,256)}) {
                Text("256px画像を保存")
            }
        }
        Button(enabled=enabled,onClick={onSummon(model)}) {
            Text("案${selectedVariant+1}を召喚（進化1から）")
        }
        Text(if(enabled)"戦闘後に捕獲すると進化・融合・Glyph変換を試せます。"
             else "召喚するには通常階の探索中に戻ってください。",
            style=MaterialTheme.typography.bodySmall)
    }
}
