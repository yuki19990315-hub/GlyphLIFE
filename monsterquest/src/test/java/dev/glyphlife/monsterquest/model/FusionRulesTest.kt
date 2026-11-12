package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import org.junit.Test

class FusionRulesTest {
    private val a = WildMonster("id-a", "フレア", "火", 35, 7, pixels="1".repeat(169))
    private val b = WildMonster("id-b", "アクア", "水", 40, 8, pixels="0".repeat(169))
    private val c = WildMonster("id-c", "モコ", "草", 24, 4)

    @Test fun fuseConsumesTwoAndProducesOneStrongerMonster() {
        val original = QuestState(captures = listOf(a, b, c))
        val result = FusionRules.fuse(original, a.id, b.id)
        assertEquals(2, result.captures.size)
        assertEquals(listOf("id-c"), result.captures.take(1).map { it.id })
        val fusion = result.captures.last()
        assertNotEquals(a.id, fusion.id)
        assertNotEquals(b.id, fusion.id)
        assertEquals("無", fusion.element)
        assertTrue(fusion.maxHp > maxOf(a.maxHp, b.maxHp))
        assertTrue(fusion.attack > maxOf(a.attack, b.attack))
        assertEquals(169, fusion.pixels.length)
        assertTrue(fusion.pixels.count { it == '1' } in 10..137)
        assertEquals("融合・フレア×アクア", fusion.species)
        assertTrue(fusion.bodyType.isNotBlank())
        assertEquals("フレア × アクア", fusion.lineage)
        assertEquals(3, original.captures.size)
    }

    @Test fun fusionRejectsSameIdOrMissingMaterial() {
        val state = QuestState(captures = listOf(a, b))
        try {
            FusionRules.fuse(state, a.id, a.id)
            fail("same id")
        } catch (_: IllegalArgumentException) {}
        try {
            FusionRules.fuse(state, a.id, "missing")
            fail("missing id")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun fusionRejectsBattleAndCannotConsumeBoss() {
        val battle = QuestState(inBattle = true, captures = listOf(a,b))
        try {
            FusionRules.fuse(battle,a.id,b.id)
            fail("battle")
        } catch (_: IllegalArgumentException) {}
        val boss = b.copy(isBoss = true)
        try {
            FusionRules.fuse(QuestState(captures=listOf(a,boss)),a.id,boss.id)
            fail("boss")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun synthesizedElementRulesAreCommutative() {
        assertEquals("無", FusionRules.resultElement(a,b))
        assertEquals(FusionRules.resultElement(a,b), FusionRules.resultElement(b,a))
        assertEquals("草", FusionRules.resultElement(c,c))
    }
    @Test fun fusionPreviewExactlyMatchesCommittedAppearance() {
        val preview=FusionRules.preview(a,b)
        val completed=FusionRules.fuse(QuestState(captures=listOf(a,b)),a.id,b.id)
        val result=completed.captures.single()
        assertNotEquals(preview.id,result.id)
        assertEquals(preview.designSeed,result.designSeed)
        assertEquals(preview.pixels,result.pixels)
        assertEquals(preview.species,result.species)
        assertEquals(preview.element,result.element)
        assertEquals(preview.maxHp,result.maxHp)
        assertEquals(preview.attack,result.attack)
        assertTrue(MonsterDesign.sprite(preview).pixels()
            .contentEquals(MonsterDesign.sprite(result).pixels()))
    }

    @Test fun previewDoesNotConsumeParentsAndIsDeterministic() {
        val first=FusionRules.preview(a,b)
        assertEquals(first,FusionRules.preview(a,b))
        assertEquals(169,first.pixels.length)
        assertTrue(first.pixels.any { it=='1' })
        assertNotEquals(first.designSeed,FusionRules.preview(b,a).designSeed)
    }

}
