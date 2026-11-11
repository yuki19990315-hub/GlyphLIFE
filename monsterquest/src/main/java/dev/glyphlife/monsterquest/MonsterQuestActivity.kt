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
import dev.glyphlife.monsterquest.model.*
import dev.glyphlife.monsterquest.ai.MonsterAi
import dev.glyphlife.monsterquest.ai.MonsterIdea
import dev.glyphlife.monsterquest.ui.MonsterArt
import dev.glyphlife.monsterquest.ui.MonsterPortrait
import kotlinx.coroutines.launch

class MonsterQuestActivity : ComponentActivity() {
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = sharedJson(intent)
        val storage = QuestStorage(this)
        setContent {
            var state by remember { mutableStateOf(storage.load()) }
            var status by remember { mutableStateOf("オフラインで遊べます") }
            var aiKey by remember { mutableStateOf("") }
            var aiDescription by remember { mutableStateOf("") }
            var aiBusy by remember { mutableStateOf(false) }
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
                        Text("MONSTER QUEST  /  MVP 0.3",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.titleMedium)
                    }
                }) { padding ->
                    QuestScreen(
                        state = state, status = status,
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
    onDefend: () -> Unit,
    onNextRoom: () -> Unit,
    onEnd: () -> Unit,
    onNewRun: () -> Unit,
    onSendResult: () -> Unit
) {
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
        val companionArt = state.companion.glyph.takeIf {
            it.length == 169 && it.any { char -> char == '1' }
        } ?: MonsterArt.generate(state.companion.species, state.companion.element)
        MonsterPortrait(
            pixels = companionArt,
            element = state.companion.element,
            modifier = Modifier.size(126.dp)
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
                MonsterPortrait(
                    pixels = MonsterArt.forWild(enemy),
                    element = enemy.element,
                    modifier = Modifier.size(if (enemy.isBoss) 220.dp else 180.dp)
                )
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
        HorizontalDivider()
        Text("冒険履歴：捕獲したモンスター",
            style = MaterialTheme.typography.titleMedium)
        if (state.captures.isEmpty()) Text("まだ捕獲していません")
        state.captures.takeLast(15).forEach { monster ->
            Text("・${monster.species} / ${monster.element}")
        }
        Text("状態：$status", style = MaterialTheme.typography.bodySmall)
        Text(
            "この版はオフライン戦闘のMVPです。AIモンスターの13×13ドット絵生成は任意のGemini接続で提供しますが、" +
                "高解像度画像の生成とオンライン対戦は未実装です。連携データに改ざん防止署名はありません。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
