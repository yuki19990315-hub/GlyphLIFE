package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Visual QA samples are rendered from the EXACT runtime 64x64 procedural
 * sprite generator, not separately invented reference artwork.
 */
class MonsterPixelPreviewTest {
    private fun monster(type:MonsterBodyType, element:String="雷", stage:Int=1):WildMonster =
        WildMonster(
            id="0f495a0b-8147-4a0a-b5b1-f6c6ff02adca",
            species="demo-" + type.name,
            element=element, maxHp=50, attack=7, bodyType=type.name,stage=stage
        )

    private fun outputDir():File {
        val root=System.getenv("GITHUB_WORKSPACE")?.let { File(it) }
            ?: File(System.getProperty("user.dir")).let { cwd ->
                if(cwd.name=="monsterquest")cwd.parentFile else cwd
            }
        return File(root,"monsterquest/build/pixel-preview").also { check(it.isDirectory || it.mkdirs()) }
    }

    private fun spriteToSheet(
        sheet:BufferedImage, sprite:PixelSprite,
        col:Int,row:Int,cols:Int, scale:Int=4
    ) {
        val cell=64*scale
        val originX=col*cell;val originY=row*cell
        // Opaque dark stage with transparent pixel artwork on top.
        for(y in 0 until cell)for(x in 0 until cell)
            sheet.setRGB(originX+x,originY+y,0xff19222f.toInt())
        for(y in 0 until 64)for(x in 0 until 64) {
            val color=sprite.pixel(x,y)
            if((color ushr 24)<96)continue
            for(dy in 0 until scale)for(dx in 0 until scale) {
                sheet.setRGB(originX+x*scale+dx,originY+y*scale+dy,color)
            }
        }
    }

    private fun save(sheet:BufferedImage,name:String):File {
        val file=File(outputDir(),name)
        check(ImageIO.write(sheet,"png",file))
        assertTrue(file.isFile && file.length()>1000)
        return file
    }

    @Test fun allBodyTypesVisualReview() {
        val types=MonsterBodyType.entries
        val sheet=BufferedImage(5*256,2*256,BufferedImage.TYPE_INT_ARGB)
        types.forEachIndexed { i,type ->
            spriteToSheet(sheet,MonsterDesign.sprite(monster(type)),i%5,i/5,5)
        }
        save(sheet,"01-10-body-types.png")
    }

    @Test fun allSevenElementsVisualReview() {
        val elements=listOf("火","水","草","雷","光","闇","無")
        val sheet=BufferedImage(7*256,256,BufferedImage.TYPE_INT_ARGB)
        elements.forEachIndexed { i,element ->
            spriteToSheet(sheet,MonsterDesign.sprite(monster(MonsterBodyType.DRAGON,element)),i,0,7)
        }
        save(sheet,"02-7-elements.png")
    }

    @Test fun threeEvolutionStagesVisualReview() {
        val sheet=BufferedImage(3*256,256,BufferedImage.TYPE_INT_ARGB)
        for(stage in 1..3) {
            spriteToSheet(sheet,MonsterDesign.sprite(monster(MonsterBodyType.MECHA,"闇",stage)),
                stage-1,0,3)
        }
        save(sheet,"03-three-evolution-stages.png")
    }
}
