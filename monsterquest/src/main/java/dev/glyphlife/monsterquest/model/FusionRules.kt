package dev.glyphlife.monsterquest.model

import java.util.UUID
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.pixel.GlyphProjector

/**
 * Offline monster synthesis. Two captured monster instances are consumed and
 * one hybrid instance is created; GlyphLIFE's canonical companion is untouched.
 * No network or AI is required.
 */
object FusionRules {
    fun resultName(first: WildMonster, second: WildMonster): String =
        "融合・${first.species.take(5)}×${second.species.take(5)}"

    fun resultElement(first: WildMonster, second: WildMonster): String {
        val pair = setOf(first.element, second.element)
        return when {
            first.element == second.element -> first.element
            pair == setOf("火", "水") -> "無"
            pair == setOf("光", "闇") -> "無"
            pair.contains("光") -> "光"
            pair.contains("雷") -> "雷"
            pair.contains("火") -> "火"
            pair.contains("草") -> "草"
            pair.contains("水") -> "水"
            else -> first.element
        }
    }

    /**
     * Pure preview: same visual identity, stats, stage and Glyph output as
     * the actual fusion. It does not consume either parent or randomize art.
     */
    fun preview(a: WildMonster, b: WildMonster): WildMonster {
        require(a.id != b.id) { "異なる2匹を選んでください" }
        require(!a.isBoss && !b.isBoss) { "ボスは合成素材にできません" }
        val visualId = UUID.nameUUIDFromBytes(
            ("monsterquest-fusion-v2|" + a.id + "|" + b.id).toByteArray(Charsets.UTF_8)
        ).toString()
        val fusion = WildMonster(
            id = visualId,
            species = resultName(a, b),
            element = resultElement(a, b),
            maxHp = (maxOf(a.maxHp, b.maxHp) + 12).coerceIn(1, 180),
            attack = (maxOf(a.attack, b.attack) + 3).coerceIn(1, 40),
            description = "${a.species}と${b.species}が合体して生まれた新種。",
            isBoss = false,
            bodyType = MonsterDesign.bodyType(a).name,
            stage = maxOf(a.stage, b.stage).coerceIn(1, 3),
            lineage = listOf(a.species, b.species).joinToString(" × "),
            motifPrimary = a.motifPrimary.ifBlank { a.species },
            motifSecondary = b.motifPrimary.ifBlank { b.species },
            designSeed = "fusion-" + visualId,
            armorParts = dev.glyphlife.monsterquest.pixel.MonsterArmor.inherit(
                a.armorParts, b.armorParts)
        )
        return fusion.copy(pixels = GlyphProjector.project(MonsterDesign.sprite(fusion)))
    }

    fun fuse(state: QuestState, firstId: String, secondId: String): QuestState {
        require(!state.inBattle) { "戦闘中には合成できません" }
        require(firstId != secondId) { "異なる2匹を選んでください" }
        val a = state.captures.firstOrNull { it.id == firstId }
            ?: throw IllegalArgumentException("素材Aが見つかりません")
        val b = state.captures.firstOrNull { it.id == secondId }
            ?: throw IllegalArgumentException("素材Bが見つかりません")
        val preview = preview(a, b)
        val fused = preview.copy(id = UUID.randomUUID().toString())
        val remaining = state.captures.filterNot { it.id == firstId || it.id == secondId }
        return state.copy(
            captures = remaining + fused,
            message = "${a.species}と${b.species}を合成！ ${fused.species}が誕生した"
        )
    }
}
