package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import org.junit.Assert.*
import org.junit.Test

class FullBodyGlyphProjectorTest {
    private val ra get() = WildMonster(
        id="00000000-0000-4000-8000-000000000111",
        species="ライゼル",element="雷",maxHp=40,attack=8,
        artFamily=MonsterArtFamilies.RAIZEL
    )

    @Test fun allEvolutionsAndBodyTypesUseGrayscaleNotFaceIcons() {
        val results = (1..3).map { stage ->
            val master=MonsterDesign.sprite(ra.copy(stage=stage))
            val levels=FullBodyGlyphProjector.project(master)
            assertEquals(169,levels.size)
            assertTrue(levels.all { it in 0..255 })
            assertTrue("No whole-body pixels in stage $stage",levels.count { it>0 } >= 10)
            assertTrue("Not a grayscale image in stage $stage",levels.filter { it>0 }.distinct().size >= 3)
            levels.forEachIndexed { i, level ->
                if(level>0) assertTrue("LED outside circle: $i",GlyphProjector.isPhysical(i))
            }
            assertEquals(FullBodyGlyphProjector.asBinary(levels),GlyphProjector.project(master))
            levels.toList()
        }
        assertEquals(3,results.distinct().size)

        val foreign=ra.copy(species="別の生き物",artFamily="",bodyType="DRAGON")
        val dragon=MonsterDesign.sprite(foreign)
        assertEquals(FullBodyGlyphProjector.asBinary(FullBodyGlyphProjector.project(dragon)),
            GlyphProjector.projectFor(foreign,dragon))
    }

    @Test fun importedArmorAndPaletteProduceDifferentBrightness() {
        val source=IntArray(4096) { i ->
            val x=i%64; val y=i/64
            if(x in 7..56 && y in 14..49)
                if(x<32) 0xff343343.toInt() else 0xfff5e4d6.toInt()
            else 0
        }
        val original=PixelSprite.fromArgb(64,64,source)
        val levels=FullBodyGlyphProjector.project(original)
        val left=(2..5).flatMap { y -> (2..5).map { x -> levels[y*13+x] } }
        val right=(2..5).flatMap { y -> (7..10).map { x -> levels[y*13+x] } }
        assertTrue("Bright area should outshine dark area",
            right.average() > left.average())
        assertFalse(levels.toList().all { it==0 })
    }

    @Test fun transparentAndBlackBrightnessYieldNoLight() {
        val transparent=PixelSprite.fromArgb(64,64,IntArray(4096))
        assertTrue(FullBodyGlyphProjector.project(transparent).all { it==0 })
        val sprite=MonsterDesign.sprite(ra)
        assertTrue(FullBodyGlyphProjector.project(sprite,
            FullBodyGlyphProjector.Options(brightness=0)).all { it==0 })
    }

    @Test fun everyLitPixelWithinHardwareMask() {
        for (type in MonsterBodyType.entries) {
            val wild=ra.copy(species="other-$type",bodyType=type.name,artFamily="")
            val result=FullBodyGlyphProjector.project(MonsterDesign.sprite(wild))
            assertEquals(169,result.size)
            result.forEachIndexed { i, level ->
                if(!GlyphProjector.isPhysical(i))assertEquals(0,level)
            }
        }
    }
}
