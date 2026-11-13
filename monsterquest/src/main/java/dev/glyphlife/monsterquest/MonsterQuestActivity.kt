package dev.glyphlife.monsterquest

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import dev.glyphlife.monsterquest.ui.MonsterBrightnessPreview
import dev.glyphlife.monsterquest.ui.MonsterArtworkView
import dev.glyphlife.monsterquest.ui.CaptureLab
import dev.glyphlife.monsterquest.ui.QuestTab
import dev.glyphlife.monsterquest.ui.QuestBottomNavigation
import dev.glyphlife.monsterquest.ui.QuestHome
import dev.glyphlife.monsterquest.ui.TapExplorationScreen
import dev.glyphlife.monsterquest.ui.QuestSummon
import dev.glyphlife.monsterquest.ui.QuestFuture
import dev.glyphlife.monsterquest.ui.MonsterDesignerPanel
import dev.glyphlife.monsterquest.ui.MonsterStaticSprite
import dev.glyphlife.monsterquest.ui.ArmorPlacementControls
import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterAnimation
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.pixel.MonsterArmor
import dev.glyphlife.monsterquest.ai.ArmorAi
import dev.glyphlife.monsterquest.ai.ArmorImageAi
import dev.glyphlife.monsterquest.pixel.ArmorSlot
import dev.glyphlife.monsterquest.pixel.CustomArmorLayers
import dev.glyphlife.monsterquest.pixel.ArmorOffset
import dev.glyphlife.monsterquest.pixel.ArmorPlacementDraft
import kotlinx.coroutines.launch

private data class PendingArmorStyles(
    val monster: WildMonster,
    val parts: Map<String,String>
)

private data class PendingPartImage(
    val monster: WildMonster,
    val slot: ArmorSlot,
    val assetId: String,
    /** Reused library assets are SHARED and must never be discarded on cancel. */
    val newlyCreated: Boolean = true
)

class MonsterQuestActivity : ComponentActivity() {
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = sharedJson(intent)
        val storage = QuestStorage(this)
        val artworkStore = MonsterArtworkStore(this)
        setContent {
            var state by remember {
                mutableStateOf(storage.load().let { saved ->
                    saved.copy(
                        inBattle=saved.tapRun.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS),
                        roomCleared=false,completed=false
                    )
                })
            }
            var selectedTab by remember { mutableStateOf(QuestTab.HOME) }
            var aiArtEnabled by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("オフラインで遊べます") }
            var aiKey by remember { mutableStateOf("") }
            var aiDescription by remember { mutableStateOf("") }
            var aiBusy by remember { mutableStateOf(false) }
            var artworkRevision by remember { mutableIntStateOf(0) }
            var artImportId by remember { mutableStateOf<String?>(null) }
            var artImportStage by remember { mutableIntStateOf(1) }
            var partImportTarget by remember { mutableStateOf<Pair<WildMonster, ArmorSlot>?>(null) }
            var pendingPartImage by remember { mutableStateOf<PendingPartImage?>(null) }
            var pendingArmorStyles by remember {
                mutableStateOf<PendingArmorStyles?>(null)
            }
            val scope = rememberCoroutineScope()

            fun update(next: QuestState) {
                val fixed=next.copy(
                    inBattle=next.tapRun.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS),
                    roomCleared=false,completed=false
                )
                state = fixed
                storage.save(fixed)
            }
            fun notify(text: String) {
                status = text
                Toast.makeText(this@MonsterQuestActivity, text, Toast.LENGTH_SHORT).show()
            }
            suspend fun applyIdea(idea: MonsterIdea) {
                if (state.summonStones<1 || state.captures.size>=100 ||
                    state.tapRun.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS)) {
                    notify("召喚結晶不足、満員、または戦闘中です")
                    return
                }
                val candidate=idea.asWildMonster(1)
                val next=TapAdventure.registerSummon(state,candidate,generatedByAi=true)
                if(next==state)return
                if(aiArtEnabled) {
                    // The user chose the separate external image API explicitly.
                    val sprite=next.captures.last()
                    val image=MonsterConceptArt.generate(aiKey,sprite,ConceptStyle.GBA_PIXEL)
                    artworkStore.store(sprite.id,image,1)
                    artworkRevision++
                }
                update(next)
                notify("AI召喚：「"+idea.name+"」を仲間と図鑑に登録しました")
            }
            fun createMonster(image: ByteArray?) {
                if (aiBusy || aiKey.isBlank() || state.summonStones<=0 ||
                    state.captures.size>=100 ||
                    state.tapRun.phase in listOf(TapPhase.FIGHT,TapPhase.BOSS)) return
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
                if (uri != null && !aiBusy && aiKey.isNotBlank() &&
                    state.summonStones>0 && state.captures.size<100 &&
                    state.tapRun.phase !in listOf(TapPhase.FIGHT,TapPhase.BOSS)) {
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
            val partPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                val request = partImportTarget
                partImportTarget = null
                if (uri != null && request != null && !aiBusy) {
                    aiBusy = true
                    scope.launch {
                        try {
                            val id = artworkStore.importArmorLayer(uri)
                            if (!state.inBattle && state.captures.any { it == request.first }) {
                                pendingPartImage?.let { old ->
                                    if (old.newlyCreated) artworkStore.discardUnassignedLayer(old.assetId)
                                }
                                pendingPartImage = PendingPartImage(request.first, request.second, id)
                                notify("装備画像を読み込みました。装着前に確認してください")
                            } else {
                                artworkStore.discardUnassignedLayer(id)
                                notify("仲間の状態が変わったので画像を破棄しました")
                            }
                        } catch (e: Exception) {
                            notify("装備画像の読み込みに失敗：${e.message}")
                        } finally { aiBusy = false }
                    }
                }
            }

            fun generatePartImage(wild: WildMonster, slot: ArmorSlot) {
                if (aiBusy || aiKey.isBlank() || state.inBattle) return
                aiBusy = true
                val key = aiKey
                scope.launch {
                    try {
                        // The user explicitly requested this generated part.
                        // Send a 512px render of the currently equipped monster
                        // as visual context; no photo library access is involved.
                        val referencePng = artworkStore.referencePng(wild)
                        val bytes = ArmorImageAi.generate(key, wild, slot, referencePng)
                        val id = artworkStore.storeArmorLayer(bytes)
                        if (!state.inBattle && state.captures.any { it == wild }) {
                            pendingPartImage?.let { old ->
                                if (old.newlyCreated) artworkStore.discardUnassignedLayer(old.assetId)
                            }
                            pendingPartImage = PendingPartImage(wild, slot, id)
                            notify("AIパーツ画像が完成しました。装着前に確認してください")
                        } else {
                            artworkStore.discardUnassignedLayer(id)
                            notify("仲間の状態が変わったため装備案を破棄しました")
                        }
                    } catch(e: Exception) {
                        notify("パーツ画像AIに失敗：${e.message}")
                    } finally { aiBusy = false }
                }
            }

            /**
             * Reuse an existing immutable asset without another paid API call.
             * The chosen ID is shown in the SAME confirmation dialog as a new
             * AI/imported layer, but cancellation never deletes this shared PNG.
             */
            fun reuseSavedPart(wild: WildMonster, slot: ArmorSlot, id: String) {
                if (state.inBattle || aiBusy) return
                val current = state.captures.firstOrNull { it.id == wild.id }
                if (current != wild) {
                    notify("仲間の状態が変わったため選択できません")
                    return
                }
                if (id !in artworkStore.listArmorLayerIds() ||
                    artworkStore.loadArmorLayer(id) == null) {
                    notify("保存済みパーツが見つからないか破損しています")
                    return
                }
                pendingPartImage?.let { old ->
                    if (old.newlyCreated) artworkStore.discardUnassignedLayer(old.assetId)
                }
                pendingPartImage = PendingPartImage(
                    monster = current, slot = slot, assetId = id,
                    newlyCreated = false
                )
                notify("保存済みパーツを選択しました。装着前に確認できます")
            }

            fun removeCustomPart(wild: WildMonster, slot: ArmorSlot) {
                if (state.inBattle) return
                val current = state.captures.firstOrNull { it.id == wild.id }
                if (current != wild) {
                    notify("仲間の状態が変わりました。選び直してください")
                    return
                }
                runCatching {
                    val updated = current.copy(
                        customArmorParts = current.customArmorParts - slot.name,
                        customArmorOffsets = current.customArmorOffsets - slot.name
                    ).let { it.copy(pixels = artworkStore.glyphFor(it)) }
                    update(state.copy(captures = state.captures.map {
                        if (it.id == wild.id) updated else it
                    }))
                    artworkRevision++
                    notify("${wild.species}の${slot.label}画像を解除しました")
                }.onFailure { notify("解除に失敗：${it.message}") }
            }

            fun setCustomPlacement(
                wild: WildMonster, slot: ArmorSlot, placement: ArmorOffset
            ) {
                if (state.inBattle || aiBusy) return
                val current = state.captures.firstOrNull { it.id == wild.id }
                if (current != wild) {
                    notify("仲間が変更されています。もう一度位置を調整してください")
                    return
                }
                runCatching {
                    val updated = ArmorPlacementDraft.reposition(current, slot, placement)
                        .let { it.copy(pixels = artworkStore.glyphFor(it)) }
                    update(state.copy(captures = state.captures.map {
                        if (it.id == current.id) updated else it
                    }))
                    artworkRevision++
                    notify("${wild.species}の${slot.label}の位置と大きさを保存しました")
                }.onFailure { notify("装備位置を保存できません：${it.message}") }
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
                        if(!state.inBattle && state.captures.any { it == wild }) {
                            pendingArmorStyles = PendingArmorStyles(wild, parts)
                            notify("AI装備案を作成しました。確認後に適用できます")
                        } else {
                            notify("仲間の状態が変わったため装備案を破棄しました")
                        }
                    } catch(e: Exception) {
                        notify("AI装備提案に失敗：" + (e.message ?: "通信エラー"))
                    } finally { aiBusy = false }
                }
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
                    val idleFrames = MonsterAnimation.idle(master, wild)
                    val levels = idleFrames.mapIndexed { pose, frame ->
                        artworkStore.glyphBrightnessFrameFor(wild, frame.sprite, pose)
                    }
                    val glyphFrames = idleFrames.mapIndexed { pose, frame ->
                        dev.glyphlife.monsterquest.pixel.FullBodyGlyphProjector
                            .asBinary(levels[pose]) to frame.durationMs
                    }
                    val encoded = QuestProtocol.exportGlyphAnimationFrames(
                        wild, glyphFrames, brightnessFrames = levels
                    )
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
                    update(state.copy(companion=pet))
                    notify("Glyph Lifeの${pet.name}を受け取りました")
                }.onFailure { notify("ペットデータを取り込めません：${it.message}") }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFDFA7), background = Color(0xFF111923),
                    surface = Color(0xFF222A34))
            ) {
                Scaffold(
                    topBar={
                        Surface(tonalElevation=2.dp,color=Color(0xFF121B30)) {
                            Text("MONSTER QUEST / v4.0",Modifier.padding(14.dp),
                                style=MaterialTheme.typography.titleMedium,
                                color=Color(0xFFFFD990))
                        }
                    },
                    bottomBar={
                        QuestBottomNavigation(selectedTab) { selectedTab=it }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when(selectedTab) {
                            QuestTab.HOME -> QuestHome(
                                state=state,store=artworkStore,revision=artworkRevision,
                                onSelectParty={ memberId ->
                                    if(!state.inBattle) update(TapAdventure.selectParty(state,memberId))
                                    else notify("戦闘中は編成を変更できません")
                                }
                            ) {
                                CaptureLab(
                                    captured=state.captures,inBattle=state.inBattle,
                                    aiBusy=aiBusy,defeated=state.defeated,
                                    artworkStore=artworkStore,artworkRevision=artworkRevision,
                                    onEvolve={ id ->
                                        runCatching {
                                            val result=EvolutionRules.evolve(state,id)
                                            val changed=result.captures.map { member ->
                                                if(member.id==id)member.copy(
                                                    pixels=artworkStore.glyphFor(member))
                                                else member
                                            }
                                            update(result.copy(captures=changed))
                                            artworkRevision++
                                            notify("進化しました！")
                                        }.onFailure { notify("進化失敗："+it.message) }
                                    },
                                    onEquip=::equipCaptured,
                                    onAiEquip=::suggestCapturedArmor,
                                    allowAiEquip=aiKey.isNotBlank()&&!aiBusy,
                                    onGeneratePart=::generatePartImage,
                                    onImportPart={ monster,slot ->
                                        if(!aiBusy && !state.inBattle) {
                                            partImportTarget=monster to slot
                                            partPicker.launch("image/png")
                                        }
                                    },
                                    onReusePart=::reuseSavedPart,
                                    onRemovePart=::removeCustomPart,
                                    onSetPlacement=::setCustomPlacement,
                                    onSendGlyph=::sendGlyphToLife,
                                    onExportPng=::exportPixelPng,
                                    onExportSpriteSheet=::exportSpriteSheet,
                                    onGenerateArt={ wild ->
                                        generatePortrait(wild,ConceptStyle.GBA_PIXEL)
                                    },
                                    onImportArt={ wild ->
                                        if(!state.inBattle) {
                                            artImportId=wild.id
                                            artImportStage=wild.stage
                                            artworkPicker.launch("image/*")
                                        }
                                    },
                                    onFuse={ a,b ->
                                        runCatching {
                                            val merged=FusionRules.fuse(state,a,b)
                                            val last=merged.captures.last()
                                            val adjusted=last.copy(
                                                pixels=artworkStore.glyphFor(last))
                                            val owned=merged.captures.dropLast(1)+adjusted
                                            update(merged.copy(
                                                captures=owned,
                                                partyIds=state.partyIds.filter { id ->
                                                    owned.any { it.id==id }
                                                }
                                            ))
                                            artworkRevision++
                                            notify("2匹を合成しました")
                                        }.onFailure { notify("合成失敗："+it.message) }
                                    }
                                )
                            }
                            QuestTab.EXPLORE -> TapExplorationScreen(
                                state=state,store=artworkStore,revision=artworkRevision,
                                onTap={
                                    if(!aiBusy)update(TapAdventure.tap(state))
                                },
                                onDodge={ side -> update(TapAdventure.dodge(state,side)) },
                                onCapture={ update(TapAdventure.capture(state)) },
                                onHeal={ update(TapAdventure.heal(state)) },
                                onBranch={ route ->
                                    update(TapAdventure.branch(state,route))
                                }
                            )
                            QuestTab.SUMMON -> QuestSummon(
                                state=state,aiKey=aiKey,
                                aiDescription=aiDescription,aiBusy=aiBusy,
                                aiArtEnabled=aiArtEnabled,
                                onAiArtEnabled={ aiArtEnabled=it },
                                onAiKey={ aiKey=it.take(256) },
                                onDescription={ aiDescription=it.take(250) },
                                onAiText={ createMonster(null) },
                                onAiPhoto={ photoPicker.launch("image/*") },
                                onOffline={
                                    if(!aiBusy && !state.inBattle && state.summonStones>0) {
                                        update(TapAdventure.offlineSummon(state))
                                        notify("オフライン召喚！ ホームに仲間が追加されました")
                                    }
                                }
                            ) {
                                MonsterDesignerPanel(
                                    enabled=!state.inBattle && !aiBusy &&
                                        state.summonStones>0 && state.captures.size<100,
                                    store=artworkStore,revision=artworkRevision,
                                    onSummon={ prototype ->
                                        runCatching {
                                            check(!state.inBattle && state.summonStones>0)
                                            val monster=MonsterPrototypeFactory.summon(
                                                prototype,1)
                                            update(TapAdventure.registerSummon(state,monster))
                                            notify("ドット絵モンスターを召喚しました")
                                        }.onFailure { notify("召喚失敗："+it.message) }
                                    },
                                    onExportPng=::exportPixelPng
                                )
                            }
                            QuestTab.FUTURE -> QuestFuture()
                        }
                    }
                }
                val styles = pendingArmorStyles
                if (styles != null && pendingPartImage == null) {
                    val current = state.captures.firstOrNull { it.id == styles.monster.id }
                    if (current == null || current != styles.monster || state.inBattle) {
                        LaunchedEffect(styles, current, state.inBattle) {
                            pendingArmorStyles = null
                        }
                    } else {
                        val predicted = remember(styles, current) {
                            current.copy(armorParts = MonsterArmor.validated(styles.parts))
                        }
                        AlertDialog(
                            onDismissRequest = { pendingArmorStyles = null },
                            title = { Text("AIアーマー案を確認") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("候補を承認するまで${current.species}の装備は変更しません")
                                    MonsterStaticSprite(predicted, Modifier.size(192.dp),
                                        store = artworkStore, revision = artworkRevision)
                                    dev.glyphlife.monsterquest.pixel.ArmorSlot.entries.forEach { slot ->
                                        val style = styles.parts[slot.name] ?: "なし"
                                        Text("${slot.label}（進化${slot.minStage}〜）：$style")
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if(!state.inBattle &&
                                        state.captures.firstOrNull { it.id == current.id } == current) {
                                        equipCaptured(current.id, styles.parts)
                                        notify("AIの装備案を適用しました")
                                    } else notify("状態が変わったため装備を適用できません")
                                    pendingArmorStyles = null
                                }) { Text("装備案を適用") }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingArmorStyles = null }) {
                                    Text("キャンセル")
                                }
                            }
                        )
                    }
                }
                val pending = pendingPartImage
                if (pending != null) {
                    // Draft lives only in this confirmation panel; cancel and
                    // stale-target paths never alter saved placement metadata.
                    var draftPosition by remember(pending.assetId, pending.slot) {
                        mutableStateOf(ArmorOffset())
                    }
                    val current = state.captures.firstOrNull { it.id == pending.monster.id }
                    if (current == null || current != pending.monster || state.inBattle) {
                        LaunchedEffect(pending, state.inBattle, current) {
                            if (pending.newlyCreated) artworkStore.discardUnassignedLayer(pending.assetId)
                            pendingPartImage = null
                        }
                    } else {
                        val candidate = remember(pending, current, draftPosition) {
                            ArmorPlacementDraft.withAsset(
                                current, pending.slot, pending.assetId, draftPosition)
                        }
                        val visibleCandidate = remember(candidate, pending.slot) {
                            ArmorPlacementDraft.visiblePreview(candidate, pending.slot)
                        }
                        AlertDialog(
                            onDismissRequest = {
                                if (pending.newlyCreated) artworkStore.discardUnassignedLayer(pending.assetId)
                                pendingPartImage = null
                            },
                            title = { Text("装備画像を確認") },
                            text = {
                                Column(
                                    modifier = Modifier.heightIn(max = 530.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("${current.species}：${pending.slot.label}の画像パーツ")
                                    MonsterStaticSprite(
                                        visibleCandidate, Modifier.size(192.dp),
                                        store = artworkStore,
                                        revision = artworkRevision
                                    )
                                    Text("元の64×64素体に重ねた完成予想です。" +
                                        " 1px単位で位置、5%単位で大きさを調整できます。")
                                    ArmorPlacementControls(
                                        value = draftPosition,
                                        onChange = { draftPosition = it },
                                        enabled = !state.inBattle && !aiBusy
                                    )
                                    if (pending.slot.minStage > current.stage) {
                                        Text("進化${pending.slot.minStage}に到達したときの" +
                                            "完成予想を表示しています。装着しても今すぐ進化はしません。")
                                    }
                                    Text("透明PNGかマゼンタ背景の画像のみ。AI生成結果は" +
                                        "実際の装着位置を確認してください。",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (state.inBattle ||
                                        state.captures.firstOrNull { it.id == current.id } != current) {
                                        if (pending.newlyCreated) artworkStore.discardUnassignedLayer(pending.assetId)
                                        notify("仲間が更新されたため適用できません")
                                    } else {
                                        runCatching {
                                            val valid = CustomArmorLayers.validated(
                                                candidate.customArmorParts)
                                            val armored = candidate.copy(
                                                customArmorParts = valid
                                            ).let { it.copy(pixels = artworkStore.glyphFor(it)) }
                                            update(state.copy(captures = state.captures.map {
                                                if(it.id == current.id) armored else it
                                            }))
                                            artworkRevision++
                                            notify("画像パーツを装着しました")
                                        }.onFailure { notify("装着に失敗：${it.message}") }
                                    }
                                    pendingPartImage = null
                                }) { Text("この画像を装着") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    if (pending.newlyCreated) artworkStore.discardUnassignedLayer(pending.assetId)
                                    pendingPartImage = null
                                }) { Text("キャンセル") }
                            }
                        )
                    }
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
    onGeneratePart: (WildMonster, ArmorSlot) -> Unit,
    onImportPart: (WildMonster, ArmorSlot) -> Unit,
    onReusePart: (WildMonster, ArmorSlot, String) -> Unit,
    onRemovePart: (WildMonster, ArmorSlot) -> Unit,
    onSetPlacement: (WildMonster, ArmorSlot, ArmorOffset) -> Unit,
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
    var artStyle by remember { mutableStateOf(ConceptStyle.GBA_PIXEL) }
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
                artFamily = state.companion.gameArtFamily(),
                stage = when {
                    state.companion.level >= 12 -> 3
                    state.companion.level >= 5 -> 2
                    else -> 1
                }
            )
        }
        Text("全身デザイン（64×64原画を拡大表示）",
            style = MaterialTheme.typography.labelMedium)
        MonsterArtworkView(
            monster = companionWild,
            store = artworkStore,
            revision = artworkRevision,
            modifier = Modifier.fillMaxWidth().height(280.dp)
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
                    }) { Text("背面Glyph用アイコンを確認") }
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
                    MonsterBrightnessPreview(
                        levels = artworkStore.glyphBrightnessFor(enemy).toList(),
                        modifier = Modifier.size(180.dp)
                    )
                    Text("背面Glyphは64×64の全身素材から13×13へ縮小し、" +
                        "白色LEDの光度0〜255でモノクロ階調を表現します。" +
                        "角・脚・尾が途切れないよう表示領域内へ収めます。" +
                        " GlyphLIFE側で読み込む際は上書き確認が必要です。",
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
        Text("AIが生成するのは名前・属性・骨格などの設定情報です。" +
            "見た目は汎用64×64ドット絵で描かれるため、AIの文章と完全には一致しません。" +
            "固有画像を使う場合は戦闘画面から別途画像生成・取り込みが必要です。" +
            "写真を選ぶと縮小したJPEG画像をGoogleに送信します。" +
            "キーは保存しません。料金・無料枠・データの扱いはGoogleの条件に従います。",
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
            ) { Text("AI設定で召喚") }
            OutlinedButton(
                enabled = !aiBusy && aiKey.isNotBlank() &&
                    !state.inBattle && !state.completed && !state.roomCleared &&
                    state.room < QuestRules.ROOM_COUNT,
                onClick = onAiPhoto
            ) { Text("写真からAI設定") }
        }
        if (aiBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        CaptureLab(
            captured = state.captures,
            inBattle = state.inBattle,
            aiBusy = aiBusy,
            defeated = state.defeated,
            artworkStore = artworkStore,
            artworkRevision = artworkRevision,
            onEvolve = onEvolve,
            onEquip = onEquip,
            onAiEquip = onAiEquip,
            allowAiEquip = aiKey.isNotBlank() && !aiBusy,
            onGeneratePart = onGeneratePart,
            onImportPart = onImportPart,
            onReusePart = onReusePart,
            onRemovePart = onRemovePart,
            onSetPlacement = onSetPlacement,
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
