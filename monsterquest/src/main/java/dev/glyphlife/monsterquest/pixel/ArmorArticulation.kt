package dev.glyphlife.monsterquest.pixel

import kotlin.math.*

/** Two-dimensional skinning for an individual transparent pixel-art part.
 * Rigid plate rotation plus an optional flexible distal bend. The proximal
 * half stays anchored; the distal half bends progressively. No interpolation
 * of ARGB colors and no edits to the original asset.
 */
object ArmorArticulation {
    data class Pose(val degrees:Double=0.0,val bend:Double=0.0) {
        init {
            require(degrees.isFinite() && degrees in -35.0..35.0)
            require(bend.isFinite() && bend in -8.0..8.0)
        }
    }

    fun deform(part:PixelSprite,pose:Pose):PixelSprite {
        require(part.width==64 && part.height==64)
        if(pose.degrees==0.0 && pose.bend==0.0)return part
        val b=GbaMonsterArtStandard.bounds(part) ?: return part
        val cx=(b.left+b.right)/2.0
        val cy=(b.top+b.bottom)/2.0
        val half=max(1.0,b.width/2.0)
        val theta=pose.degrees*PI/180
        val c=cos(theta);val s=sin(theta)
        // Flexible parts extend toward the left of the right-facing body.
        fun flex(x:Double):Double {
            val weight=((cx-x)/half).coerceIn(0.0,1.0)
            return pose.bend*weight*weight
        }
        // Determine a safe output translation before rasterization; do not
        // clip a large rotated wing at the 64px edge.
        var minX=Double.POSITIVE_INFINITY;var maxX=Double.NEGATIVE_INFINITY
        var minY=Double.POSITIVE_INFINITY;var maxY=Double.NEGATIVE_INFINITY
        for(x in b.left..b.right)for(y in listOf(b.top,b.bottom)) {
            val lx=x-cx;val ly=y-cy+flex(x.toDouble())
            val tx=cx+c*lx-s*ly;val ty=cy+s*lx+c*ly
            minX=min(minX,tx);maxX=max(maxX,tx)
            minY=min(minY,ty);maxY=max(maxY,ty)
        }
        val scale=minOf(1.0,60.0/(maxX-minX+1),60.0/(maxY-minY+1))
        val l=cx+(minX-cx)*scale;val r=cx+(maxX-cx)*scale
        val t=cy+(minY-cy)*scale;val bot=cy+(maxY-cy)*scale
        val dx=when { l<2 -> 2-l; r>61 -> 61-r; else -> 0.0 }
        val dy=when { t<2 -> 2-t; bot>61 -> 61-bot; else -> 0.0 }
        val out=IntArray(4096)
        // Inverse map: one nearest-neighbour sample per target pixel.
        for(y in 1..62)for(x in 1..62) {
            val tx=(x-cx-dx)/scale;val ty=(y-cy-dy)/scale
            val sx=cx+c*tx+s*ty
            val sy=cy-s*tx+c*ty-flex(sx)
            val ix=sx.roundToInt();val iy=sy.roundToInt()
            if(ix in b.left..b.right && iy in b.top..b.bottom)
                out[y*64+x]=part.pixel(ix,iy)
        }
        val warped=PixelSprite.fromArgb(64,64,out)
        return if(warped.opaqueCount()==0)part else warped
    }
}
