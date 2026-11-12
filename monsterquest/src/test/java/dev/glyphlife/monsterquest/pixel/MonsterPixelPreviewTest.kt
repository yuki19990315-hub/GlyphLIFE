package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * Produces actual pixel previews from the runtime generator. A standalone
 * PNG encoder avoids java.awt (unavailable to Android's JVM test classpath).
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

    private fun drawCell(
        sheet:IntArray,width:Int,sprite:PixelSprite,
        col:Int,row:Int,scale:Int=4
    ) {
        val cell=64*scale
        val x0=col*cell;val y0=row*cell
        for(y in 0 until cell)for(x in 0 until cell)
            sheet[(y0+y)*width+x0+x]=0xff19222f.toInt()
        for(y in 0 until 64)for(x in 0 until 64) {
            val color=sprite.pixel(x,y)
            if((color ushr 24)<96)continue
            for(dy in 0 until scale)for(dx in 0 until scale)
                sheet[(y0+y*scale+dy)*width+x0+x*scale+dx]=color
        }
    }

    private fun writeChunk(out:DataOutputStream,name:String,data:ByteArray) {
        val type=name.toByteArray(Charsets.US_ASCII)
        require(type.size==4)
        out.writeInt(data.size)
        out.write(type);out.write(data)
        val crc=CRC32()
        crc.update(type);crc.update(data)
        out.writeInt(crc.value.toInt())
    }

    /** PNG RGBA8, filter type 0, zlib IDAT. No OS graphics API required. */
    private fun png(width:Int,height:Int,pixels:IntArray):ByteArray {
        require(pixels.size==width*height)
        val raw=ByteArrayOutputStream(height*(width*4+1))
        for(y in 0 until height) {
            raw.write(0)
            for(x in 0 until width) {
                val color=pixels[y*width+x]
                raw.write((color ushr 16) and 255)
                raw.write((color ushr 8) and 255)
                raw.write(color and 255)
                raw.write((color ushr 24) and 255)
            }
        }
        val compressed=ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { it.write(raw.toByteArray()) }
        val output=ByteArrayOutputStream()
        val out=DataOutputStream(output)
        out.write(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))
        val header=ByteArrayOutputStream()
        DataOutputStream(header).use {
            it.writeInt(width);it.writeInt(height)
            it.writeByte(8);it.writeByte(6);it.writeByte(0)
            it.writeByte(0);it.writeByte(0)
        }
        writeChunk(out,"IHDR",header.toByteArray())
        writeChunk(out,"IDAT",compressed.toByteArray())
        writeChunk(out,"IEND",byteArrayOf())
        out.flush()
        return output.toByteArray()
    }

    private fun save(width:Int,height:Int,data:IntArray,name:String):File {
        val file=File(outputDir(),name)
        file.writeBytes(png(width,height,data))
        assertTrue(file.isFile && file.length()>1000)
        return file
    }

    @Test fun allBodyTypesVisualReview() {
        val types=MonsterBodyType.entries
        val w=5*256;val h=2*256;val data=IntArray(w*h)
        types.forEachIndexed { i,type ->
            drawCell(data,w,MonsterDesign.sprite(monster(type)),i%5,i/5)
        }
        save(w,h,data,"01-10-body-types.png")
    }

    @Test fun allSevenElementsVisualReview() {
        val elements=listOf("火","水","草","雷","光","闇","無")
        val w=7*256;val h=256;val data=IntArray(w*h)
        elements.forEachIndexed { i,element ->
            drawCell(data,w,MonsterDesign.sprite(monster(MonsterBodyType.DRAGON,element)),i,0)
        }
        save(w,h,data,"02-7-elements.png")
    }

    @Test fun originalIdleAndAttackFramesVisualReview() {
        val wild=monster(MonsterBodyType.DRAGON,"火",2)
        val master=MonsterDesign.sprite(wild)
        val frames=MonsterAnimation.idle(master,wild)+MonsterAnimation.attack(master,wild)
        val w=frames.size*256;val h=256;val data=IntArray(w*h)
        frames.forEachIndexed { index,frame ->
            drawCell(data,w,frame.sprite,index,0)
        }
        save(w,h,data,"04-idle-and-attack-animation.png")
    }

    @Test fun threeEvolutionStagesVisualReview() {
        val w=3*256;val h=256;val data=IntArray(w*h)
        for(stage in 1..3)
            drawCell(data,w,MonsterDesign.sprite(monster(MonsterBodyType.MECHA,"闇",stage)),
                stage-1,0)
        save(w,h,data,"03-three-evolution-stages.png")
    }
}
