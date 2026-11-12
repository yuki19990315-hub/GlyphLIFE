package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Native 64x64 original pixel-art. There is no 13x13 art generation in this
 * class: Glyph pixels are a separate derivative produced by GlyphProjector.
 * Colors are 32-bit ARGB; 0 is transparent.
 */
class PixelSprite private constructor(val width: Int, val height: Int, private val data: IntArray) {
    init { require(width in 1..256 && height in 1..256 && data.size == width * height) }
    fun pixel(x: Int, y: Int): Int = if (x in 0 until width && y in 0 until height)
        data[y * width + x] else 0
    fun pixels(): IntArray = data.copyOf()
    fun opaqueCount(): Int = data.count { (it ushr 24) != 0 }
    fun colors(): Set<Int> = data.filter { (it ushr 24) != 0 }.toSet()

    /** 256x256 bestiary mode is a crisp 4x nearest-neighbour enlargement, not added detail. */
    fun nearestScaled(toWidth: Int, toHeight: Int): PixelSprite {
        require(toWidth in 1..1024 && toHeight in 1..1024)
        val out = IntArray(toWidth * toHeight)
        for (y in 0 until toHeight) for (x in 0 until toWidth) {
            out[y * toWidth + x] = pixel(x * width / toWidth, y * height / toHeight)
        }
        return PixelSprite(toWidth, toHeight, out)
    }

    companion object {
        fun fromArgb(width: Int, height: Int, data: IntArray): PixelSprite =
            PixelSprite(width, height, data.copyOf())
    }
}

enum class MonsterBodyType {
    BEAST, DRAGON, BIPED, GOLEM, SERPENT, AVIAN, INSECTOID, AQUATIC, MECHA, SPIRIT
}

/**
 * Stable species architecture for growth/fusion. This is procedural art;
 * it does NOT use or train on any protected franchise assets.
 */
object MonsterDesign {
    private val fire = intArrayOf(0xFF29172CL.toInt(),0xFF5D2934L.toInt(),0xFFB54432L.toInt(),0xFFFF8C42L.toInt(),0xFFFFD36AL.toInt())
    private val water = intArrayOf(0xFF142A42L.toInt(),0xFF205170L.toInt(),0xFF327CACL.toInt(),0xFF74C5D8L.toInt(),0xFFE1FAFFL.toInt())
    private val grass = intArrayOf(0xFF1D3227L.toInt(),0xFF34573BL.toInt(),0xFF4E8954L.toInt(),0xFFA3C76AL.toInt(),0xFFE5F4B0L.toInt())
    private val lightning = intArrayOf(0xFF25233CL.toInt(),0xFF42456EL.toInt(),0xFF8177AFL.toInt(),0xFFF0CB57L.toInt(),0xFFFFFFCBL.toInt())
    private val light = intArrayOf(0xFF424056L.toInt(),0xFF746B8EL.toInt(),0xFFB9A8BFL.toInt(),0xFFF5D8A0L.toInt(),0xFFFFFFE6L.toInt())
    private val dark = intArrayOf(0xFF19172FL.toInt(),0xFF33284CL.toInt(),0xFF66517CL.toInt(),0xFFA173B0L.toInt(),0xFFEC9AEBL.toInt())
    private val neutral = intArrayOf(0xFF28303BL.toInt(),0xFF455567L.toInt(),0xFF70838BL.toInt(),0xFFB9C6BDL.toInt(),0xFFF6E9CFL.toInt())
    fun palette(element: String): IntArray = when(element) {
        "火" -> fire; "水" -> water; "草" -> grass; "雷" -> lightning
        "光" -> light; "闇" -> dark; else -> neutral
    }.copyOf()

    fun classify(species: String): MonsterBodyType {
        val s = species.lowercase()
        return when {
            s.contains("竜") || s.contains("ドラゴン") -> MonsterBodyType.DRAGON
            s.contains("ギア") || s.contains("機甲") || s.contains("歯車") || s.contains("ロボ") -> MonsterBodyType.MECHA
            s.contains("モコ") || s.contains("獣") || s.contains("狼") -> MonsterBodyType.BEAST
            s.contains("ルーン") || s.contains("精霊") || s.contains("霊") -> MonsterBodyType.SPIRIT
            s.contains("鳥") || s.contains("翼") -> MonsterBodyType.AVIAN
            s.contains("蛇") -> MonsterBodyType.SERPENT
            s.contains("魚") || s.contains("アクア") -> MonsterBodyType.AQUATIC
            s.contains("虫") -> MonsterBodyType.INSECTOID
            s.contains("岩") || s.contains("守護者") -> MonsterBodyType.GOLEM
            else -> MonsterBodyType.entries[Math.floorMod(species.hashCode(), MonsterBodyType.entries.size)]
        }
    }

    fun bodyType(wild: WildMonster): MonsterBodyType =
        MonsterBodyType.entries.firstOrNull { it.name == wild.bodyType } ?: classify(wild.species)

    fun stage(wild: WildMonster): Int = wild.stage.coerceIn(1,3)

    /**
     * All geometry and decorative marks are generated at true 64x64 logical
     * resolution with a deliberately limited (<=8) palette.
     */
    fun sprite(wild: WildMonster): PixelSprite {
        val w=64; val h=64
        val data=IntArray(w*h)
        val p=palette(wild.element)
        val seed=(wild.species + ":" + wild.element + ":" + wild.lineage + ":" + wild.id).hashCode()
        val stage=stage(wild)
        val scale=if (wild.isBoss) 1.13 else if (stage==3) 1.10 else if (stage==1) 0.88 else 1.0

        fun set(x:Int,y:Int,color:Int) {
            if (x in 0 until w && y in 0 until h) data[y*w+x]=color
        }
        fun rect(x0:Int,y0:Int,x1:Int,y1:Int,c:Int) {
            for (y in max(0,y0)..min(63,y1)) for(x in max(0,x0)..min(63,x1)) set(x,y,c)
        }
        fun ellipse(cx:Int,cy:Int,rx:Int,ry:Int,c:Int) {
            if (rx<1 || ry<1) return
            for(y in max(0,cy-ry)..min(63,cy+ry)) for(x in max(0,cx-rx)..min(63,cx+rx)) {
                val dx=x-cx; val dy=y-cy
                if (dx.toLong()*dx*ry*ry + dy.toLong()*dy*rx*rx <= rx.toLong()*rx*ry*ry) set(x,y,c)
            }
        }
        fun line(x0:Int,y0:Int,x1:Int,y1:Int,c:Int,thick:Int=1) {
            var x=x0;var y=y0; val dx=abs(x1-x0);val dy=abs(y1-y0)
            val sx=if(x0<x1)1 else -1;val sy=if(y0<y1)1 else -1;var error=dx-dy
            while(true) {
                for (offsetY in -(thick/2)..(thick/2)) for(offsetX in -(thick/2)..(thick/2))
                    set(x+offsetX,y+offsetY,c)
                if(x==x1 && y==y1) break
                val e=2*error
                if(e> -dy){error-=dy;x+=sx}
                if(e<dx){error+=dx;y+=sy}
            }
        }
        fun ovalArmor(cx:Int,cy:Int,rx:Int,ry:Int) {
            ellipse(cx,cy,rx+2,ry+2,p[0])
            ellipse(cx,cy-1,rx,ry,p[1])
            ellipse(cx-1,cy-2,max(1,rx-2),max(1,ry-2),p[2])
            ellipse(cx-rx/3,cy-ry/2,max(1,rx/2),max(1,ry/3),p[3])
        }
        fun eye(cx:Int,cy:Int) {
            rect(cx-2,cy-1,cx+2,cy+1,p[0])
            rect(cx,cy-1,cx+1,cy,p[4])
        }
        val type=bodyType(wild)
        val torsoRx=(14*scale).roundToInt()
        val torsoRy=(13*scale).roundToInt()

        when(type) {
            MonsterBodyType.BEAST -> {
                for(x in listOf(18,26,37,45)) { line(x,42,x-2,55,p[0],5); line(x,42,x-2,54,p[2],3) }
                line(44,34,57,22,p[0],5);line(45,34,57,22,p[3],3)
                ovalArmor(32,35,19,12);ovalArmor(23,23,12,11)
                line(17,18,11,7,p[0],5);line(29,17,34,5,p[0],5)
                line(17,18,12,9,p[3],2);line(29,17,33,8,p[3],2);eye(19,23)
                ellipse(13,29,5,3,p[0]); ellipse(11,28,2,1,p[4])
            }
            MonsterBodyType.DRAGON -> {
                line(43,40,57,51,p[0],8); line(43,39,55,48,p[3],4)
                // Two broad wings with strong opposing silhouettes.
                for (n in 0..5) {
                    line(30,31,6+n,9+n*4,p[0],3)
                    line(35,31,57-n,9+n*4,p[0],3)
                    if(n<4) {
                        line(30,31,8+n,10+n*4,p[2],1)
                        line(35,31,55-n,10+n*4,p[3],1)
                    }
                }
                ovalArmor(34,37,torsoRx,torsoRy)
                ovalArmor(24,23,10,10)
                line(19,18,14,6,p[0],5);line(27,17,34,5,p[0],5)
                line(19,18,14,8,p[4],2);eye(19,23)
                line(27,42,24,56,p[0],6);line(39,44,43,57,p[0],6)
                line(23,55,18,57,p[3],3);line(43,55,48,58,p[3],3)
            }
            MonsterBodyType.BIPED -> {
                line(27,39,21,55,p[0],8);line(38,39,43,55,p[0],8)
                line(22,54,15,57,p[2],5);line(44,54,51,57,p[2],5)
                ovalArmor(32,34,torsoRx,torsoRy);ovalArmor(32,18,9,10)
                line(20,28,9,43,p[0],7);line(45,28,54,43,p[0],7)
                line(20,27,10,40,p[3],3);line(45,27,54,40,p[3],3)
                line(27,15,21,5,p[0],4);line(37,15,43,5,p[0],4)
                eye(27,18);eye(36,18)
            }
            MonsterBodyType.GOLEM -> {
                rect(20,41,29,56,p[0]);rect(35,41,44,56,p[0])
                rect(19,23,45,44,p[0]);rect(22,25,42,41,p[1])
                rect(24,27,41,38,p[2]);rect(29,29,35,35,p[4])
                rect(11,26,20,44,p[0]);rect(44,26,53,44,p[0])
                rect(14,28,18,41,p[3]);rect(46,28,50,41,p[3])
                rect(24,14,40,25,p[0]);rect(26,15,38,22,p[2]);eye(28,19);eye(36,19)
            }
            MonsterBodyType.SERPENT -> {
                for (n in 0..6) {
                    val xx= 22 + n*4
                    val yy= 44 + (if(n%2==0) 4 else -4)
                    ellipse(xx,yy,7,6,p[0]);ellipse(xx,yy-1,5,4,p[2])
                }
                ovalArmor(23,25,12,11);eye(18,24);eye(27,24)
                line(17,19,12,6,p[0],4);line(29,17,35,4,p[0],4)
                line(17,19,12,7,p[3],2)
            }
            MonsterBodyType.AVIAN -> {
                for(n in 0..8) {
                    line(29,31,6+n/2,7+n*4,p[0],2)
                    line(34,31,57-n/2,7+n*4,p[0],2)
                }
                ovalArmor(31,34,13,16);ovalArmor(31,19,9,10)
                line(28,20,20,24,p[4],4);eye(33,20)
                line(27,47,25,58,p[0],3);line(36,47,38,58,p[0],3)
                line(30,48,22,58,p[3],2)
            }
            MonsterBodyType.INSECTOID -> {
                for(n in 0..3) {
                    val y=25+n*7
                    line(23,y,9,y+9,p[0],3);line(41,y,55,y+9,p[0],3)
                }
                ovalArmor(32,39,14,15);ovalArmor(32,21,11,11)
                line(27,15,19,5,p[0],3);line(37,15,44,5,p[0],3)
                ellipse(26,21,3,4,p[4]);ellipse(38,21,3,4,p[4])
                for(n in 0..3) line(27+n*3,31,27+n*3,49,p[3],1)
            }
            MonsterBodyType.AQUATIC -> {
                // Scaled fish body, fins, tail and a face distinct from the previous oval.
                ovalArmor(30,33,19,13)
                for (n in 0..7) {
                    line(44,32,59,16+n*4,p[0],2)
                    if(n%2==0) line(47,32,57,18+n*4,p[3],1)
                }
                line(28,23,38,10,p[0],7);line(29,41,42,53,p[0],7)
                eye(20,29);line(11,39,16,42,p[4],2)
                for(n in 0..4) line(24+n*5,30,22+n*5,34,p[3],1)
            }
            MonsterBodyType.MECHA -> {
                line(25,42,24,56,p[0],8);line(39,42,41,56,p[0],8)
                rect(19,23,46,43,p[0]);rect(22,25,43,40,p[1])
                rect(25,28,40,38,p[2]);rect(30,29,35,35,p[4])
                rect(24,11,41,24,p[0]);rect(26,13,39,21,p[2])
                rect(27,17,31,19,p[4]);rect(35,17,39,19,p[4])
                line(18,28,8,39,p[0],7);line(47,28,57,39,p[0],7)
                line(18,28,8,37,p[3],3);line(47,28,57,37,p[3],3)
                line(29,12,26,5,p[0],3);line(38,12,41,5,p[0],3)
            }
            MonsterBodyType.SPIRIT -> {
                for(n in 0..5) {
                    val x=21+n*4
                    line(x,40,x+(if(n%2==0)-3 else 4),54+n%3,p[0],4)
                    line(x,41,x+(if(n%2==0)-2 else 3),51+n%3,p[2],2)
                }
                ovalArmor(32,29,torsoRx,17)
                line(25,17,19,6,p[0],4);line(39,17,46,6,p[0],4)
                eye(26,27);eye(38,27)
                ellipse(32,37,4,2,p[4])
            }
        }
        // Fusion visually inherits the secondary parent's silhouette accent.
        // The primary body architecture remains fixed, so offspring are readable.
        if(wild.lineage.isNotBlank()) {
            when(classify(wild.motifSecondary)) {
                MonsterBodyType.DRAGON, MonsterBodyType.AVIAN -> {
                    line(18,30,6,10,p[0],5);line(45,30,58,10,p[0],5)
                    line(18,30,9,13,p[4],2);line(45,30,55,13,p[4],2)
                }
                MonsterBodyType.MECHA, MonsterBodyType.GOLEM -> {
                    rect(13,30,21,37,p[0]);rect(15,32,19,35,p[4])
                    rect(44,30,51,37,p[0]);rect(46,32,49,35,p[4])
                }
                MonsterBodyType.AQUATIC, MonsterBodyType.SERPENT -> {
                    line(43,39,58,46,p[0],6);line(44,39,57,45,p[4],2)
                }
                else -> {
                    line(24,12,18,5,p[0],4);line(40,12,46,5,p[0],4)
                    line(24,12,19,7,p[4],2);line(40,12,45,7,p[4],2)
                }
            }
        }

        // Body motifs grow incrementally; facial identity and core silhouette remain.
        if(stage>=2 || wild.isBoss) {
            line(30,19,31,10,p[0],4);line(33,19,34,10,p[0],4)
            line(31,11,31,8,p[4],1);line(34,11,34,8,p[4],1)
        }
        if(stage>=3 || wild.isBoss) {
            line(18,33,9,23,p[0],4);line(46,33,55,23,p[0],4)
            line(18,33,9,24,p[3],2);line(46,33,55,24,p[3],2)
        }
        // Hand-placed highlights in multiple positions; max 5 base colors.
        val markX=18+Math.floorMod(seed,26)
        val markY=35+Math.floorMod(seed ushr 5,10)
        for(n in 0..2) if(data.getOrElse(markY*w+markX+n){0} != 0)
            set(markX+n,markY,p[4])
        return PixelSprite.fromArgb(w,h,data)
    }
}

/** Conversion contract: derive from native pixels only when displaying on Glyph. */
object GlyphProjector {
    private val left = intArrayOf(4,2,1,1,0,0,0,0,0,1,1,2,4)
    fun isPhysical(index:Int):Boolean {
        if(index !in 0..168)return false
        val y=index/13;val x=index%13
        return x in left[y]..(12-left[y])
    }
    fun project(sprite: PixelSprite): String {
        require(sprite.width>=13 && sprite.height>=13)
        val result=CharArray(169) { '0' }
        for(y in 0..12) for(x in 0..12) {
            val i=y*13+x
            if(!isPhysical(i))continue
            val x0=x*sprite.width/13;val x1=max(x0+1,(x+1)*sprite.width/13)
            val y0=y*sprite.height/13;val y1=max(y0+1,(y+1)*sprite.height/13)
            var coverage=0;var detail=0;var count=0
            for(sy in y0 until y1) for(sx in x0 until x1) {
                val c=sprite.pixel(sx,sy)
                if(c ushr 24 >= 96) {
                    coverage++
                    // High-contrast highlights and eyes remain visible after reduction.
                    val light=((c shr 16) and 255)*30 + ((c shr 8) and 255)*59 + (c and 255)*11
                    if(light>=16000)detail++
                }
                count++
            }
            if(coverage*100>=count*33 || (detail>=2 && coverage*100>=count*13))
                result[i]='1'
        }
        return String(result)
    }
}
