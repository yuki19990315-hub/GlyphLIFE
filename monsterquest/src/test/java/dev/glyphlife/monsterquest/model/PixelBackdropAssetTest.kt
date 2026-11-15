package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** Checks the REAL shipped pixel PNGs, not only their generation script. */
class PixelBackdropAssetTest {
    private val assetDir=sequenceOf(
        File("monsterquest/src/main/res/drawable-nodpi"),
        File("src/main/res/drawable-nodpi"))
        .firstOrNull{it.isDirectory} ?: File("src/main/res/drawable-nodpi")

    @Test fun allTenScenesContainFourValidLimitedPaletteLayers() {
        for(zone in listOf("meadow","forest","cave","ruins","volcano",
            "ice","coast","wasteland","dark","boss")) {
            val palette=mutableSetOf<Int>()
            for(layer in listOf("far","mid","ground","front")) {
                val png=File(assetDir,"mq_bg_${zone}_${layer}.png")
                assertTrue("PNG missing: ${png.path}",png.isFile)
                val image=ImageIO.read(png)
                assertNotNull("PNG unreadable: ${png.path}",image)
                assertEquals(640,image.width)
                assertEquals(180,image.height)
                assertTrue("No alpha: ${png.name}",image.colorModel.hasAlpha())
                val sample=if(layer=="front")95 else 120
                if(layer=="front"||layer=="ground") {
                    for(x in 0 until 640)
                        assertEquals("Foreground hides combat sprites: ${png.name}",
                            0,image.getRGB(x,sample) ushr 24)
                }
                for(y in 0 until 180)for(x in 0 until 640) {
                    val argb=image.getRGB(x,y)
                    if((argb ushr 24)>0)palette.add(argb and 0xFFFFFF)
                    if(layer=="far")assertEquals(255,argb ushr 24)
                }
            }
            assertTrue("Too few colors in $zone: ${palette.size}",palette.size>=32)
            assertTrue("Palette overflow in $zone: ${palette.size}",palette.size<=64)
        }
    }
}
