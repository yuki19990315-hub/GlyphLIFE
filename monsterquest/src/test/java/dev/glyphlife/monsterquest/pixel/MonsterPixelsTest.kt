package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class MonsterPixelsTest {
    private fun creature(type: MonsterBodyType, stage: Int=1, element: String="雷") =
        WildMonster(
            id="00000000-0000-0000-0000-000000000001",
            species="original-" + type.name, element=element, maxHp=40, attack=5,
            bodyType=type.name, stage=stage
        )

    @Test fun allTenBodyTypesProduceNative64Sprites() {
        val silhouettes=mutableSetOf<String>()
        for(type in MonsterBodyType.entries) {
            val sprite=MonsterDesign.sprite(creature(type))
            assertEquals(64,sprite.width)
            assertEquals(64,sprite.height)
            assertEquals(4096,sprite.pixels().size)
            assertTrue(type.name + ": empty",sprite.opaqueCount() in 120..3600)
            assertTrue(type.name + ": palette too large",sprite.colors().size in 3..16)
            silhouettes.add(sprite.pixels().joinToString(","))
        }
        assertEquals(10,silhouettes.size)
    }

    @Test fun paletteGivesDifferentColorIdentityAcrossSevenElements() {
        val keys=listOf("火","水","草","雷","光","闇","無")
        assertEquals(7,keys.map { MonsterDesign.palette(it).toList() }.distinct().size)
        keys.forEach { element ->
            val s=MonsterDesign.sprite(creature(MonsterBodyType.DRAGON,element=element))
            assertTrue(s.colors().isNotEmpty())
        }
    }

    @Test fun evolutionKeepsAnatomicalTypeButAddsVisualChange() {
        val wild=creature(MonsterBodyType.BEAST)
        val a=MonsterDesign.sprite(wild).pixels()
        val b=MonsterDesign.sprite(wild.copy(stage=2)).pixels()
        val c=MonsterDesign.sprite(wild.copy(stage=3)).pixels()
        assertFalse(a.contentEquals(b))
        assertFalse(b.contentEquals(c))
        assertEquals(MonsterBodyType.BEAST,MonsterDesign.bodyType(wild.copy(stage=3)))
    }

    @Test fun glyphProjectionContainsOnlyRealPhysicalLeds() {
        val glyphs=mutableSetOf<String>()
        for(type in MonsterBodyType.entries) {
            val bits=GlyphProjector.project(MonsterDesign.sprite(creature(type)))
            assertEquals(169,bits.length)
            assertTrue(bits.all { it=='0'||it=='1' })
            assertTrue(bits.count { it=='1' } in 12..137)
            bits.forEachIndexed { i, bit ->
                if(bit=='1')assertTrue("lit unwired LED " + i,GlyphProjector.isPhysical(i))
            }
            glyphs.add(bits)
        }
        assertTrue("Glyph silhouettes should vary",glyphs.size>=5)
        assertEquals(137,(0..168).count(GlyphProjector::isPhysical))
    }

    @Test fun nearestNeighbor256IsPixelExact() {
        val s=MonsterDesign.sprite(creature(MonsterBodyType.MECHA))
        val large=s.nearestScaled(256,256)
        assertEquals(256,large.width)
        assertEquals(256,large.height)
        for(y in 0 until 64 step 2)for(x in 0 until 64 step 2) {
            val color=s.pixel(x,y)
            for(dy in 0..3)for(dx in 0..3)
                assertEquals(color,large.pixel(x*4+dx,y*4+dy))
        }
    }

    @Test fun originalPixelArrayCannotBeExternallyMutated() {
        val s=MonsterDesign.sprite(creature(MonsterBodyType.SPIRIT))
        val original=s.pixel(32,29)
        val copy=s.pixels()
        copy[29*64+32]=0
        assertEquals(original,s.pixel(32,29))
    }

    @Test fun projectorRejectsTinySource() {
        try{
            GlyphProjector.project(PixelSprite.fromArgb(12,12,IntArray(144)))
            fail("Expected failure")
        }catch(_:IllegalArgumentException){}
    }
}
