package dev.glyphlife.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import dev.glyphlife.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = PetRepository(this)
        val gallery = ArtworkLibrary(this)
        val glyph = createGlyphBridge(this)
        setContent {
            var pet by remember { mutableStateOf(repo.loadPet()) }
            var roster by remember { mutableStateOf(repo.loadPets()) }
            var artworks by remember { mutableStateOf(gallery.list()) }
            var animation by remember { mutableStateOf(repo.loadAnimation(pet.id)) }
            var chat by remember { mutableStateOf(repo.loadChat(pet.id)) }
            var tab by remember { mutableIntStateOf(0) }
            var glyphStatus by remember { mutableStateOf("未接続") }
            var questStatus by remember { mutableStateOf("取り込み待ち") }
            var aiKey by remember { mutableStateOf("") }
            var aiBusy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
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

            DisposableEffect(glyph) { onDispose { glyph.close() } }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFDFEE9B),
                background = Color(0xFF111414),
                surface = Color(0xFF1B2020)
            )) {
                Scaffold(
                    topBar = {
                        Surface(tonalElevation = 2.dp) {
                            Text("GLYPH LIFE  /  v0.6",
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
                                icon = { Text("▣") }, label = { Text("作品") })
                            NavigationBarItem(
                                selected = tab == 3, onClick = { tab = 3 },
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
                                val context = chat.takeLast(8).joinToString("\n") {
                                    (if (it.fromUser) "user: " else "pet: ") + it.text.take(150)
                                }
                                chat = (chat + ChatEntry(true, message)).takeLast(30)
                                repo.saveChat(chat, pet.id)
                                if (aiKey.isBlank()) {
                                    chat = (chat + ChatEntry(false, PetChat.reply(pet, message))).takeLast(30)
                                    repo.saveChat(chat, pet.id)
                                } else {
                                    aiBusy = true
                                    val prompt = GeminiClient.petPrompt(
                                        pet.name, pet.species, pet.element,
                                        pet.personality, context, message
                                    )
                                    val activeKey = aiKey
                                    scope.launch {
                                        try {
                                            val reply = GeminiClient.generate(activeKey, prompt)
                                            chat = (chat + ChatEntry(false, reply.take(1000))).takeLast(30)
                                            repo.saveChat(chat, pet.id)
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
                        2 -> GalleryScreen(
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
                            onShare = { shareJson(repo.exportPet(pet, animation)) },
                            onImportResult = { questPicker.launch("*/*") }
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
    onImportResult: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Monster Quest連携", style = MaterialTheme.typography.headlineSmall)
        Text("ペット情報をJSON v1で共有できます。別アプリ側でデータを受け取る機能は今後実装します。")
        Button(onClick = onShare) { Text("ペットデータを共有") }
        Text("将来のMonster Quest用の冒険結果JSONを読み込めます。" +
            " 経験値と幸福度の上限、個体IDと重複イベントIDを検証します。")
        OutlinedButton(onClick = onImportResult) { Text("冒険結果JSONを取り込む") }
        Text("状態：$status")
        Text("注意：現在のJSONには電子署名はなく、競争モードやオンライン対戦用の改ざん耐性はありません。",
            style = MaterialTheme.typography.bodySmall)
    }
}
