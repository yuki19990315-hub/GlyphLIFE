package dev.glyphlife.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.SystemClock
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.ai.GeminiClient
import dev.glyphlife.app.data.*
import dev.glyphlife.app.glyph.createGlyphBridge
import dev.glyphlife.app.media.GlyphImage
import dev.glyphlife.app.model.*
import dev.glyphlife.app.nfc.RingNfcStore
import dev.glyphlife.app.nfc.RingTagFingerprint
import dev.glyphlife.app.ui.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    private var incomingQuestPayload by mutableStateOf<String?>(null)
    private val ringStore by lazy { RingNfcStore(this) }
    private val adapter by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var readerResumed = false
    private var ringEnabled by mutableStateOf(false)
    private var ringEnrolling by mutableStateOf(false)
    private var ringStatus by mutableStateOf("未登録")
    private var ringScans by mutableIntStateOf(0)
    private var lastScanMs = 0L

    private fun refreshRingReader() {
        val nfc = adapter ?: return
        if (!readerResumed) return
        if (!ringEnabled && !ringEnrolling) {
            runCatching { nfc.disableReaderMode(this) }
            return
        }
        if (!nfc.isEnabled) {
            ringStatus = "端末のNFC設定をONにしてください"
            return
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        try {
            nfc.enableReaderMode(this, { tag ->
                val uid = tag.id
                if (uid.isEmpty()) return@enableReaderMode
                val fingerprint = RingTagFingerprint.fromUid(uid)
                runOnUiThread {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastScanMs >= 1500) {
                        lastScanMs = now
                        if (ringEnrolling) {
                            ringStore.enrolledFingerprint = fingerprint
                            ringStore.enabled = true
                            ringEnabled = true
                            ringEnrolling = false
                            ringStatus = "リングを登録しました（前景で有効）"
                            refreshRingReader()
                        } else if (ringEnabled && ringStore.enrolledFingerprint == fingerprint) {
                            ringScans += 1
                            ringStatus = "リングを認識：モードを切り替えました"
                        } else {
                            ringStatus = "登録済みリングとは一致しません"
                        }
                    }
                }
            }, flags, null)
        } catch (e: Exception) {
            ringStatus = "NFCリーダーを起動できません：${e.message}"
        }
    }

    override fun onResume() {
        super.onResume()
        readerResumed = true
        refreshRingReader()
    }

    override fun onPause() {
        readerResumed = false
        runCatching { adapter?.disableReaderMode(this) }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingQuestPayload = sharedText(intent)
    }

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND &&
            (intent.type == "application/json" || intent.type == "text/plain"))
            intent.getStringExtra(Intent.EXTRA_TEXT)
        else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingQuestPayload = sharedText(intent)
        ringEnabled = ringStore.enabled
        ringStatus = if (ringStore.isEnrolled()) "登録済み" else "まだリングを登録していません"
        val repo = PetRepository(this)
        val gallery = ArtworkLibrary(this)
        val toyPrefs = GlyphToyPreferences(this)
        val glyph = createGlyphBridge(this)
        setContent {
            var pet by remember { mutableStateOf(repo.loadPet()) }
            var roster by remember { mutableStateOf(repo.loadPets()) }
            var artworks by remember { mutableStateOf(gallery.list()) }
            var animation by remember { mutableStateOf(repo.loadAnimation(pet.id)) }
            var chat by remember { mutableStateOf(repo.loadChat(pet.id)) }
            var tab by remember { mutableIntStateOf(0) }
            var toyMode by remember { mutableStateOf(toyPrefs.mode) }
            var clockNow by remember { mutableStateOf(LocalDateTime.now()) }
            LaunchedEffect(tab) {
                if (tab == 4) {
                    while (true) {
                        clockNow = LocalDateTime.now()
                        delay(20_000)
                    }
                }
            }
            var fluidPreview by remember { mutableStateOf(GlyphFluid.initial()) }
            var glyphStatus by remember { mutableStateOf("未接続") }
            var questStatus by remember { mutableStateOf("取り込み待ち") }
            var aiKey by remember { mutableStateOf("") }
            var aiBusy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(ringScans) {
                if (ringScans > 0) {
                    val mode = ringScans % 3
                    tab = when (mode) { 1 -> 1; 2 -> 2; else -> 0 }
                    if (glyph.isSupported) {
                        val frame = when (mode) {
                            1 -> animation.current.pixels
                            2 -> fluidPreview
                            else -> PetSpriteGenerator.create(pet).frames.first().pixels
                        }
                        runCatching {
                            glyph.connect { glyphStatus = it }
                            glyph.showFrame(frame)
                        }.onFailure { ringStatus = "Glyph出力失敗：${it.message}" }
                    }
                }
            }
            LaunchedEffect(incomingQuestPayload) {
                val raw = incomingQuestPayload ?: return@LaunchedEffect
                incomingQuestPayload = null
                runCatching {
                    val event = QuestResult.parse(raw)
                    val target = repo.loadPets().firstOrNull { it.id == event.petId }
                        ?: throw IllegalArgumentException("対象ペットが見つかりません")
                    val updated = repo.applyQuestResult(raw, target)
                    roster = repo.loadPets()
                    if (pet.id == updated.id) pet = updated
                    questStatus = "${updated.name}の冒険結果を反映しました"
                }.onFailure { e -> questStatus = "受信失敗：${e.message}" }
                tab = 4
            }
            val questPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) runCatching {
                    val payload = readTextFromUri(uri, 65_536)
                    pet = repo.applyQuestResult(payload, pet)
                    roster = repo.loadPets()
                    questStatus = "冒険結果を反映しました"
                }.onFailure { e -> questStatus = "反映できません：${e.message}" }
            }

            fun notify(text: String) { Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show() }
            fun updateAnimation(next: GlyphAnimation) {
                animation = next
                repo.saveAnimation(next, pet.id)
            }
            fun shareJson(payload: String) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, payload)
                }
                startActivity(Intent.createChooser(intent, "JSONデータを共有"))
            }
            fun sendToMonsterQuest(payload: String) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, payload)
                    setClassName("dev.glyphlife.monsterquest",
                        "dev.glyphlife.monsterquest.MonsterQuestActivity")
                }
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    // Monster Quest is optional. Let the user save/share the JSON otherwise.
                    shareJson(payload)
                }
            }

            DisposableEffect(glyph) { onDispose { glyph.close() } }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFDFEE9B),
                background = Color(0xFF111414),
                surface = Color(0xFF1B2020)
            )) {
                Scaffold(
                    topBar = {
                        Surface(tonalElevation = 2.dp) {
                            Text("GLYPH LIFE  /  v0.12",
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0, onClick = { tab = 0 },
                                icon = { Text("◉") }, label = { Text("Pet") })
                            NavigationBarItem(
                                selected = tab == 1, onClick = { tab = 1 },
                                icon = { Text("▦") }, label = { Text("Creator") })
                            NavigationBarItem(
                                selected = tab == 2, onClick = { tab = 2 },
                                icon = { Text("≈") }, label = { Text("Fluid") })
                            NavigationBarItem(
                                selected = tab == 3, onClick = { tab = 3 },
                                icon = { Text("▣") }, label = { Text("作品") })
                            NavigationBarItem(
                                selected = tab == 4, onClick = { tab = 4 },
                                icon = { Text("⇄") }, label = { Text("連携") })
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> PetScreen(
                            pet = pet, roster = roster, animation = animation, chat = chat,
                            modifier = Modifier.padding(padding),
                            onPetChange = { changed ->
                                pet = changed
                                repo.savePet(changed)
                                roster = repo.loadPets()
                            },
                            onHatch = {
                                runCatching {
                                    pet = repo.addPet(PetFactory.hatch())
                                    roster = repo.loadPets()
                                    animation = repo.loadAnimation(pet.id)
                                    chat = repo.loadChat(pet.id)
                                }.onFailure { notify("ペット追加失敗：${it.message}") }
                            },
                            onSelectPet = { id ->
                                runCatching {
                                    pet = repo.selectPet(id)
                                    animation = repo.loadAnimation(pet.id)
                                    chat = repo.loadChat(pet.id)
                                }.onFailure { notify("切り替え失敗：${it.message}") }
                            },
                            onGeneratePetArt = {
                                updateAnimation(PetSpriteGenerator.create(pet))
                                tab = 1
                                notify("ペットの専用アニメを作成しました")
                            },
                            onChat = { message ->
                                val petAtRequest = pet
                                val requestPetId = petAtRequest.id
                                val context = chat.takeLast(8).joinToString("\n") {
                                    (if (it.fromUser) "user: " else "pet: ") + it.text.take(150)
                                }
                                chat = (chat + ChatEntry(true, message)).takeLast(30)
                                repo.saveChat(chat, requestPetId)
                                if (aiKey.isBlank()) {
                                    chat = (chat + ChatEntry(false, PetChat.reply(pet, message))).takeLast(30)
                                    repo.saveChat(chat, requestPetId)
                                } else {
                                    aiBusy = true
                                    val prompt = GeminiClient.petPrompt(
                                        petAtRequest.name, petAtRequest.species, petAtRequest.element,
                                        petAtRequest.personality, context, message
                                    )
                                    val activeKey = aiKey
                                    scope.launch {
                                        try {
                                            val reply = GeminiClient.generate(activeKey, prompt)
                                            val previous = repo.loadChat(requestPetId)
                                            val updated = (previous +
                                                ChatEntry(false, reply.take(1000))).takeLast(30)
                                            repo.saveChat(updated, requestPetId)
                                            // Never attach a delayed reply to a newly selected pet.
                                            if (pet.id == requestPetId) chat = updated
                                        } catch (e: Exception) {
                                            notify("AI会話の失敗：${e.message}")
                                        } finally {
                                            aiBusy = false
                                        }
                                    }
                                }
                            },
                            aiKey = aiKey,
                            aiBusy = aiBusy,
                            onAiKeyChange = { aiKey = it.take(256) }
                        )
                        1 -> CreatorScreen(
                            animation = animation,
                            modifier = Modifier.padding(padding),
                            glyphStatus = glyphStatus,
                            isGlyphSupported = glyph.isSupported,
                            onAnimationChange = ::updateAnimation,
                            onConnect = { glyph.connect { glyphStatus = it } },
                            onShowFrame = { frame ->
                                runCatching { glyph.showFrame(frame) }
                                    .onFailure { notify("表示に失敗：${it.message}") }
                            },
                            onImportImage = { uri ->
                                runCatching {
                                    updateAnimation(animation.replace(GlyphImage.importFromUri(this@MainActivity, uri)))
                                    notify("画像を13×13に変換しました")
                                }.onFailure { notify("読み込み失敗：${it.message}") }
                            },
                            onImportAnimation = { uri ->
                                runCatching {
                                    updateAnimation(repo.parseAnimation(readTextFromUri(uri, 200_000)))
                                    notify("アニメーションを読み込みました")
                                }.onFailure { notify("JSON読み込み失敗：${it.message}") }
                            },
                            onExportAnimation = { shareJson(repo.encodeAnimation(animation)) },
                            onSavePng = {
                                runCatching {
                                    GlyphImage.savePng(this@MainActivity, animation.current.pixels)
                                    notify("写真フォルダにPNGを保存しました")
                                }.onFailure { notify("保存失敗：${it.message}") }
                            },
                            onSaveEffect = { name, frames ->
                                runCatching {
                                    gallery.save(name, frames)
                                    artworks = gallery.list()
                                    notify("エフェクトを作品ギャラリーに保存しました")
                                }.onFailure {
                                    notify("作品保存失敗：${it.message}")
                                }
                            },
                            aiKey = aiKey,
                            aiBusy = aiBusy,
                            onAiKeyChange = { aiKey = it.take(256) },
                            onAiGenerate = { instruction ->
                                if (aiKey.isBlank()) {
                                    notify("先にGemini APIキーを設定してください")
                                } else {
                                    aiBusy = true
                                    val activeKey = aiKey
                                    scope.launch {
                                        try {
                                            val response = GeminiClient.generate(
                                                activeKey, GeminiClient.spritePrompt(instruction), true
                                            )
                                            val generated = GeminiClient.parseSprite(response)
                                            updateAnimation(generated)
                                            notify("AIで${generated.frames.size}フレーム生成しました")
                                        } catch (e: Exception) {
                                            notify("AI生成失敗：${e.message}")
                                        } finally {
                                            aiBusy = false
                                        }
                                    }
                                }
                            }
                        )
                        2 -> FluidScreen(
                            modifier = Modifier.padding(padding),
                            glyphSupported = glyph.isSupported,
                            glyphStatus = glyphStatus,
                            onConnect = { glyph.connect { glyphStatus = it } },
                            onFrame = { frame ->
                                runCatching { glyph.showFrame(frame) }
                                    .onFailure { notify("Glyph出力失敗：${it.message}") }
                            },
                            onPreviewFrame = { fluidPreview = it },
                            onCopyToCreator = { frame ->
                                updateAnimation(animation.replace(frame))
                                tab = 1
                                notify("水の形をCreatorの選択フレームへ反映しました")
                            }
                        )
                        3 -> GalleryScreen(
                            items = artworks,
                            modifier = Modifier.padding(padding),
                            onSave = { name ->
                                runCatching {
                                    gallery.save(name, animation)
                                    artworks = gallery.list()
                                    notify("作品を保存しました")
                                }.onFailure { notify("作品の保存失敗：${it.message}") }
                            },
                            onOpen = { id ->
                                runCatching {
                                    updateAnimation(gallery.load(id))
                                    tab = 1
                                    notify("保存済み作品をCreatorに開きました")
                                }.onFailure { notify("読み込み失敗：${it.message}") }
                            },
                            onDelete = { id ->
                                runCatching {
                                    gallery.delete(id)
                                    artworks = gallery.list()
                                }.onFailure { notify("削除失敗：${it.message}") }
                            },
                            onExportSheet = {
                                runCatching {
                                    GlyphImage.saveSpriteSheet(this@MainActivity, animation)
                                    notify("写真フォルダにスプライトシートを保存しました")
                                }.onFailure { notify("出力失敗：${it.message}") }
                            }
                        )
                        else -> LinkScreen(
                            modifier = Modifier.padding(padding),
                            status = questStatus,
                            onShare = { sendToMonsterQuest(repo.exportPet(pet, animation)) },
                            onImportResult = { questPicker.launch("*/*") },
                            toyMode = toyMode,
                            clockPixels = GlyphClock.draw(
                                clockNow.hour, clockNow.minute,
                                PhoneBattery.percentage(this@MainActivity)
                            ),
                            glyphSupported = glyph.isSupported,
                            onClockModeChange = { selected ->
                                toyMode = selected
                                toyPrefs.mode = selected
                                notify("Glyph Toy表示を${selected.label}に変更しました")
                            },
                            onClockPreview = {
                                val now = LocalDateTime.now()
                                val pixels = GlyphClock.draw(
                                    now.hour, now.minute,
                                    PhoneBattery.percentage(this@MainActivity)
                                )
                                runCatching {
                                    glyph.connect { glyphStatus = it }
                                    glyph.showFrame(pixels)
                                }.onFailure { notify("時計のGlyph表示に失敗：${it.message}") }
                            },
                            nfcAvailable = adapter != null,
                            nfcEnabled = ringEnabled,
                            nfcRegistered = ringStore.isEnrolled(),
                            nfcEnrolling = ringEnrolling,
                            nfcStatus = ringStatus,
                            onEnrollRing = {
                                if (adapter?.isEnabled != true) {
                                    ringStatus = "端末のNFCを有効化してください"
                                } else {
                                    ringEnrolling = true
                                    ringStatus = "リングをスマホのNFC位置へかざしてください"
                                    refreshRingReader()
                                }
                            },
                            onRingEnabledChange = { enabled ->
                                ringEnabled = enabled && ringStore.isEnrolled()
                                ringStore.enabled = ringEnabled
                                refreshRingReader()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun readTextFromUri(uri: Uri, limit: Int): String {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readNBytes(limit + 1) }
            ?: error("ファイルを読み込めません")
        require(bytes.size <= limit) { "ファイルが大きすぎます" }
        return bytes.toString(Charsets.UTF_8)
    }
}

@Composable
private fun LinkScreen(
    modifier: Modifier = Modifier,
    status: String,
    onShare: () -> Unit,
    onImportResult: () -> Unit,
    toyMode: GlyphToyMode,
    clockPixels: List<Boolean>,
    glyphSupported: Boolean,
    onClockModeChange: (GlyphToyMode) -> Unit,
    onClockPreview: () -> Unit,
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    nfcRegistered: Boolean,
    nfcEnrolling: Boolean,
    nfcStatus: String,
    onEnrollRing: () -> Unit,
    onRingEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Monster Quest連携", style = MaterialTheme.typography.headlineSmall)
        Text("ペット情報をJSON v1でMonster Questへ送り、冒険結果を受け取れます。相手のアプリが未インストールなら共有メニューを開きます。")
        Button(onClick = onShare) { Text("ペットデータを共有") }
        Text("Monster Questの冒険結果JSONを読み込めます。" +
            " 経験値と幸福度の上限、個体IDと重複イベントIDを検証します。")
        OutlinedButton(onClick = onImportResult) { Text("冒険結果JSONを取り込む") }
        Text("状態：$status")
        Text("注意：現在のJSONには電子署名はなく、競争モードやオンライン対戦用の改ざん耐性はありません。",
            style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Glyph時計・バッテリー", style = MaterialTheme.typography.titleMedium)
        Text("時刻を上下2段、電池残量を最下段の5点で表示。AIやネット接続は不要です。" +
            "AOD Glyph Toyは約1分ごとの更新で、画面のプレビューとは動き方が異なります。",
            style = MaterialTheme.typography.bodySmall)
        PixelGrid(
            pixels = clockPixels,
            modifier = Modifier.size(198.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlyphToyMode.entries.forEach { mode ->
                FilterChip(
                    selected = toyMode == mode,
                    onClick = { onClockModeChange(mode) },
                    label = { Text(mode.label) }
                )
            }
        }
        Text("この設定はSDK付きビルドでAOD Glyph Toyが選ばれているときに反映されます。" +
            "SDKなしのAPKでは画面プレビューのみです。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = glyphSupported, onClick = onClockPreview) {
            Text("時計を今すぐ背面Glyphへ表示")
        }
        HorizontalDivider()
        Text("NFCリングでGlyphモード切り替え", style = MaterialTheme.typography.titleMedium)
        Text("登録したリングを読み取るたびに Pet → Creator → Fluid → Pet と切り替えます。リングのIDのハッシュだけ端末内に保存します。")
        Text(if (nfcAvailable) "NFC読み取り対応端末です" else "この端末ではNFCが利用できません")
        OutlinedButton(
            enabled = nfcAvailable && !nfcEnrolling,
            onClick = onEnrollRing
        ) { Text(if (nfcRegistered) "別のリングで再登録" else "NFCリングを登録") }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("リング操作を有効にする")
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = nfcEnabled,
                enabled = nfcAvailable && nfcRegistered,
                onCheckedChange = onRingEnabledChange
            )
        }
        Text("NFC：$nfcStatus", style = MaterialTheme.typography.bodySmall)
        Text("アプリが前面にある間だけ使用できます。NFCのUIDは複製・変化する場合があるため本人認証には使いません。実機検証は未実施。",
            style = MaterialTheme.typography.bodySmall)
    }
}
