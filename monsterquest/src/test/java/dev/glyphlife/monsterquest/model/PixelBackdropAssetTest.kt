package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.FileInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.abs

/** Checks the REAL shipped pixel PNGs, not only their generation script. */
class PixelBackdropAssetTest {
    private val assetDir=sequenceOf(
        File("monsterquest/src/main/res/drawable-nodpi"),
        File("src/main/res/drawable-nodpi"))
        .firstOrNull{it.isDirectory} ?: File("src/main/res/drawable-nodpi")

    private data class Decoded(val width:Int,val height:Int,val pixels:IntArray)

    /** Small PNG decoder for test resources. Android Gradle tests cannot
     * compile against java.desktop's javax.imageio, so inspect the real PNG
     * IDAT bytes with java.io and java.util.zip only. Encoder uses RGBA8. */
    private fun decode(file:File):Decoded=DataInputStream(FileInputStream(file)).use{input->
        val header=ByteArray(8)
        input.readFully(header)
        assertArrayEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10),header)
        var width=0
        var height=0
        val idat=ByteArrayOutputStream()
        while(true) {
            val length=input.readInt()
            assertTrue("Malformed PNG chunk",length in 0..4_000_000)
            val tag=ByteArray(4)
            input.readFully(tag)
            val payload=ByteArray(length)
            input.readFully(payload)
            input.readInt() // CRC is verified by Android's PNG resource linker.
            when(String(tag,Charsets.US_ASCII)) {
                "IHDR" -> {
                    val ihdr=DataInputStream(ByteArrayInputStream(payload))
                    width=ihdr.readInt()
                    height=ihdr.readInt()
                    assertEquals("8-bit required",8,payload[8].toInt())
                    assertEquals("RGBA required",6,payload[9].toInt())
                    assertEquals("No interlacing",0,payload[12].toInt())
                }
                "IDAT" -> idat.write(payload)
                "IEND" -> break
            }
        }
        assertEquals(640,width)
        assertEquals(180,height)
        val bytes=InflaterInputStream(
            ByteArrayInputStream(idat.toByteArray())).use{it.readBytes()}
        val rowBytes=width*4
        assertEquals(height*(rowBytes+1),bytes.size)
        val colors=IntArray(width*height)
        var prior=ByteArray(rowBytes)
        var cursor=0
        for(y in 0 until height) {
            val filter=bytes[cursor++].toInt() and 255
            val line=ByteArray(rowBytes)
            for(i in line.indices) {
                val left=if(i>=4)line[i-4].toInt()and 255 else 0
                val above=prior[i].toInt()and 255
                val topLeft=if(i>=4)prior[i-4].toInt()and 255 else 0
                val prediction=when(filter) {
                    0->0
                    1->left
                    2->above
                    3->(left+above)/2
                    4->{
                        val p=left+above-topLeft
                        val a=abs(p-left)
                        val b=abs(p-above)
                        val c=abs(p-topLeft)
                        if(a<=b && a<=c)left else if(b<=c)above else topLeft
                    }
                    else->error("Bad PNG filter $filter")
                }
                val value=bytes[cursor++].toInt()and 255
                line[i]=((value+prediction)and 255).toByte()
            }
            for(x in 0 until width) {
                val i=x*4
                colors[y*width+x]=
                    ((line[i+3].toInt()and 255)shl 24) or
                    ((line[i].toInt()and 255)shl 16) or
                    ((line[i+1].toInt()and 255)shl 8) or
                    (line[i+2].toInt()and 255)
            }
            prior=line
        }
        Decoded(width,height,colors)
    }

    @Test fun allTenScenesContainFourValidLimitedPaletteLayers() {
        for(zone in listOf("meadow","forest","cave","ruins","volcano",
            "ice","coast","wasteland","dark","boss")) {
            val palette=mutableSetOf<Int>()
            for(layer in listOf("far","mid","ground","front")) {
                val png=File(assetDir,"mq_bg_${zone}_${layer}.png")
                assertTrue("PNG missing: ${png.path}",png.isFile)
                val image=decode(png)
                assertEquals(640,image.width)
                assertEquals(180,image.height)
                val sample=if(layer=="front")95 else 120
                if(layer=="front"||layer=="ground") {
                    for(x in 0 until 640)
                        assertEquals("Foreground hides combat sprites: ${png.name}",
                            0,image.pixels[sample*640+x] ushr 24)
                }
                for(argb in image.pixels) {
                    if((argb ushr 24)>0)palette.add(argb and 0xFFFFFF)
                    if(layer=="far")assertEquals(255,argb ushr 24)
                }
            }
            assertTrue("Too few colors in $zone: ${palette.size}",palette.size>=32)
            assertTrue("Palette overflow in $zone: ${palette.size}",palette.size<=64)
        }
    }
}
