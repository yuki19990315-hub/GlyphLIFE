package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.FusionRules
import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class ArmorPlacementTest {
    private val asset="20000000-0000-4000-8000-000000000001"
    private val monster=WildMonster(
        id="00000000-0000-4000-8000-000000000555",
        species="装甲試験獣",element="雷",maxHp=55,attack=8,stage=3,
        bodyType="BEAST",customArmorParts=mapOf("HEAD" to asset)
    )
    private val art=PixelSprite.fromArgb(64,64,IntArray(4096) { i ->
        if (i%64 in 20..43 && i/64 in 14..35) 0xffffe68a.toInt() else 0
    })
    private fun rendered(w:WildMonster):PixelSprite =
        CustomArmorLayers.compose(MonsterDesign.sprite(
            w.copy(customArmorParts=emptyMap())),w) { art }

    @Test fun zeroAdjustmentMatchesOldSave() {
        assertArrayEquals(rendered(monster).pixels(),
            rendered(monster.copy(partPlacements=mapOf("HEAD" to ArmorPlacement()))).pixels())
    }

    @Test fun moveAndResizeChangeReal64PixelsAndKeepGlyphMask() {
        val normal=rendered(monster).pixels()
        val moved=rendered(monster.copy(partPlacements=mapOf(
            "HEAD" to ArmorPlacement(8,8,100))))
        val shrunk=rendered(monster.copy(partPlacements=mapOf(
            "HEAD" to ArmorPlacement(0,0,50))))
        assertFalse(normal.contentEquals(moved.pixels()))
        assertFalse(normal.contentEquals(shrunk.pixels()))
        for (sprite in listOf(moved,shrunk)) {
            assertEquals(64,sprite.width)
            assertTrue(sprite.colors().size<=16)
            GlyphProjector.project(sprite).forEachIndexed { i,c ->
                if (c=='1') assertTrue(GlyphProjector.isPhysical(i))
            }
        }
    }

    @Test fun invalidPositionsAndSlotsAreRejected() {
        for (candidate in listOf(-13,13)) {
            try { ArmorPlacement(offsetX=candidate);fail() }
            catch (_:IllegalArgumentException) {}
        }
        for (percent in listOf(49,151)) {
            try { ArmorPlacement(sizePercent=percent);fail() }
            catch (_:IllegalArgumentException) {}
        }
        try {CustomArmorLayers.validatedPlacements(mapOf("NO" to ArmorPlacement()));fail()}
        catch (_:IllegalArgumentException) {}
    }

    @Test fun fusionInheritsAssetAndMatchingPlacementFromEachParent() {
        val a=monster.copy(id="00000000-0000-4000-8000-000000000101",
            customArmorParts=mapOf("HEAD" to asset,"WING" to asset),
            partPlacements=mapOf("HEAD" to ArmorPlacement(3,-4,130),
                "WING" to ArmorPlacement(4,5,80)))
        val b=monster.copy(id="00000000-0000-4000-8000-000000000102",
            customArmorParts=mapOf("HEAD" to asset,"WING" to asset),
            partPlacements=mapOf("HEAD" to ArmorPlacement(-5,6,70),
                "WING" to ArmorPlacement(-6,-7,120)))
        val fused=FusionRules.preview(a,b)
        assertEquals(a.partPlacements["HEAD"],fused.partPlacements["HEAD"])
        assertEquals(b.partPlacements["WING"],fused.partPlacements["WING"])
        assertEquals(2,fused.partPlacements.size)
        assertEquals(3,a.partPlacements["HEAD"]?.offsetX)
    }
}
