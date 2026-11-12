package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.model.MonsterPrototypeFactory
import dev.glyphlife.monsterquest.model.FusionRules
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

    /** Exact game renderer output: compare the authored lightning and moonlight
     * creature families side-by-side at every evolution stage.
     */
    @Test fun featuredArtFamilyVisualReview() {
        val width=3*256;val height=2*256;val data=IntArray(width*height)
        for(stage in 1..3) {
            val ra = WildMonster(
                id="preview-raizel",species="ライゼル",element="雷",
                maxHp=30,attack=8,stage=stage,
                artFamily=dev.glyphlife.monsterquest.model.MonsterArtFamilies.RAIZEL
            )
            val luna = WildMonster(
                id="preview-luna",species="精霊",element="光",
                maxHp=30,attack=8,stage=stage,
                artFamily=dev.glyphlife.monsterquest.model.MonsterArtFamilies.LUNA
            )
            drawCell(data,width,MonsterDesign.sprite(ra),stage-1,0)
            drawCell(data,width,MonsterDesign.sprite(luna),stage-1,1)
        }
        save(width,height,data,"10-raizel-luna-3-stages.png")
    }

    @Test fun armorPositionVisualReview() {
        val base=monster(MonsterBodyType.BEAST,"雷",3)
        val asset=IntArray(4096)
        for(y in 18..43)for(x in 15..47) {
            asset[y*64+x]=if((x+y)%5==0)0xfffaf4d4.toInt() else 0xff7869aa.toInt()
        }
        val layer=CustomArmorLayers.normalize(PixelSprite.fromArgb(64,64,asset))
        val positions=listOf(ArmorOffset(-10,0),ArmorOffset(),ArmorOffset(10,0))
        val width=positions.size*256
        val data=IntArray(width*256)
        positions.forEachIndexed { col, pos ->
            val creature=base.copy(
                customArmorParts=mapOf("HEAD" to
                    "10000000-0000-4000-8000-000000000001"),
                customArmorOffsets=mapOf("HEAD" to pos))
            val combined=CustomArmorLayers.compose(
                MonsterDesign.sprite(creature),creature) { layer }
            drawCell(data,width,combined,col,0)
        }
        save(width,256,data,"09-armor-position-left-center-right.png")
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
    /** 10 anatomy types × 4 distinct seeds: actual rendered data, not sketches. */
    @Test fun fourVariantsPerAnatomyVisualReview() {
        val variants=listOf(0,7,14,21)
        val types=MonsterBodyType.entries
        val w=variants.size*256;val h=types.size*256;val pixels=IntArray(w*h)
        types.forEachIndexed { row,type ->
            variants.forEachIndexed { col,variant ->
                val wild=MonsterPrototypeFactory.preview(
                    "原生幻獣",type,"雷",1,variant
                )
                drawCell(pixels,w,MonsterDesign.sprite(wild),col,row)
            }
        }
        save(w,h,pixels,"05-silhouette-10-types-4-variants.png")
    }

    /** A predictable fusion result must match the preview before capture is spent. */
    @Test fun fusionBeforeAfterVisualReview() {
        val first=MonsterPrototypeFactory.preview(
            "焔牙獣",MonsterBodyType.BEAST,"火",2,4
        ).copy(id="00000000-0000-4000-8000-000000000101")
        val second=MonsterPrototypeFactory.preview(
            "雷翼竜",MonsterBodyType.DRAGON,"雷",2,11
        ).copy(id="00000000-0000-4000-8000-000000000102")
        val result=FusionRules.preview(first,second)
        val w=3*256;val h=256;val pixels=IntArray(w*h)
        listOf(first,second,result).forEachIndexed { index,wild ->
            drawCell(pixels,w,MonsterDesign.sprite(wild),index,0)
        }
        save(w,h,pixels,"06-fusion-parent-a-parent-b-result.png")
    }

    /** The actual 13x13 mask, intentionally kept distinct from 64px masters. */
    @Test fun glyphConversionVisualReview() {
        val types=MonsterBodyType.entries
        val cell=13*14
        val width=types.size*cell
        val data=IntArray(width*cell) { 0xff1b2632.toInt() }
        types.forEachIndexed { col,type ->
            val wild=MonsterPrototypeFactory.preview("試作",type,"雷",1,8)
            val glyph=GlyphProjector.project(MonsterDesign.sprite(wild))
            for(y in 0..12)for(x in 0..12) {
                if(glyph[y*13+x]=='1') {
                    for(dy in 2 until 12)for(dx in 2 until 12)
                        data[(y*14+dy)*width+col*cell+x*14+dx]=0xffe5f5b2.toInt()
                }
            }
        }
        save(width,cell,data,"07-glyph-13x13-10-body-types.png")
    }

    @Test fun featuredRaizelThreeStageVisualReview() {
        val w=3*256;val h=256;val pixels=IntArray(w*h)
        (1..3).forEach { stage ->
            val monster=WildMonster(
                id="00000000-0000-4000-8000-000000000111",
                species="ライゼル",element="雷",maxHp=40,attack=8,stage=stage
            )
            drawCell(pixels,w,MonsterDesign.sprite(monster),stage-1,0)
        }
        save(w,h,pixels,"08-featured-raizel-3-stages.png")
    }

    @Test fun modularArmorAndStageEvolutionVisualReview() {
        val base=MonsterPrototypeFactory.preview(
            "幻装獣",MonsterBodyType.BEAST,"雷",1,9)
        val parts=MonsterArmor.suggestion("雷",MonsterBodyType.BEAST)
        val sprites=listOf(
            base,
            base.copy(armorParts=parts),
            base.copy(stage=2,armorParts=parts),
            base.copy(stage=3,armorParts=parts)
        )
        val w=4*256;val h=256;val pixels=IntArray(w*h)
        sprites.forEachIndexed { index,wild ->
            drawCell(pixels,w,MonsterDesign.sprite(wild),index,0)
        }
        save(w,h,pixels,"09-base-armor-evolution.png")
    }

    @Test fun armorStylesComparisonVisualReview() {
        val base=MonsterPrototypeFactory.preview(
            "結晶獣",MonsterBodyType.DRAGON,"水",3,12)
        val candidates=listOf(base)+ArmorStyle.entries.map { style ->
            base.copy(armorParts=ArmorSlot.entries.associate { it.name to style.name })
        }
        val w=5*256;val h=256;val pixels=IntArray(w*h)
        candidates.forEachIndexed { index,wild ->
            drawCell(pixels,w,MonsterDesign.sprite(wild),index,0)
        }
        save(w,h,pixels,"10-armor-4-styles.png")
    }

    @Test fun imagePartScaleFiveWayVisualReview() {
        val base=MonsterPrototypeFactory.preview(
            "装備サイズ比較",MonsterBodyType.BEAST,"雷",3,4)
        val id="20000000-0000-4000-8000-000000000001"
        val module=PixelSprite.fromArgb(64,64,IntArray(4096) { n ->
            if (n%64 in 23..42 && n/64 in 13..37) 0xffedcf8a.toInt() else 0
        })
        val variants=listOf(
            base,
            base.copy(customArmorParts=mapOf("HEAD" to id)),
            base.copy(customArmorParts=mapOf("HEAD" to id),
                customArmorOffsets=mapOf("HEAD" to ArmorOffset(0,0,50))),
            base.copy(customArmorParts=mapOf("HEAD" to id),
                customArmorOffsets=mapOf("HEAD" to ArmorOffset(0,0,150))),
            base.copy(customArmorParts=mapOf("HEAD" to id),
                customArmorOffsets=mapOf("HEAD" to ArmorOffset(6,-5,120)))
        )
        val width=variants.size*256
        val pixels=IntArray(width*256)
        variants.forEachIndexed { col,wild ->
            val sprite=CustomArmorLayers.compose(MonsterDesign.sprite(wild),wild) { module }
            drawCell(pixels,width,sprite,col,0)
        }
        save(width,256,pixels,"12-custom-armor-scale-comparison.png")
    }

}
