package dev.glyphlife.monsterquest

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.data.QuestStorage
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.*
import dev.glyphlife.monsterquest.ai.MonsterAi
import dev.glyphlife.monsterquest.ai.MonsterIdea
import dev.glyphlife.monsterquest.ai.MonsterConceptArt
import dev.glyphlife.monsterquest.ai.ConceptStyle
import dev.glyphlife.monsterquest.ui.MonsterArt
import dev.glyphlife.monsterquest.ui.MonsterPortrait
import dev.glyphlife.monsterquest.ui.MonsterArtworkView
import dev.glyphlife.monsterquest.ui.CaptureLab
import dev.glyphlife.monsterquest.ui.MonsterDesignerPanel
import dev.glyphlife.monsterquest.ui.MonsterStaticSprite
import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterAnimation
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.pixel.MonsterArmor
import dev.glyphlife.monsterquest.pixel.ArmorSlot
import dev.glyphlife.monsterquest.pixel.PixelSprite
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline
import dev.glyphlife.monsterquest.ai.ArmorLayerAi
import dev.glyphlife.monsterquest.ai.ArmorAi
import kotlinx.coroutines.launch

private data class ArmorLayerDraft(
    val sourceMonster: WildMonster,
    val slot: ArmorSlot,
    val sprite: PixelSprite,
    val source: String
)

class MonsterQuestActivity : ComponentActivity() {
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = sharedJson(intent)
        val storage = QuestStorage(this)
        val artworkStore = MonsterArtworkStore(this)
        setContent {
            var state by remember { mutableStateOf(storage.load()) }
            var status by remember { mutableStateOf("オフラインで遊べます") }
            var aiKey by remember { mutableStateOf("") }
            var aiDescription by remember { mutableStateOf("") }
            var aiBusy by remember { mutableStateOf(false) }
            var pendingTextArmor by remember {
                mutableStateOf<Pair<WildMonster, Map<String, String>>?>(null)
            }
            var pendingImageArmor by remember { mutableStateOf<ArmorLayerDraft?>(null) }
            var imageImportTarget by remember {
                mutableStateOf<Pair<String, ArmorSlot>?>(null)
            }
            var artworkRevision by remember { mutableIntStateOf(0) }
            var artImportId by remember { mutableStateOf<String?>(null) }
            var artImportStage by remember { mutableIntStateOf(1) }
            val scope = rememberCoroutineScope()

            fun update(next: QuestState) {
                state = next
                storage.save(next)
            }
            fun notify(text: String) {
                status = text
                Toast.makeText(this@MonsterQuestActivity, text, Toast.LENGTH_SHORT).show()
            }
            fun applyIdea(idea: MonsterIdea) {
                val next = QuestRules.summon(state, idea.asWildMonster(state.room))
                if (next === state || next == state) notify("冒険中のこの状態では召喚できません")
                else {
                    update(next)
                    notify("AIモンスター「${idea.name}」を召喚しました")
                }
            }
            fun createMonster(image: ByteArray?) {
                if (aiBusy || aiKey.isBlank() || state.inBattle || state.completed ||
                    state.room == QuestRules.ROOM_COUNT) return
                val key = aiKey
                val instruction = aiDescription.ifBlank { "不思議な生き物" }
                aiBusy = true
                scope.launch {
                    try {
                        applyIdea(MonsterAi.generate(key, instruction, image))
                    } catch (e: Exception) {
                        notify("AI生成に失敗：${e.message}")
                    } finally {
                        aiBusy = false
                    }
                }
            }
            val photoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null && !aiBusy && aiKey.isNotBlank()) {
                    val key = aiKey
                    val description = aiDescription.ifBlank { "この写真をもとに独自のモンスターを作って" }
                    aiBusy = true
                    scope.launch {
                        try {
                            val jpeg = MonsterAi.preparePhoto(this@MonsterQuestActivity, uri)
                            applyIdea(MonsterAi.generate(key, description, jpeg))
                        } catch (e: Exception) {
                            notify("写真AI生成に失敗：${e.message}")
                        } finally {
                            aiBusy = false
                        }
                    }
                }
            }
            val artworkPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                val id = artImportId
                val stage = artImportStage
                artImportId = null
                if (uri != null && id != null) {
                    scope.launch {
                        runCatching {
                            artworkStore.importFromUri(id, uri,stage)
                            artworkRevision += 1
                            notify("高精細イラストを端末に保存しました")
                        }.onFailure { notify("イラスト読み込み失敗：${it.message}") }
                    }
                }
            }
            val armorImagePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                val target = imageImportTarget
                imageImportTarget = null
                if (uri != null && target != null && !aiBusy) {
                    val original = state.captures.firstOrNull { it.id == target.first }
                    if (original == null || state.inBattle) {
                        notify("対象の仲間が変更されたため取込みを中止しました")
                    } else {
                        aiBusy = true
                        scope.launch {
                            try {
                                val layer = artworkStore.importArmorLayer(uri)
                                if (state.captures.firstOrNull { it.id == original.id } == original &&
                                    !state.inBattle) {
                                    pendingImageArmor = ArmorLayerDraft(
                                        original, target.second, layer, "画像取込み")
                                } else notify("取込み中に仲間が変更されました")
                            } catch (e: Exception) {
                                notify("装備画像を読み込めません：" + (e.message ?: "形式エラー"))
                            } finally { aiBusy = false }
                        }
                    }
                }
            }
            fun generatePortrait(wild: WildMonster, style: ConceptStyle) {
                if (aiBusy || aiKey.isBlank()) return
                aiBusy = true
                val key = aiKey
                scope.launch {
                    try {
                        val image = MonsterConceptArt.generate(key, wild, style)
                        artworkStore.store(wild.id, image,wild.stage)
                        artworkRevision += 1
                        notify("${wild.species}の高精細イラストを保存しました")
                    } catch (e: Exception) {
                        notify("立ち絵の生成に失敗：${e.message}")
                    } finally {
                        aiBusy = false
                    }
                }
            }
            fun equipCaptured(id: String, parts: Map<String,String>) {
                if (state.inBattle) {
                    notify("戦闘中は装備変更できません")
                    return
                }
                runCatching {
                    val validated = MonsterArmor.validated(parts)
                    check(state.captures.any { it.id == id }) { "仲間が見つかりません" }
                    val changed = state.captures.map { member ->
                        if(member.id != id) member else {
                            val armored = member.copy(armorParts = validated)
                            armored.copy(pixels = artworkStore.glyphFor(armored))
                        }
                    }
                    update(state.copy(captures = changed))
                    artworkRevision++
                }.onFailure { notify("装備を保存できません：" + it.message) }
            }
            fun suggestCapturedArmor(wild: WildMonster) {
                if (aiBusy || aiKey.isBlank() || state.inBattle) return
                aiBusy = true
                val key = aiKey
                scope.launch {
                    try {
                        val parts = ArmorAi.suggest(key, wild)
                        if (state.captures.firstOrNull { it.id == wild.id } == wild &&
                            !state.inBattle) {
                            pendingTextArmor = wild to parts
                            notify("装備案ができました。適用前に確認してください")
                        } else notify("提案中に仲間が変更されたため破棄しました")
                    } catch(e: Exception) {
                        notify("AI装備提案に失敗：" + (e.message ?: "通信エラー"))
                    } finally { aiBusy = false }
                }
            }
            fun generateImageArmor(wild: WildMonster, slot: ArmorSlot) {
                if (aiBusy || aiKey.isBlank() || state.inBattle) return
                aiBusy = true
                val key = aiKey
                scope.launch {
                    try {
                        val raw = ArmorLayerAi.generate(
                            key, wild, slot, artworkStore.spriteFor(wild))
                        val image = artworkStore.prepareArmorLayer(raw)
                        if (state.captures.firstOrNull { it.id == wild.id } == wild &&
                            !state.inBattle) {
                            pendingImageArmor = ArmorLayerDraft(
                                wild, slot, image, "AI生成")
                            notify("AI画像を作りました。プレビューを確認してください")
                        } else notify("生成中に仲間が変更されました")
                    } catch(e: Exception) {
                        notify("AIパーツ画像を生成できません：" + (e.message ?: "通信エラー"))
                    } finally { aiBusy = false }
                }
            }
            fun removeImageArmor(id: String, slot: ArmorSlot) {
                if (state.inBattle || aiBusy) return
                val monster = state.captures.firstOrNull { it.id == id } ?: return
                val revised = monster.copy(
                    armorLayers = monster.armorLayers - slot.name)
                val withGlyph = revised.copy(pixels = artworkStore.glyphFor(revised))
                update(state.copy(captures = state.captures.map {
                    if(it.id == id) withGlyph else it
                }))
                artworkRevision++
                notify("${slot.label}の画像レイヤーを外しました")
            }
            fun exportSpriteSheet(wild: WildMonster) {
                scope.launch {
                    runCatching {
                        artworkStore.exportSpriteSheet(wild)
                        notify("${wild.species}の4コマPNGスプライトシートを写真フォルダに保存しました")
                    }.onFailure { notify("アニメ出力失敗：${it.message}") }
                }
            }
            fun exportPixelPng(wild: WildMonster, size: Int) {
                scope.launch {
                    runCatching {
                        artworkStore.exportPng(wild,size)
                        notify("${wild.species}の${size}×${size} PNGを写真フォルダへ保存しました")
                    }.onFailure { notify("PNG出力に失敗：${it.message}") }
                }
            }
            fun sendGlyphToLife(wild: WildMonster) {
                runCatching {
                    val master = artworkStore.spriteFor(wild)
                    val glyphFrames = MonsterAnimation.idle(master,wild).map { frame ->
                        GlyphProjector.project(frame.sprite) to frame.durationMs
                    }
                    val encoded=QuestProtocol.exportGlyphAnimationFrames(wild,glyphFrames)
                    val share=Intent(Intent.ACTION_SEND).apply {
                        type="application/json"
                        putExtra(Intent.EXTRA_TEXT,encoded)
                    }
                    val direct=Intent(share).apply {
                        setClassName("dev.glyphlife.app","dev.glyphlife.app.MainActivity")
                    }
                    try { startActivity(direct) }
                    catch (_:ActivityNotFoundException) {
                        startActivity(Intent.createChooser(share,"13×13 Glyphデータを共有"))
                    }
                }.onFailure { notify("Glyphデータを送れません：${it.message}") }
            }
            LaunchedEffect(incoming) {
                val raw = incoming ?: return@LaunchedEffect
                incoming = null
                runCatching {
                    val pet = QuestProtocol.parseGlyphPet(raw)
                    update(QuestRules.start(pet).copy(captures = state.captures))
                    notify("Glyph Lifeの${pet.name}を受け取りました")
                }.onFailure { notify("ペットデータを取り込めません：${it.message}") }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFDFA7), background = Color(0xFF111923),
                    surface = Color(0xFF222A34))
            ) {
                Scaffold(topBar = {
                    Surface(tonalElevation = 2.dp) {
                        Text("MONSTER QUEST  /  v2.7",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.titleMedium)
                    }
                }) { padding ->
                    QuestScreen(
                        state = state, status = status,
                        artworkStore = artworkStore, artworkRevision = artworkRevision,
                        onSendGlyph = ::sendGlyphToLife,
                        onOfflineSummon = { prototype ->
                            runCatching {
                                val spawned=MonsterPrototypeFactory.summon(prototype,state.room)
                                val next=QuestRules.summon(state,spawned)
                                if(next==state) notify("この場面では召喚できません")
                                else {
                                    update(next)
                                    notify("無料ドット絵モンスター「${spawned.species}」を召喚")
                                }
                            }.onFailure { notify("試作召喚失敗：${it.message}") }
                        },
                        onExportPng = ::exportPixelPng,
                        onExportSpriteSheet = ::exportSpriteSheet,
                        onEquip = ::equipCaptured,
                        onAiEquip = ::suggestCapturedArmor,
                        onImportLayer = { wild, slot ->
                            if(!state.inBattle && !aiBusy) {
                                imageImportTarget = wild.id to slot
                                armorImagePicker.launch("image/*")
                            }
                        },
                        onGenerateLayer = ::generateImageArmor,
                        onRemoveLayer = ::removeImageArmor,
                        onEvolve = { id ->
                            runCatching {
                                val next = EvolutionRules.evolve(state,id)
                                val repaired = next.copy(captures=next.captures.map {
                                    if(it.id != id) it
                                    else it.copy(pixels=artworkStore.glyphFor(it))
                                })
                                update(repaired)
                                artworkRevision += 1
                                notify("モンスターが進化しました。64×64の外見が変化します")
                            }.onFailure { notify("進化できません：${it.message}") }
                        },
                        onGenerateArt = ::generatePortrait,
                        onImportArt = { wild ->
                            artImportId = wild.id
                            artImportStage = wild.stage
                            artworkPicker.launch("image/*")
                        },
                        aiKey = aiKey, aiDescription = aiDescription,
                        aiBusy = aiBusy,
                        onAiKeyChange = { aiKey = it.take(256) },
                        onAiDescriptionChange = { aiDescription = it.take(250) },
                        onAiText = { createMonster(null) },
                        onAiPhoto = { photoPicker.launch("image/*") },
                        modifier = Modifier.padding(padding),
                        onMove = { x, y ->
                            if (aiBusy) return@QuestScreen
                            val nextX = state.position % 4 + x
                            val nextY = state.position / 4 + y
                            if (nextX in 0..3 && nextY in 0..3) {
                                update(QuestRules.move(state, nextY * 4 + nextX))
                            }
                        },
                        onAttack = { magic -> if (!aiBusy) update(QuestRules.attack(state, magic)) },
                        onCapture = { if (!aiBusy) update(QuestRules.capture(state)) },
                        onPotion = { if (!aiBusy) update(QuestRules.usePotion(state)) },
                        onSupport = {
                            if (!aiBusy) update(QuestRules.supportAttack(state))
                        },
                        onFuse = { a, b ->
                            runCatching {
                                val next=FusionRules.fuse(state,a,b)
                                val hybrid=next.captures.last()
                                val repaired=hybrid.copy(pixels=artworkStore.glyphFor(hybrid))
                                update(next.copy(captures=next.captures.dropLast(1)+repaired))
                                artworkRevision++
                            }
                                .onFailure { notify("合成に失敗：${it.message}") }
                        },
                        onDefend = { if (!aiBusy) update(QuestRules.defend(state)) },
                        onNextRoom = { update(QuestRules.nextRoom(state)) },
                        onEnd = { update(QuestRules.endRun(state)) },
                        onNewRun = {
                            update(QuestRules.start(state.companion).copy(captures = state.captures))
                        },
                        onSendResult = {
                            runCatching {
                                val payload = QuestProtocol.rewardJson(state)
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, payload)
                                }
                                val direct = Intent(share).apply {
                                    setClassName("dev.glyphlife.app",
                                        "dev.glyphlife.app.MainActivity")
                                }
                                try {
                                    startActivity(direct)
                                } catch (_: ActivityNotFoundException) {
                                    startActivity(Intent.createChooser(share,
                                        "Glyph Lifeに冒険結果を渡す"))
                                }
                            }.onFailure { notify("共有できません：${it.message}") }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = sharedJson(intent)
    }

    private fun sharedJson(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND &&
            (intent.type == "application/json" || intent.type == "text/plain")) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null
}

@Composable
private fun QuestScreen(
    state: QuestState,
    status: String,
    artworkStore: MonsterArtworkStore,
    artworkRevision: Int,
    onSendGlyph: (WildMonster) -> Unit,
    onOfflineSummon: (WildMonster) -> Unit,
    onExportPng: (WildMonster, Int) -> Unit,
    onExportSpriteSheet: (WildMonster) -> Unit,
    onEvolve: (String) -> Unit,
    onEquip: (String, Map<String,String>) -> Unit,
    onAiEquip: (WildMonster) -> Unit,
    onImportLayer: (WildMonster, ArmorSlot) -> Unit,
    onGenerateLayer: (WildMonster, ArmorSlot) -> Unit,
    onRemoveLayer: (String, ArmorSlot) -> Unit,
    onGenerateArt: (WildMonster, ConceptStyle) -> Unit,
    onImportArt: (WildMonster) -> Unit,
    aiKey: String,
    aiDescription: String,
    aiBusy: Boolean,
    onAiKeyChange: (String) -> Unit,
    onAiDescriptionChange: (String) -> Unit,
    onAiText: () -> Unit,
    onAiPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    onMove: (Int, Int) -> Unit,
    onAttack: (Boolean) -> Unit,
    onCapture: () -> Unit,
    onPotion: () -> Unit,
    onSupport: () -> Unit,
    onFuse: (String, String) -> Unit,
    onDefend: () -> Unit,
    onNextRoom: () -> Unit,
    onEnd: () -> Unit,
    onNewRun: () -> Unit,
    onSendResult: () -> Unit
) {
    var artStyle by remember { mutableStateOf(ConceptStyle.DARK_FANTASY) }
    var glyphPreviewId by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("探索：${state.room} / ${QuestRules.ROOM_COUNT}部屋",
            style = MaterialTheme.typography.headlineSmall)
        Text("相棒：${state.companion.name} Lv${state.companion.level} " +
            "（${state.companion.species}・${state.companion.element}）")
        Text(if (state.companion.importedFromGlyphLife) "Glyph Life連携中"
             else "体験用の相棒です。Glyph Lifeの「連携」タブから送ると変更できます。")
        val companionWild = remember(state.companion) {
            WildMonster(
                id = state.companion.id, species = state.companion.species,
                element = state.companion.element, maxHp = 100,
                attack = 10 + state.companion.level,
                stage = when {
                    state.companion.level >= 12 -> 3
                    state.companion.level >= 5 -> 2
                    else -> 1
                }
            )
        }
        MonsterArtworkView(
            monster = companionWild,
            store = artworkStore,
            revision = artworkRevision,
            modifier = Modifier.size(152.dp)
        )
        LinearProgressIndicator(
            progress = { state.playerHp / 100f }, modifier = Modifier.fillMaxWidth())
        Text("HP ${state.playerHp}/100　回復薬${state.potions}個　撃破${state.defeated}　捕獲${state.captures.size}匹")
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(state.message, modifier = Modifier.padding(12.dp))
        }
        if (!state.inBattle && !state.roomCleared && !state.completed) {
            Column(modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (y in 0..3) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (x in 0..3) {
                            val i = y * 4 + x
                            val blocked = i in state.walls
                            val current = i == state.position
                            val target = i == 15
                            Box(
                                modifier = Modifier.weight(1f).height(58.dp)
                                    .background(
                                        if (blocked) Color(0xFF10151A) else if (current) Color(0xFF576B78)
                                        else Color(0xFF303945),
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(when {
                                    current -> "自"
                                    blocked -> "■"
                                    target -> "敵"
                                    else -> "·"
                                })
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(modifier = Modifier.width(80.dp))
                Button(onClick = { onMove(0, -1) }) { Text("↑") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onMove(-1, 0) }) { Text("←") }
                Button(onClick = { onMove(0, 1) }) { Text("↓") }
                Button(onClick = { onMove(1, 0) }) { Text("→") }
            }
        }
        if (state.inBattle) {
            val enemy = state.enemy
            if (enemy != null) {
                Text("${if (enemy.isBoss) "★ BOSS ★  " else ""}${enemy.species}（${enemy.element}）が出現！",
                    style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(
                    progress = { state.enemyHp.toFloat() / enemy.maxHp }, modifier = Modifier.fillMaxWidth())
                Text("敵HP：${state.enemyHp}/${enemy.maxHp}")
                if (enemy.description.isNotBlank()) {
                    Text(enemy.description, style = MaterialTheme.typography.bodyMedium)
                }
                MonsterArtworkView(
                    monster = enemy,
                    store = artworkStore,
                    revision = artworkRevision,
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
                Text("本編：64×64カラードット絵（図鑑・戦闘では256×256に等倍系拡大）。" +
                    if (artworkStore.hasArt(enemy.id,enemy.stage))
                        " カスタム画像から変換した素材を使用中。"
                    else " オフラインの専用デザインを使用中。",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        glyphPreviewId = if (glyphPreviewId == enemy.id) null else enemy.id
                    }) { Text("Glyph用13×13を確認") }
                    OutlinedButton(onClick = { onSendGlyph(enemy) }) {
                        Text("GlyphLIFEへ送る")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onExportPng(enemy,64) }) { Text("64px PNG保存") }
                    OutlinedButton(onClick = { onExportPng(enemy,256) }) { Text("256px PNG保存") }
                }
                OutlinedButton(onClick = { onExportSpriteSheet(enemy) }) {
                    Text("64px×4コマのアニメPNG保存")
                }
                if (glyphPreviewId == enemy.id) {
                    MonsterPortrait(
                        pixels = artworkStore.glyphFor(enemy),
                        element = enemy.element, modifier = Modifier.size(164.dp)
                    )
                    Text("64×64の待機4コマをそれぞれ13×13へ変換し、実LED137点のマスクを適用。" +
                        " GlyphLIFEではアニメ4コマの上書き確認が必要です。",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("モンスタードット絵生成（任意の有料API）",
                    style = MaterialTheme.typography.titleMedium)
                Text("画像生成は通常のモンスターAIと別のAPI呼び出しです。Gemini 3.1 Flash Imageは" +
                    "無料枠なし。1K画像1枚の出力料金は約$0.067＋入力料金。課金設定のあるAPIキーが必要です。" +
                    "画像が気に入らないときは別のテイストで再生成できます。既存の画像は保存成功まで維持されます。",
                    style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ConceptStyle.entries.forEach { candidate ->
                        FilterChip(
                            selected = artStyle == candidate,
                            onClick = { artStyle = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !aiBusy && aiKey.isNotBlank(),
                        onClick = { onGenerateArt(enemy, artStyle) }
                    ) { Text(if (aiBusy) "処理中…" else "64×64カラー素材をAI生成") }
                    OutlinedButton(
                        enabled = !aiBusy,
                        onClick = { onImportArt(enemy) }
                    ) { Text("持っている絵を使う") }
                }
                Text("外部画像も64×64・最大16色のPNGへ変換。生成と読込は現在の進化段階に保存され、" +
                    "高解像度図鑑表示は256×256へ輪郭を保って拡大します。",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAttack(false) }) { Text("攻撃") }
                    Button(onClick = { onAttack(true) }) { Text("属性魔法") }
                    OutlinedButton(onClick = onCapture, enabled = !enemy.isBoss) { Text("捕獲") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDefend) { Text("防御") }
                    OutlinedButton(enabled = state.potions > 0, onClick = onPotion) {
                        Text("回復薬（${state.potions}）")
                    }
                }
                if (state.captures.isNotEmpty()) {
                    OutlinedButton(
                        enabled = !state.supportUsedInBattle,
                        onClick = onSupport
                    ) {
                        Text(if (state.supportUsedInBattle) "援護は使用済み"
                            else "仲間の援護：${state.captures.last().species}")
                    }
                    Text("捕獲した最後の仲間が1戦に1回、援護攻撃します。",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.roomCleared && !state.completed) {
            Button(onClick = onNextRoom) { Text("次の部屋へ") }
        }
        if (state.defeated > 0 && state.rewardEventId == null) {
            OutlinedButton(onClick = onEnd) { Text("冒険を終えて報酬確定") }
        }
        if (state.rewardEventId != null && state.companion.importedFromGlyphLife) {
            Button(onClick = onSendResult) { Text("報酬をGlyph Lifeへ送る") }
            Text("経験値+${state.earnedXp}、なかよし度+${state.earnedHappiness}。" +
                 "同じ報酬の再送はGlyph Life側で拒否されます。")
        }
        if (state.completed) {
            OutlinedButton(onClick = onNewRun) { Text("新しい冒険を始める") }
        }
        MonsterDesignerPanel(
            enabled = !state.inBattle && !state.completed &&
                !state.roomCleared && state.room < QuestRules.ROOM_COUNT,
            store = artworkStore,
            revision = artworkRevision,
            onSummon = onOfflineSummon,
            onExportPng = onExportPng
        )
        HorizontalDivider()
        Text("AIモンスター召喚（任意）", style = MaterialTheme.typography.titleMedium)
        Text("Gemini APIキーを入力すると文章からモンスターを生成できます。" +
            "写真を選ぶと、縮小したJPEG画像をGoogleに送信します。キーは保存しません。" +
            "料金・無料枠・データの扱いはGoogleの条件に従います。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = aiKey, onValueChange = onAiKeyChange,
            label = { Text("Gemini APIキー（この起動中のみ）") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = aiDescription, onValueChange = onAiDescriptionChange,
            label = { Text("モンスターのイメージ") },
            placeholder = { Text("例：雷と機械の小さなドラゴン") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !aiBusy && aiKey.isNotBlank() && aiDescription.isNotBlank() &&
                    !state.inBattle && !state.completed && !state.roomCleared &&
                    state.room < QuestRules.ROOM_COUNT,
                onClick = onAiText
            ) { Text("AIで召喚") }
            OutlinedButton(
                enabled = !aiBusy && aiKey.isNotBlank() &&
                    !state.inBattle && !state.completed && !state.roomCleared &&
                    state.room < QuestRules.ROOM_COUNT,
                onClick = onAiPhoto
            ) { Text("写真からAI召喚") }
        }
        if (aiBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        CaptureLab(
            captured = state.captures,
            inBattle = state.inBattle,
            defeated = state.defeated,
            artworkStore = artworkStore,
            artworkRevision = artworkRevision,
            onEvolve = onEvolve,
            onEquip = onEquip,
            onAiEquip = onAiEquip,
            allowAiEquip = aiKey.isNotBlank() && !aiBusy,
            onImportLayer = onImportLayer,
            onGenerateLayer = onGenerateLayer,
            onRemoveLayer = onRemoveLayer,
            imageBusy = aiBusy,
            onSendGlyph = onSendGlyph,
            onExportPng = onExportPng,
            onExportSpriteSheet = onExportSpriteSheet,
            onFuse = onFuse
        )
        Text("状態：$status", style = MaterialTheme.typography.bodySmall)
        Text(
            "ゲーム本編は64×64カラーPNGが正本で、13×13はGlyph送信時の派生表示です。" +
                "AI画像生成APIの実機通信は未検証。オンライン対戦と署名付き報酬は未実装です。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
