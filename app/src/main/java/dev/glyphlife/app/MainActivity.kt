package dev.glyphlife.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.data.PetRepository
import dev.glyphlife.app.glyph.createGlyphBridge
import dev.glyphlife.app.ui.CreatorScreen
import dev.glyphlife.app.ui.PetScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = PetRepository(this)
        val glyph = createGlyphBridge(this)
        setContent {
            var pet by remember { mutableStateOf(repo.loadPet()) }
            var pixels by remember { mutableStateOf(repo.loadPixels()) }
            var tab by remember { mutableIntStateOf(0) }
            var glyphStatus by remember { mutableStateOf("未接続") }
            var connected by remember { mutableStateOf(false) }

            DisposableEffect(glyph) { onDispose { glyph.close() } }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFDFEE9B),
                background = Color(0xFF111414),
                surface = Color(0xFF1B2020),
            )) {
                Scaffold(
                    topBar = {
                        Surface(tonalElevation = 2.dp) {
                            Text("GLYPH LIFE  /  MVP 0.1",
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
                                icon = { Text("⇄") }, label = { Text("連携") })
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> PetScreen(
                            pet = pet, pixels = pixels, modifier = Modifier.padding(padding),
                            onPetChange = { changed ->
                                pet = changed
                                repo.savePet(changed)
                            })
                        1 -> CreatorScreen(
                            pixels = pixels, modifier = Modifier.padding(padding),
                            glyphStatus = glyphStatus, isGlyphSupported = glyph.isSupported,
                            onPixelsChange = { changed ->
                                pixels = changed
                                repo.savePixels(changed)
                            },
                            onConnect = {
                                glyph.connect { status ->
                                    glyphStatus = status
                                    connected = status == "接続済み"
                                }
                            },
                            onSend = {
                                if (connected) {
                                    glyph.showFrame(pixels)
                                    glyphStatus = "表示要求を送信しました（実機表示は要確認）"
                                } else glyphStatus = "先に接続してください"
                            })
                        else -> LinkScreen(modifier = Modifier.padding(padding)) {
                            val payload = repo.exportPet(pet, pixels)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_TEXT, payload)
                            }
                            startActivity(Intent.createChooser(intent, "ペットデータを共有"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkScreen(modifier: Modifier = Modifier, onShare: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Monster Quest連携", style = MaterialTheme.typography.headlineSmall)
        Text("現在は連携仕様（JSON v1）の書き出しだけ実装しています。" +
            "Monster Quest側の受け取り、同期、チート対策は今後の開発対象です。")
        Button(onClick = onShare) { Text("ペットデータを共有（JSON）") }
        Text("注意：共有先にはペット情報とドット絵が渡ります。信頼できるアプリを選択してください。",
            style = MaterialTheme.typography.bodySmall)
    }
}
