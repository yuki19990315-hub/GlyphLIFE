package dev.glyphlife.monsterquest.pixel

import kotlin.math.abs

/** A separate, reversible equipment badge drawn ON the 64x64 creature.
 * No imported artwork is overwritten. All pixels reuse existing colors so
 * transparent pixels + 15-color master palettes stay within the GBA standard.
 */
object BossRelicPixels {
    fun compose(base:PixelSprite,kind:String?):PixelSprite {
        if(kind !in setOf("moonfang","clockgear","starseal","abysscore",
                          "lifepearl","spiritclock"))return base
        if(base.width!=64 || base.height!=64)return base
        val palette=base.colors().sortedBy { color ->
            ((color ushr 16) and 255)*299+
            ((color ushr 8) and 255)*587+(color and 255)*114
        }
        if(palette.size<2)return base
        val dark=palette.first()
        val mid=palette[palette.size/2]
        val light=palette.last()
        val bounds=GbaMonsterArtStandard.bounds(base) ?: return base
        // Lower-body pendant with a one-pixel transparent safety rim.
        // Avoid the head/eyes and never crop tails at the frame edge.
        val cx=(bounds.right-5).coerceIn(13,53)
        val cy=(bounds.bottom-10).coerceIn(28,52)
        val out=base.pixels()
        fun draw(x:Int,y:Int,color:Int) {
            if(x in 1..62 && y in 1..62)out[y*64+x]=color
        }
        // A small dark-edged crest that sits like a fastened accessory,
        // not a disconnected image of a face or a circular crop.
        for(y in -5..5)for(x in -5..5)
            if(abs(x)+abs(y)<=7) draw(cx+x,cy+y,dark)
        for(y in -4..4)for(x in -4..4)
            if(abs(x)+abs(y)<=5) draw(cx+x,cy+y,mid)
        fun px(x:Int,y:Int,color:Int=light)=draw(cx+x,cy+y,color)
        when(kind) {
            "moonfang" -> {
                for(y in -3..2) {px(-2,y);px(2,y)}
                px(-1,3);px(1,3);px(0,2)
            }
            "clockgear" -> {
                for(a in -2..2){px(a,-3);px(a,3);px(-3,a);px(3,a)}
                px(-1,-1);px(1,-1);px(-1,1);px(1,1)
                px(0,0,dark)
            }
            "starseal" -> {
                for(i in -3..3){px(i,0);px(0,i)}
                px(-2,-2);px(2,-2);px(-2,2);px(2,2)
            }
            "abysscore" -> {
                for(i in -2..2){px(i,-2);px(i,2);px(-2,i);px(2,i)}
                px(0,0,dark);px(-1,0,dark);px(1,0,dark)
            }
            "lifepearl" -> {
                for(y in -2..2)for(x in -2..2)
                    if(x*x+y*y<=5)px(x,y)
                px(-1,-1,dark)
            }
            "spiritclock" -> {
                for(y in -3..3)for(x in -3..3) {
                    val d=x*x+y*y
                    if(d in 8..13)px(x,y)
                }
                px(0,-1);px(0,0);px(1,0)
            }
        }
        return PixelSprite.fromArgb(64,64,out)
    }
}
