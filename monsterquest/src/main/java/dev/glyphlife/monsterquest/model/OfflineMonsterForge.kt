package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.GbaMonsterArtStandard
import dev.glyphlife.monsterquest.pixel.GlyphProjector
import dev.glyphlife.monsterquest.pixel.MonsterArmor
import dev.glyphlife.monsterquest.pixel.MonsterBodyType
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import java.util.UUID

/**
 * Completely offline, rule-based text-to-monster generator.
 *
 * IMPORTANT: No neural network or image-generation model is involved.
 * It selects and adapts existing authored 64x64 sprite families.
 * The explicit seed is persisted so the same monster remains stable.
 */
object OfflineMonsterForge {
    data class Plan(
        val name: String,
        val element: String,
        val bodyType: MonsterBodyType,
        val primaryMotif: String,
        val secondaryMotif: String,
        val armor: Map<String, String>,
        val input: String
    )

    private val elementTerms = listOf(
        "火" to listOf("火", "炎", "焔", "燃", "灼", "fire", "flame"),
        "水" to listOf("水", "氷", "雪", "海", "泡", "ice", "water", "frost"),
        "草" to listOf("草", "花", "植物", "森", "木", "葉", "forest", "plant", "leaf"),
        "雷" to listOf("雷", "電", "稲妻", "磁力", "thunder", "electric", "lightning"),
        "光" to listOf("光", "聖", "太陽", "星", "天使", "light", "holy"),
        "闇" to listOf("闇", "影", "夜", "悪魔", "幽霊", "dark", "shadow")
    )
    private val bodyTerms = listOf(
        MonsterBodyType.BEAST to listOf("狐", "狼", "犬", "猫", "獣", "虎", "ライオン", "fox", "wolf", "cat", "beast"),
        MonsterBodyType.DRAGON to listOf("ドラゴン", "竜", "龍", "dragon", "drake"),
        MonsterBodyType.AVIAN to listOf("鳥", "鷲", "鷹", "フクロウ", "翼獣", "bird", "eagle", "owl"),
        MonsterBodyType.MECHA to listOf("機械", "機甲", "ロボ", "メカ", "歯車", "robot", "mecha"),
        MonsterBodyType.AQUATIC to listOf("魚", "サメ", "クジラ", "海獣", "fish", "shark"),
        MonsterBodyType.INSECTOID to listOf("昆虫", "虫", "甲虫", "蝶", "カブト", "insect", "moth"),
        MonsterBodyType.SERPENT to listOf("蛇", "ヘビ", "大蛇", "serpent", "snake"),
        MonsterBodyType.GOLEM to listOf("岩", "巨像", "ゴーレム", "石像", "golem"),
        MonsterBodyType.SPIRIT to listOf("精霊", "霊", "妖精", "幻", "spirit", "fairy"),
        MonsterBodyType.BIPED to listOf("戦士", "騎士", "人型", "侍", "knight", "warrior")
    )
    private val bodyNames = mapOf(
        MonsterBodyType.BEAST to "獣",
        MonsterBodyType.DRAGON to "竜",
        MonsterBodyType.AVIAN to "鳥",
        MonsterBodyType.MECHA to "機甲",
        MonsterBodyType.AQUATIC to "海獣",
        MonsterBodyType.INSECTOID to "甲虫",
        MonsterBodyType.SERPENT to "蛇",
        MonsterBodyType.GOLEM to "巨像",
        MonsterBodyType.SPIRIT to "精霊",
        MonsterBodyType.BIPED to "騎士"
    )
    private val elementNames = mapOf(
        "火" to "焔", "水" to "蒼", "草" to "翠", "雷" to "閃",
        "光" to "輝", "闇" to "影", "無" to "原"
    )

    private data class Matched<T>(val value: T, val term: String, val position: Int)

    private fun <T> firstTerm(text: String, groups: List<Pair<T, List<String>>>): Matched<T>? =
        groups.flatMap { (value, terms) ->
            terms.mapNotNull { term ->
                text.indexOf(term).takeIf { it >= 0 }?.let { Matched(value, term, it) }
            }
        }.minWithOrNull(compareBy<Matched<T>> { it.position }.thenByDescending { it.term.length })

    fun plan(raw: String): Plan {
        val prompt = raw.trim().take(250).ifBlank { "不思議なオリジナルモンスター" }
        // A custom name is an identifier, not an anatomy description.
        // "name: TerraFox thunder dragon" should select DRAGON, not BEAST.
        val namePattern = Regex(
            """(?:名前|name)\s*[:：]\s*([\p{L}\p{N}ー・_-]{1,16})""",
            RegexOption.IGNORE_CASE)
        val named = namePattern.find(prompt)?.groupValues?.get(1)
        val normalized = prompt.replaceFirst(namePattern, "").lowercase()
        val body = firstTerm(normalized, bodyTerms)
        val element = firstTerm(normalized, elementTerms)
        val type = body?.value ?: MonsterBodyType.entries[
            Math.floorMod(prompt.hashCode(), MonsterBodyType.entries.size)]
        val color = element?.value ?: "無"
        val details = listOf("結晶", "装甲", "角", "翼", "尾", "羽", "鱗", "花", "炎", "氷", "光", "棘")
            .filter { normalized.contains(it) }
        val secondary = details.firstOrNull() ?: "固有の輪郭"
        val armor = linkedMapOf<String, String>()
        if (normalized.contains("角") || normalized.contains("horn") ||
            normalized.contains("棘")) armor["HEAD"] = "SPIKE"
        if (normalized.contains("結晶") || normalized.contains("crystal"))
            armor["CORE"] = "CRYSTAL"
        else if (normalized.contains("装甲") || normalized.contains("armor"))
            armor["CORE"] = "PLATE"
        if (normalized.contains("尾") || normalized.contains("tail"))
            armor["TAIL"] = if (armor["CORE"] == "CRYSTAL") "CRYSTAL" else "SPIKE"
        if (normalized.contains("紋") || normalized.contains("rune"))
            armor["BACK"] = "RUNE"
        if (normalized.contains("翼") || normalized.contains("wing"))
            armor["WING"] = "CRYSTAL"

        val name = named ?: (
            (elementNames[color] ?: "原") +
                (bodyNames[type] ?: "幻獣") +
                listOf("ルミ", "リオン", "ネア", "ザル", "ミア", "ノク")[
                    Math.floorMod(prompt.hashCode(), 6)]
            )
        return Plan(
            name = name.take(16), element = color, bodyType = type,
            primaryMotif = body?.term ?: (bodyNames[type] ?: "魔獣"),
            secondaryMotif = secondary,
            armor = MonsterArmor.validated(armor),
            input = prompt
        )
    }

    fun design(prompt: String, seed: String): WildMonster {
        require(seed.isNotBlank() && seed.length <= 100)
        val plan = plan(prompt)
        val base = WildMonster(
            id = UUID.randomUUID().toString(),
            species = plan.name,
            element = plan.element,
            maxHp = 25,
            attack = 5,
            stage = 1,
            bodyType = plan.bodyType.name,
            description = "オフライン工房で作られた${plan.element}属性の${plan.primaryMotif}型。特徴：${plan.secondaryMotif}。",
            motifPrimary = plan.primaryMotif,
            motifSecondary = plan.secondaryMotif,
            designSeed = "local-$seed",
            armorParts = plan.armor,
            originKind = MonsterOrigin.LOCAL_FORGE,
            originConditions = listOf(
                "入力条件：${plan.input}",
                "生成方式：端末内の既存ドット絵素材とローカル規則（画像生成AIではありません）",
                "属性：${plan.element}",
                "体型：${plan.bodyType.name}",
                "主モチーフ：${plan.primaryMotif}",
                "特徴：${plan.secondaryMotif}",
                "装甲指定：${plan.armor.entries.joinToString { it.key + "=" + it.value }.ifBlank { "なし" }}"
            ).joinToString("\n").take(600)
        )
        val sprite = MonsterDesign.sprite(base)
        check(GbaMonsterArtStandard.audit(sprite).compliant) {
            "ドット絵の品質規格に適合しないため召喚を中止しました"
        }
        return base.copy(pixels = GlyphProjector.projectFor(base, sprite))
    }
}
