package dev.glyphlife.monsterquest.model

/** Record actual conditions; never invent missing old histories. */
object MonsterOrigin {
    const val AI_IMAGE = "ai-image"
    const val AI_TEXT = "ai-text"
    const val FUSION = "fusion"
    const val WILD = "wild"

    fun label(kind: String): String = when (kind) {
        AI_IMAGE -> "AI画像召喚"
        AI_TEXT -> "AI設定召喚（外見は仮素材）"
        FUSION -> "モンスター合成"
        WILD -> "探索で発見"
        else -> "記録なし（旧データ・探索など）"
    }

    fun ai(
        prompt: String, photo: Boolean, element: String,
        bodyType: String, primary: String, secondary: String
    ): String = listOfNotNull(
        "入力指示：" + prompt.trim().replace(Regex("\\s+"), " ").take(250),
        if (photo) "参考写真：あり" else "参考写真：なし",
        "属性：" + element.take(20),
        bodyType.takeIf { it.isNotBlank() }?.let { "体型：" + it.take(32) },
        primary.takeIf { it.isNotBlank() }?.let { "主モチーフ：" + it.take(50) },
        secondary.takeIf { it.isNotBlank() }?.let { "副モチーフ：" + it.take(50) }
    ).joinToString("\n").take(600)

    /** Real location and source, not a fabricated history for old monsters. */
    fun encounter(
        floor: Int, roadPosition: Int, boss: Boolean, rare: Boolean, source: String
    ): String = listOf(
        if (roadPosition >= 0)
            "出現場所：一本道の§{roadPosition}歩地点 / エリア§{floor}"
        else "出現場所：階層探索 §{floor}F",
        "種類：" + if (boss) "ボス" else if (rare) "希少個体" else "通常個体",
        "出現経路：" + when(source) {
            "tracking" -> "図鑑の追跡対象"
            "ai-known" -> "AI召喚済み種族の再出現"
            else -> if (boss) "ボス出現地点" else "通常抽選"
        }
    ).joinToString("\n").take(600)

    fun fusion(a: WildMonster, b: WildMonster): String = (
        "素材A：${a.species.take(30)}（${a.element}属性・進化${a.stage}）\n" +
        "素材B：${b.species.take(30)}（${b.element}属性・進化${b.stage}）\n" +
        "合成規則：2体融合"
    ).take(600)
}
