package dev.glyphlife.monsterquest.pixel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

/**
 * Explicitly converts a user/imported or AI-generated image to a native
 * 64x64 limited-palette PNG source; this is NOT just resizing a 1K image for
 * display. The 256x256 presentation is a lossless nearest-neighbour upsample.
 */
object PixelAssetPipeline {
    const val NATIVE_SIZE = 64
    private const val MAX_COLORS = 16

    fun fromBitmap(source: Bitmap): PixelSprite {
        require(source.width in 16..8192 && source.height in 16..8192)
        // Fit full body rather than crop horns, wings and tail.
        val ratio=min(NATIVE_SIZE.toFloat()/source.width,NATIVE_SIZE.toFloat()/source.height)
        val targetW=max(1,(source.width*ratio).toInt())
        val targetH=max(1,(source.height*ratio).toInt())
        val resized=Bitmap.createScaledBitmap(source,targetW,targetH,false)
        val canvasBitmap=Bitmap.createBitmap(NATIVE_SIZE,NATIVE_SIZE,Bitmap.Config.ARGB_8888)
        try {
            Canvas(canvasBitmap).drawBitmap(
                resized, ((NATIVE_SIZE-targetW)/2).toFloat(),
                ((NATIVE_SIZE-targetH)/2).toFloat(), Paint().apply { isFilterBitmap=false }
            )
            val pixels=IntArray(NATIVE_SIZE*NATIVE_SIZE)
            canvasBitmap.getPixels(pixels,0,NATIVE_SIZE,0,0,NATIVE_SIZE,NATIVE_SIZE)
            removeUniformBackground(pixels)
            return PixelSprite.fromArgb(NATIVE_SIZE,NATIVE_SIZE,paletteReduce(pixels))
        } finally {
            if(resized !== source) resized.recycle()
            canvasBitmap.recycle()
        }
    }

    fun toBitmap(sprite: PixelSprite): Bitmap =
        Bitmap.createBitmap(sprite.pixels(),sprite.width,sprite.height,Bitmap.Config.ARGB_8888)

    fun display256(sprite: PixelSprite): Bitmap {
        val native=toBitmap(sprite)
        return try { Bitmap.createScaledBitmap(native,256,256,false) }
        finally { native.recycle() }
    }

    /** Remove only EDGE-CONNECTED near-uniform background, never interior colors. */
    private fun removeUniformBackground(pixels:IntArray) {
        val side=NATIVE_SIZE
        // All image corners must be opaque and similar to avoid deleting a
        // creature's near-solid body color by mistake.
        val cornerIndices=intArrayOf(0,side-1,side*(side-1),side*side-1)
        val corners=cornerIndices.map { pixels[it] }
        if(corners.any { (it ushr 24) < 245 })return
        val bg=corners[0]
        if(corners.any { distanceSq(it,bg)>24*24 })return
        val visited=BooleanArray(pixels.size)
        val q=ArrayDeque<Int>()
        cornerIndices.forEach { q.add(it) }
        while(q.isNotEmpty()) {
            val i=q.removeFirst()
            if(visited[i])continue
            visited[i]=true
            val c=pixels[i]
            if((c ushr 24)<200 || distanceSq(c,bg)>42*42)continue
            pixels[i]=0
            val x=i%side;val y=i/side
            if(x>0)q.add(i-1)
            if(x<side-1)q.add(i+1)
            if(y>0)q.add(i-side)
            if(y<side-1)q.add(i+side)
        }
    }

    private fun distanceSq(a:Int,b:Int):Int {
        val dr=((a shr 16) and 255)-((b shr 16) and 255)
        val dg=((a shr 8) and 255)-((b shr 8) and 255)
        val db=(a and 255)-(b and 255)
        return dr*dr+dg*dg+db*db
    }

    /**
     * Deterministic palette reduction. Greedy weighted farthest colors retain
     * both frequent material colors and contrast accents rather than flattening
     * an entire sprite to the modal background color.
     */
    fun paletteReduce(input:IntArray):IntArray {
        val opaque=input.filter { (it ushr 24)>=96 }
        if(opaque.isEmpty())return IntArray(input.size)
        val counts=opaque.groupingBy { it or (0xff shl 24) }.eachCount()
        val candidates=counts.entries.sortedWith(
            compareByDescending<Map.Entry<Int,Int>> { it.value }.thenBy { it.key }
        ).take(512)
        val selected=ArrayList<Int>()
        selected.add(candidates.first().key)
        while(selected.size<MAX_COLORS && selected.size<candidates.size) {
            val candidate=candidates.asSequence().filter { it.key !in selected }
                .maxWithOrNull(compareBy<Map.Entry<Int,Int>> {
                    val nearest=selected.minOf { s -> distanceSq(it.key,s) }
                    nearest.toLong() * (2 + it.value.coerceAtMost(16))
                }.thenBy { it.value }) ?: break
            selected.add(candidate.key)
        }
        return IntArray(input.size) { i ->
            val pixel=input[i]
            if((pixel ushr 24)<96)0 else selected.minBy { distanceSq(it,pixel) }
        }
    }
}
