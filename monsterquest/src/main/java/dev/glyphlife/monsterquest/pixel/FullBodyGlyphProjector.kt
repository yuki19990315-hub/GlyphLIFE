package dev.glyphlife.monsterquest.pixel

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Every LED of the Phone (4a) Pro's 13x13 Glyph Matrix receives its own
 * 0..255 monochrome brightness value, derived from the actual entire creature.
 *
 * - Source: the resolved, composited, animated 64px monster sprite.
 * - The ENTIRE alpha silhouette is retained, never replaced by a face icon.
 * - Bounding-box fit adapts to the round hardware mask, so paws and tail
 *   cannot be cropped by the physical LED boundary.
 * - Fractional pixel-area coverage preserves thin horns and legs.
 * - No Android classes, remote calls or per-species exceptions.
 */
object FullBodyGlyphProjector {
    const val SIZE = 13
    const val COUNT = SIZE * SIZE

    data class Options(
        val brightness: Int = 255,
        val contrast: Double = 1.15,
        val gamma: Double = 0.85,
        val alphaThreshold: Int = 16
    ) {
        init {
            require(brightness in 0..255)
            require(contrast in 0.5..2.0 && contrast.isFinite())
            require(gamma in 0.4..2.0 && gamma.isFinite())
            require(alphaThreshold in 1..255)
        }
    }

    fun project(sprite: PixelSprite, options: Options = Options()): IntArray {
        val sw = sprite.width
        val sh = sprite.height
        val src = sprite.pixels()
        var left=sw; var top=sh; var right=-1; var bottom=-1
        for (y in 0 until sh) for (x in 0 until sw) {
            if ((src[y*sw+x] ushr 24) >= options.alphaThreshold) {
                left=min(left,x); right=max(right,x)
                top=min(top,y); bottom=max(bottom,y)
            }
        }
        if (right < left || bottom < top || options.brightness == 0) return IntArray(COUNT)

        val bw=right-left+1
        val bh=bottom-top+1
        // Wide/flat and narrow/tall monsters use more of the circular matrix.
        // For near-square bodies, retreat into the fully-wired 9x9 center.
        val fits=listOf(11.0,10.5,10.0,9.5,9.0)
        var best=IntArray(COUNT)
        for (fit in fits) {
            val scale=min(fit/bw,fit/bh)
            val drawW=bw*scale
            val drawH=bh*scale
            val startX=(SIZE-drawW)/2.0
            val startY=(SIZE-drawH)/2.0
            val area=DoubleArray(COUNT)
            val light=DoubleArray(COUNT)
            for (y in top..bottom) for (x in left..right) {
                val rgba=src[y*sw+x]
                val alpha=(rgba ushr 24)/255.0
                if (alpha < options.alphaThreshold/255.0) continue
                val r=(rgba ushr 16) and 255
                val g=(rgba ushr 8) and 255
                val b=rgba and 255
                val lum=(0.2126*r+0.7152*g+0.0722*b)/255.0
                val x0=startX+(x-left)*scale
                val y0=startY+(y-top)*scale
                val x1=x0+scale
                val y1=y0+scale
                val fromX=max(0,floor(x0).toInt())
                val fromY=max(0,floor(y0).toInt())
                val toX=min(12,ceil(x1).toInt()-1)
                val toY=min(12,ceil(y1).toInt()-1)
                for (dy in fromY..toY) for (dx in fromX..toX) {
                    val overlapX=max(0.0,min(dx+1.0,x1)-max(dx.toDouble(),x0))
                    val overlapY=max(0.0,min(dy+1.0,y1)-max(dy.toDouble(),y0))
                    val weight=overlapX*overlapY*alpha
                    val pos=dy*SIZE+dx
                    area[pos]+=weight
                    light[pos]+=weight*lum
                }
            }
            var offMask=0.0
            var totalArea=0.0
            for (i in 0 until COUNT) {
                totalArea+=area[i]
                if (!GlyphProjector.isPhysical(i)) offMask+=area[i]
            }
            // An alpha halo can straddle an LED that doesn't physically exist.
            // A <=1% fringe exception avoids needless over-shrinking.
            if (offMask > max(0.001,totalArea*0.01) && fit>9.0) continue

            val output=IntArray(COUNT)
            for (i in output.indices) {
                if (!GlyphProjector.isPhysical(i) || area[i] < 0.015) continue
                val mean=light[i]/area[i]
                val corrected=((mean-0.5)*options.contrast+0.5).coerceIn(0.0,1.0)
                // Lift dark materials without erasing their differences.
                val tone=corrected.pow(options.gamma)
                // Fractional coverage must contribute to brightness; otherwise
                // an almost invisible horn becomes a solid white square.
                val coverage=area[i].coerceIn(0.0,1.0).pow(0.55)
                val normalized=(0.16+0.84*tone)*coverage
                output[i]=(normalized*options.brightness).roundToInt().coerceIn(0,255)
            }
            best=output
            break
        }
        return best
    }

    /** Legacy 1-bit data for old game saves / old GlyphLIFE APKs. */
    fun asBinary(levels: IntArray): String {
        require(levels.size==COUNT)
        return levels.mapIndexed { i, v ->
            if (GlyphProjector.isPhysical(i) && v >= 24) '1' else '0'
        }.joinToString("")
    }
}
