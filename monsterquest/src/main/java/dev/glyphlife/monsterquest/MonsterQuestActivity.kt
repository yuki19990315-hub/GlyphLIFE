package dev.glyphlife.monsterquest

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MonsterQuestActivity : ComponentActivity() {
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = sharedJson(intent)
        val storage = QuestStorage(this)
        setContent {
            var state by remember { mutableStateOf(storage.load()) }
            var status by remember { mutableStateOf("オフラインで遊べます") }

            fun update(next: QuestState) {
                state = next
                storage.save(next)
            }
            fun notify(text: String) {
                status = text
                Toast.makeText(this@MonsterQuestActivity, text, Toast.LENGTH_SHORT).show()
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
                        Text("MONSTER QUEST  /  MVP 0.1",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.titleMedium)
                    }
                }) { padding ->
                    QuestScreen(
                        state = state, status = status,
                        modifier = Modifier.padding(padding),
                        onMove = { x, y ->
                            val nextX = state.position % 4 + x
                            val nextY = state.position / 4 + y
                            if (nextX in 0..3 && nextY in 0..3) {
                                update(QuestRules.move(state, nextY * 4 + nextX))
                            }
                        },
                        onAttack = { magic -> update(QuestRules.attack(state, magic)) },
                        onCapture = { update(QuestRules.capture(state)) },
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
                                startActivity(Intent.createChooser(share, "Glyph Lifeに冒険結果を渡す"))
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
    modifier: Modifier = Modifier,
    onMove: (Int, Int) -> Unit,
    onAttack: (Boolean) -> Unit,
    onCapture: () -> Unit,
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
        LinearProgressIndicator(
            progress = { state.playerHp / 100f }, modifier = Modifier.fillMaxWidth())
        Text("HP ${state.playerHp}/100　撃破${state.defeated}　捕獲${state.captures.size}匹")
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
                Text("${enemy.species}（${enemy.element}）が出現！",
                    style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(
                    progress = { state.enemyHp.toFloat() / enemy.maxHp }, modifier = Modifier.fillMaxWidth())
                Text("敵HP：${state.enemyHp}/${enemy.maxHp}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAttack(false) }) { Text("攻撃") }
                    Button(onClick = { onAttack(true) }) { Text("属性魔法") }
                    OutlinedButton(onClick = onCapture) { Text("捕獲") }
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
        Text("冒険履歴：捕獲したモンスター",
            style = MaterialTheme.typography.titleMedium)
        if (state.captures.isEmpty()) Text("まだ捕獲していません")
        state.captures.takeLast(15).forEach { monster ->
            Text("・${monster.species} / ${monster.element}")
        }
        Text("状態：$status", style = MaterialTheme.typography.bodySmall)
        Text(
            "この版は手続き生成・オフライン戦闘のMVPです。AI生成の見た目や画像生成、" +
                "オンライン対戦は未実装です。連携データに改ざん防止署名はありません。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
