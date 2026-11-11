package dev.glyphlife.app.model

import java.util.UUID
import kotlin.random.Random

/** An offline egg generator. This is procedural, not AI-generated content. */
object PetFactory {
    private val species = listOf("スライム", "ドラゴン", "精霊", "キツネ", "ロボット")
    private val elements = listOf("水", "火", "草", "雷", "光", "闇", "無")
    private val personalities = listOf("好奇心旺盛", "甘えん坊", "マイペース", "いたずら好き", "臆病")

    fun hatch(random: Random = Random.Default): PetState = PetState(
        id = UUID.randomUUID().toString(),
        name = listOf("ピコ", "モチ", "ルナ", "ポム", "ネム")[random.nextInt(5)],
        species = species[random.nextInt(species.size)],
        element = elements[random.nextInt(elements.size)],
        personality = personalities[random.nextInt(personalities.size)],
        happiness = 55 + random.nextInt(21),
        energy = 65 + random.nextInt(26)
    )
}

/** A deliberately simple offline response fallback, not a language model. */
object PetChat {
    fun reply(pet: PetState, message: String): String {
        val text = message.trim()
        if (text.isEmpty()) return "なにを話そうか？"
        return when {
            listOf("おはよう", "こんにちは", "こんばんは", "やあ").any { it in text } ->
                "やっほー！${pet.name}だよ。今日も一緒に遊ぼう！"
            "名前" in text -> "ぼくの名前は${pet.name}！ 覚えてくれたの？"
            "属性" in text || "タイプ" in text -> "ぼくは${pet.element}属性の${pet.species}だよ！"
            "疲れ" in text || "元気" in text ->
                if (pet.energy < 35) "ちょっと眠いかも。少し休みたいな……"
                else "元気いっぱい！ いっしょに遊ぼう！"
            "好き" in text -> "いっしょに過ごす時間が好き！"
            else -> when (pet.personality) {
                "臆病" -> "えっと……もう少し聞かせて？"
                "いたずら好き" -> "ふふ、面白そうなことを思いついた！"
                "マイペース" -> "のんびり話すのもいいね。"
                else -> "うんうん！ もっとお話聞きたいな。"
            }
        }
    }
}
