package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster

/**
 * Immutable source of truth for both an unconfirmed preview and final equip.
 * The sprite itself is never modified; only a UUID and a bounded placement
 * transform are stored in the monster metadata.
 */
object ArmorPlacementDraft {
    fun withAsset(
        monster: WildMonster, slot: ArmorSlot, assetId: String,
        placement: ArmorOffset
    ): WildMonster {
        val asset = CustomArmorLayers.validated(mapOf(slot.name to assetId))
            .getValue(slot.name)
        val nextParts = CustomArmorLayers.validated(
            monster.customArmorParts + (slot.name to asset))
        val nextOffsets = CustomArmorLayers.validatedOffsets(
            monster.customArmorOffsets + (slot.name to placement))
        return monster.copy(
            customArmorParts = nextParts,
            customArmorOffsets = nextOffsets
        )
    }

    fun reposition(
        monster: WildMonster, slot: ArmorSlot, placement: ArmorOffset
    ): WildMonster {
        val id = monster.customArmorParts[slot.name]
            ?: throw IllegalArgumentException("この部位に画像パーツがありません")
        return withAsset(monster, slot, id, placement)
    }

    /** Only for the visual review; does not evolve the real monster. */
    fun visiblePreview(monster: WildMonster, slot: ArmorSlot): WildMonster =
        if (monster.stage >= slot.minStage) monster
        else monster.copy(stage = slot.minStage)
}
