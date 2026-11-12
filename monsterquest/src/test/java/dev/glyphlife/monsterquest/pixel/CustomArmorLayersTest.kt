package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.FusionRules
import dev.glyphlife.monsterquest.model.QuestState
import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CustomArmorLayersTest {
    private val idA="10000000-0000-4000-8000-000000000001"
    private val idB="10000000-0000-4000-8000-000000000002"

    private fun monster(stage:Int=3)=WildMonster(
        id="00000000-0000-4000-8000-000000000123",
        species="試験装甲獣",element="雷",maxHp=50,attack=9,
        bodyType=MonsterBodyType.BEAST.name,stage=stage
    )
    private fun sampleLayer(): PixelSprite {
        val input=IntArray(64*64)
        for(y in 18..39) for(x in 22..41) input[y*64+x]=0xfffdeacc.toInt()
        return CustomArmorLayers.normalize(PixelSprite.fromArgb(64,64,input))
    }

    @Test fun transparentLayerDoesNotAlterBaseWhenUnassigned() {
        val wild=monster()
        val base=MonsterDesign.sprite(wild)
        val normalized=sampleLayer()
        assertEquals(64,normalized.width)
        assertEquals(64,normalized.height)
        assertEquals(0,normalized.pixel(0,0))
        assertTrue(CustomArmorLayers.compose(base,wild) { normalized }.pixels()
            .contentEquals(base.pixels()))
    }

    @Test fun imageLayersModifyVisiblePixelsButDoNotChangePaletteOrSize() {
        val layer=sampleLayer()
        for(slot in ArmorSlot.entries) {
            val wild=monster().copy(customArmorParts=mapOf(slot.name to idA))
            val base=MonsterDesign.sprite(wild.copy(customArmorParts=emptyMap()))
            val rendered=CustomArmorLayers.compose(base,wild) { layer }
            assertEquals(64,rendered.width)
            assertEquals(64,rendered.height)
            assertTrue(rendered.colors().size<=16)
            assertTrue("Layer not visually changed: $slot",
                !rendered.pixels().contentEquals(base.pixels()))
            val bits=GlyphProjector.project(rendered)
            assertEquals(169,bits.length)
            bits.forEachIndexed { i,c ->
                if (c=='1') assertTrue(GlyphProjector.isPhysical(i))
            }
        }
    }

    @Test fun plannedLayerStaysHiddenUntilUnlockAndThenAppears() {
        val part=sampleLayer()
        val wild=monster(stage=1).copy(customArmorParts=mapOf("WING" to idA))
        val first=MonsterDesign.sprite(wild)
        assertArrayEquals(first.pixels(),CustomArmorLayers.compose(first,wild) { part }.pixels())
        val evolved=wild.copy(stage=3)
        val later=MonsterDesign.sprite(evolved)
        val composed=CustomArmorLayers.compose(later,evolved) { part }
        assertFalse(later.pixels().contentEquals(composed.pixels()))
    }

    @Test fun validMapIsStrictlyUuidScopedAndRejectsArbitraryPaths() {
        assertEquals(mapOf("HEAD" to idA),CustomArmorLayers.validated(mapOf("HEAD" to idA)))
        for (candidate in listOf("../secret.png","/etc/passwd","foo","")) {
            try { CustomArmorLayers.validated(mapOf("HEAD" to candidate)); fail(candidate) }
            catch (_:IllegalArgumentException){}
        }
        try { CustomArmorLayers.validated(mapOf("INVALID" to idA)); fail() }
        catch (_:IllegalArgumentException){}
    }

    @Test fun magentaBackgroundIsRemovedAndSolidOpaqueBackgroundRejected() {
        val solid=IntArray(64*64) { 0xffff00ff.toInt() }
        for(y in 20..35) for(x in 20..35)solid[y*64+x]=0xff283844.toInt()
        val cut=CustomArmorLayers.normalize(PixelSprite.fromArgb(64,64,solid))
        assertEquals(0,cut.pixel(0,0))
        assertTrue(cut.opaqueCount()>0)
        val uncut=PixelSprite.fromArgb(64,64,IntArray(4096){0xff123456.toInt()})
        try { CustomArmorLayers.normalize(uncut);fail("background should be rejected") }
        catch (_:IllegalArgumentException){}
    }

    @Test fun fusionInheritsImmutableImageReferencesAndMatchesConfirmation() {
        val a=monster().copy(id="00000000-0000-4000-8000-000000000101",
            customArmorParts=mapOf("HEAD" to idA,"CORE" to idB))
        val b=monster().copy(id="00000000-0000-4000-8000-000000000102",
            customArmorParts=mapOf("HEAD" to idB,"WING" to idA,"TAIL" to idB))
        val proposal=FusionRules.preview(a,b)
        assertEquals(idA,proposal.customArmorParts["HEAD"])
        assertEquals(idB,proposal.customArmorParts["CORE"])
        assertEquals(idA,proposal.customArmorParts["WING"])
        assertEquals(idB,proposal.customArmorParts["TAIL"])
        val result=FusionRules.fuse(QuestState(captures=listOf(a,b)),a.id,b.id)
            .captures.single()
        assertEquals(proposal.customArmorParts,result.customArmorParts)
        val part=sampleLayer()
        val before=CustomArmorLayers.compose(MonsterDesign.sprite(proposal),proposal) { part }
        val after=CustomArmorLayers.compose(MonsterDesign.sprite(result),result) { part }
        assertArrayEquals(before.pixels(),after.pixels())
    }
    @Test fun pixelOffsetsChangeOnlyCompositeNotImmutableSource() {
        val layer=sampleLayer()
        val root=monster(stage=3).copy(customArmorParts=mapOf("HEAD" to idA))
        val normal=CustomArmorLayers.compose(MonsterDesign.sprite(root),root) { layer }
        val withOffset=root.copy(customArmorOffsets=mapOf(
            "HEAD" to ArmorOffset(10,-6)))
        val shifted=CustomArmorLayers.compose(MonsterDesign.sprite(withOffset),
            withOffset) { layer }
        assertFalse(normal.pixels().contentEquals(shifted.pixels()))
        assertEquals(64,shifted.width)
        assertTrue(shifted.colors().size<=16)
        assertEquals(0,layer.pixel(0,0))
        assertEquals(10,withOffset.customArmorOffsets.getValue("HEAD").dx)
        assertTrue(GlyphProjector.project(shifted).all { it=='0'||it=='1' })
    }

    @Test fun offsetBoundsAreStrictAndMergePreservesTheCorrectParentSide() {
        try { ArmorOffset(13,0);fail("offset should be bounded") }
        catch (_:IllegalArgumentException) {}
        val first=monster(stage=3).copy(
            id="00000000-0000-4000-8000-000000000211",
            customArmorParts=mapOf("HEAD" to idA, "WING" to idA),
            customArmorOffsets=mapOf("HEAD" to ArmorOffset(-4,6),
                "WING" to ArmorOffset(8,0)))
        val second=monster(stage=3).copy(
            id="00000000-0000-4000-8000-000000000212",
            customArmorParts=mapOf("HEAD" to idB,"WING" to idB),
            customArmorOffsets=mapOf("HEAD" to ArmorOffset(5,0),
                "WING" to ArmorOffset(-8,4)))
        val fused=FusionRules.preview(first,second)
        assertEquals(ArmorOffset(-4,6),fused.customArmorOffsets["HEAD"])
        assertEquals(ArmorOffset(-8,4),fused.customArmorOffsets["WING"])
        assertEquals(ArmorOffset(-12,4),ArmorOffset(-11,2).moved(-2,2))
        val actual=FusionRules.fuse(
            QuestState(captures=listOf(first,second)),first.id,second.id
        ).captures.single()
        assertEquals(fused.customArmorOffsets,actual.customArmorOffsets)
        val art=sampleLayer()
        assertArrayEquals(
            CustomArmorLayers.compose(MonsterDesign.sprite(fused),fused) { art }.pixels(),
            CustomArmorLayers.compose(MonsterDesign.sprite(actual),actual) { art }.pixels()
        )
    }

    @Test fun scalableImageLayerUsesNonDestructiveSizeMetadata() {
        val asset=sampleLayer()
        val monster=monster(stage=3).copy(customArmorParts=mapOf("HEAD" to idA))
        fun frame(item:WildMonster):PixelSprite =
            CustomArmorLayers.compose(MonsterDesign.sprite(item),item) { asset }
        val normal=frame(monster)
        val small=frame(monster.copy(customArmorOffsets=
            mapOf("HEAD" to ArmorOffset(0,0,50))))
        val large=frame(monster.copy(customArmorOffsets=
            mapOf("HEAD" to ArmorOffset(0,0,150))))
        assertFalse(normal.pixels().contentEquals(small.pixels()))
        assertFalse(normal.pixels().contentEquals(large.pixels()))
        assertTrue(small.colors().size<=16)
        assertTrue(large.colors().size<=16)
        assertEquals(0,asset.pixel(0,0))
        assertEquals(120,ArmorOffset(2,-1,110).scaled(10).scalePercent)
        assertEquals(110,ArmorOffset(2,-1,110).moved(1,1).scalePercent)
        assertEquals(50,ArmorOffset(0,0,50).scaled(-10).scalePercent)
        assertEquals(150,ArmorOffset(0,0,150).scaled(10).scalePercent)
        try { ArmorOffset(0,0,151);fail("Bad scale accepted") }
        catch (_:IllegalArgumentException) {}
        val transferred=monster.copy(customArmorOffsets=mapOf("HEAD" to ArmorOffset(1,2,140)))
        val secondary=monster.copy(id="00000000-0000-4000-8000-000000000612")
        assertEquals(140,FusionRules.preview(transferred,secondary)
            .customArmorOffsets["HEAD"]?.scalePercent)
    }

}
