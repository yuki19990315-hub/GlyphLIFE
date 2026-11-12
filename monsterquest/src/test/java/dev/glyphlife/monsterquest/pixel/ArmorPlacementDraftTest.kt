package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.FusionRules
import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class ArmorPlacementDraftTest {
    private val id = "10000000-0000-4000-8000-000000001101"
    private val secondId = "10000000-0000-4000-8000-000000001102"
    private fun creature(stage: Int = 3) = WildMonster(
        id = "00000000-0000-4000-8000-000000001201",
        species = "角竜", element = "雷", maxHp = 38, attack = 7,
        bodyType = MonsterBodyType.DRAGON.name, stage = stage
    )
    private fun part(): PixelSprite {
        val data = IntArray(64 * 64)
        for (y in 21..40) for (x in 25..37) {
            data[y * 64 + x] = 0xffecce74.toInt()
        }
        return CustomArmorLayers.normalize(PixelSprite.fromArgb(64,64,data))
    }
    private fun draw(wild: WildMonster, asset: PixelSprite) =
        CustomArmorLayers.compose(MonsterDesign.sprite(wild), wild) { asset }

    @Test fun previewKeepsOriginalUntilConfirmedAndMatchesCommittedPixels() {
        val original = creature().copy(customArmorParts = mapOf("HEAD" to id))
        val originalReference = original.copy()
        val source = part()
        val sourcePixels = source.pixels()
        val oldPixels = draw(original, source).pixels()
        val placement = ArmorOffset(dx = 6, dy = -5, scalePercent = 125)
        val preview = ArmorPlacementDraft.reposition(original, ArmorSlot.HEAD, placement)
        assertEquals(originalReference, original)
        assertEquals(emptyMap<String, ArmorOffset>(), original.customArmorOffsets)
        assertEquals(placement, preview.customArmorOffsets["HEAD"])
        assertFalse(oldPixels.contentEquals(draw(preview, source).pixels()))
        val committed = ArmorPlacementDraft.reposition(original, ArmorSlot.HEAD, placement)
        assertArrayEquals(draw(preview, source).pixels(), draw(committed, source).pixels())
        assertEquals(GlyphProjector.project(draw(preview,source)),
            GlyphProjector.project(draw(committed,source)))
        assertArrayEquals(sourcePixels, source.pixels())
    }

    @Test fun newPartDraftPreservesOtherSlotsAndFusionInheritance() {
        val original = creature().copy(customArmorParts = mapOf("CORE" to secondId),
            customArmorOffsets = mapOf("CORE" to ArmorOffset(1,2,110)))
        val proposal = ArmorPlacementDraft.withAsset(original, ArmorSlot.HEAD,
            id, ArmorOffset(-4,3,90))
        assertEquals(1, original.customArmorParts.size)
        assertEquals(secondId, proposal.customArmorParts["CORE"])
        assertEquals(ArmorOffset(1,2,110), proposal.customArmorOffsets["CORE"])
        assertEquals(id, proposal.customArmorParts["HEAD"])
        val other = creature().copy(id = "00000000-0000-4000-8000-000000001202")
        val fused = FusionRules.preview(proposal, other)
        assertEquals(id, fused.customArmorParts["HEAD"])
        assertEquals(ArmorOffset(-4,3,90), fused.customArmorOffsets["HEAD"])
    }

    @Test fun lockedStagePreviewNeverAdvancesRealMonster() {
        val original = creature(stage = 1)
        val withWing = ArmorPlacementDraft.withAsset(
            original, ArmorSlot.WING, id, ArmorOffset(8,-2,130))
        assertEquals(1, withWing.stage)
        val preview = ArmorPlacementDraft.visiblePreview(withWing, ArmorSlot.WING)
        assertEquals(3, preview.stage)
        assertEquals(1, original.stage)
        val a = draw(withWing, part())
        val b = draw(preview, part())
        assertTrue(a.colors().size <= 16)
        assertTrue(b.colors().size <= 16)
        assertFalse(a.pixels().contentEquals(b.pixels()))
    }

    @Test fun nonUuidAssetAndUnattachedSlotAreRejected() {
        try { ArmorPlacementDraft.withAsset(creature(), ArmorSlot.HEAD,
            "../invalid.png", ArmorOffset()); fail("invalid path") }
        catch (_: IllegalArgumentException) {}
        try { ArmorPlacementDraft.reposition(creature(),
            ArmorSlot.TAIL, ArmorOffset()); fail("missing part") }
        catch (_: IllegalArgumentException) {}
    }
}
