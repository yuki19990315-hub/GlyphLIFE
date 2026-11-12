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
    private fun mix(a:Int,b:Int,aWeight:Int=1,bWeight:Int=1):Int {
        val total=aWeight+bWeight
        fun component(shift:Int) =
            ((((a ushr shift) and 255)*aWeight +
                ((b ushr shift) and 255)*bWeight)/total)
        return (0xff shl 24) or (component(16) shl 16) or
            (component(8) shl 8) or component(0)
    }

    /** Five hue anchors plus seven derived material/lighting shades = up to 12 colors. */
    fun palette(element: String): IntArray {
        val p=when(element) {
            "火" -> fire; "水" -> water; "草" -> grass; "雷" -> lightning
            "光" -> light; "闇" -> dark; else -> neutral
        }
        return p + intArrayOf(
            mix(p[0],p[1]),mix(p[1],p[2]),mix(p[2],p[3]),
            mix(p[3],p[4]),mix(p[1],p[3]),mix(p[0],p[2]),
            mix(p[2],p[4])
        )
    }

    fun classify(species: String): MonsterBodyType {
        val s = species.lowercase()
        return when {
            s == "ライゼル" -> MonsterBodyType.BEAST
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
        FeaturedCompanionArt.find(wild)?.let { return MonsterArmor.compose(it, wild) }
        FeaturedPixelArt.find(wild)?.let { return MonsterArmor.compose(it, wild) }
        val w=64; val h=64
        val data=IntArray(w*h)
        val p=palette(wild.element)
        val seed=(wild.species + ":" + wild.element + ":" + wild.lineage + ":" +
            wild.designSeed.ifBlank { wild.id }).hashCode()
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
        fun triangle(ax:Int,ay:Int,bx:Int,by:Int,cx:Int,cy:Int,c:Int) {
            fun edge(x1:Int,y1:Int,x2:Int,y2:Int,x:Int,y:Int) =
                (x-x1)*(y2-y1)-(y-y1)*(x2-x1)
            val xMin=max(0,min(ax,min(bx,cx)))
            val xMax=min(63,max(ax,max(bx,cx)))
            val yMin=max(0,min(ay,min(by,cy)))
            val yMax=min(63,max(ay,max(by,cy)))
            for(y in yMin..yMax)for(x in xMin..xMax) {
                val a=edge(ax,ay,bx,by,x,y)
                val b=edge(bx,by,cx,cy,x,y)
                val cc=edge(cx,cy,ax,ay,x,y)
                if((a>=0&&b>=0&&cc>=0)||(a<=0&&b<=0&&cc<=0))set(x,y,c)
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
                // Solid membrane wings (not dotted single-pixel diagonals).
                triangle(29,33,8,5,12,43,p[0])
                triangle(29,31,12,13,15,38,p[1])
                triangle(29,31,17,17,19,35,p[2])
                triangle(35,33,56,5,52,43,p[0])
                triangle(35,31,52,13,49,38,p[1])
                triangle(35,31,47,17,45,35,p[2])
                for(n in 0..3) {
                    line(29,32,10+n*3,11+n*7,p[5],1)
                    line(35,32,54-n*3,11+n*7,p[6],1)
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
                triangle(29,33,5,8,10,48,p[0])
                triangle(29,32,9,14,14,43,p[1])
                triangle(29,32,13,19,17,41,p[2])
                triangle(35,33,59,8,54,48,p[0])
                triangle(35,32,55,14,50,43,p[1])
                triangle(35,32,51,19,47,41,p[2])
                for(n in 0..3){
                    line(29,32,8+n,14+n*9,p[6],1)
                    line(35,32,56-n,14+n*9,p[6],1)
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
        // Elemental shape language matters as much as palette.
        // Draw icons on the periphery so facial anatomy stays recognizable.
        when(wild.element) {
            "火" -> {
                triangle(48,41,58,17,55,41,p[3])
                triangle(52,31,61,10,59,34,p[4])
                line(48,41,56,27,p[7],2)
            }
            "水" -> {
                triangle(45,38,58,28,56,45,p[3])
                ellipse(54,16,3,4,p[8])
                ellipse(58,7,2,3,p[7])
            }
            "草" -> {
                triangle(15,27,7,13,22,23,p[3])
                triangle(49,28,57,13,42,23,p[7])
                line(15,27,9,15,p[4],1)
            }
            "雷" -> {
                line(45,16,53,11,p[3],3)
                line(53,11,48,23,p[4],3)
                line(48,23,58,18,p[3],3)
            }
            "光" -> {
                line(25,8,30,5,p[8],2)
                line(30,5,35,5,p[8],2)
                line(35,5,40,8,p[8],2)
                ellipse(54,14,3,3,p[4])
            }
            "闇" -> {
                triangle(13,22,8,7,22,18,p[0])
                triangle(49,22,56,7,42,18,p[3])
                ellipse(54,42,3,3,p[4])
            }
            else -> {
                line(13,40,8,31,p[6],3)
                line(50,40,55,31,p[6],3)
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

        // Species-specific anatomy. Earlier versions shared nearly identical
        // silhouettes within each body class; these variations affect the actual
        // outline and not just random interior speckles. The seed excludes stage
        // so evolution preserves the creature's identifying features.
        val morphology = Math.floorMod(seed ushr 3, 6)
        val crest = Math.floorMod(seed ushr 12, 3)
        when (morphology) {
            0 -> { // Forward-swept antlers, reinforced roots.
                line(23,17,10,8,p[0],5); line(40,17,53,8,p[0],5)
                line(23,16,12,9,p[4],2); line(40,16,51,9,p[4],2)
                line(13,10,8,3,p[0],3); line(50,10,55,3,p[0],3)
            }
            1 -> { // Jagged back plates with separated gaps.
                for(n in 0..3) {
                    val x=20+n*8
                    triangle(x-4,27,x,10-(n%2)*4,x+4,27,p[0])
                    triangle(x-2,25,x,15-(n%2)*4,x+2,25,p[3])
                }
            }
            2 -> { // Heavy plated shoulders and a luminous central seam.
                triangle(19,29,3,15,13,44,p[0])
                triangle(45,29,61,15,51,44,p[0])
                triangle(17,30,7,20,14,39,p[2])
                triangle(47,30,57,20,50,39,p[2])
                line(32,24,32,41,p[4],2)
            }
            3 -> { // Twin blade tails/fins: distinct wide outline.
                line(43,41,59,50,p[0],7)
                line(43,42,57,49,p[3],3)
                triangle(55,48,62,38,60,57,p[0])
                triangle(52,46,60,42,58,53,p[4])
            }
            4 -> { // High curved horns and lower cheek spikes.
                line(23,18,14,6,p[0],6)
                line(14,6,20,2,p[0],4)
                line(40,18,49,6,p[0],6)
                line(49,6,43,2,p[0],4)
                line(18,31,7,34,p[0],4)
                line(46,31,57,34,p[0],4)
            }
            else -> { // Asymmetric crown and one long curved dorsal blade.
                triangle(25,19,17,2,33,14,p[0])
                triangle(25,17,20,6,30,13,p[3])
                line(43,33,57,17,p[0],6)
                line(57,17,60,7,p[0],4)
                line(43,33,56,19,p[4],2)
            }
        }
        when(crest) {
            0 -> { // Split forehead crest.
                line(29,17,26,10,p[0],3); line(36,17,39,10,p[0],3)
                line(29,16,27,12,p[4],1)
            }
            1 -> { // Faceted forehead crystal.
                triangle(29,15,32,7,36,15,p[0])
                triangle(31,14,32,9,34,14,p[4])
            }
            else -> { // Lateral brow visor.
                line(24,17,40,17,p[0],4)
                line(27,16,37,16,p[4],2)
            }
        }

        // Stage 2: distinct crest and a bright protective shoulder crest.
        if(stage>=2 || wild.isBoss) {
            line(29,18,23,5,p[0],5);line(36,18,43,5,p[0],5)
            line(29,17,25,7,p[4],2);line(36,17,41,7,p[4],2)
            triangle(19,31,7,21,18,40,p[0])
            triangle(45,31,57,21,46,40,p[0])
            line(19,31,10,24,p[8],2);line(45,31,54,24,p[8],2)
        }
        // Stage 3: more substantial shoulders, wings and a luminous crest.
        if(stage>=3 || wild.isBoss) {
            triangle(18,36,5,17,16,44,p[0])
            triangle(46,36,59,17,48,44,p[0])
            triangle(18,35,9,21,16,41,p[3])
            triangle(46,35,55,21,48,41,p[3])
            line(29,12,32,1,p[0],4);line(35,12,32,1,p[0],4)
            line(32,2,32,9,p[4],2)
            if(type==MonsterBodyType.MECHA || type==MonsterBodyType.GOLEM) {
                rect(6,23,17,36,p[0]);rect(8,25,15,33,p[2])
                rect(47,23,58,36,p[0]);rect(49,25,56,33,p[2])
                rect(10,29,13,31,p[4]);rect(51,29,54,31,p[4])
            }
        }
        // Atelier styles have one stable secondary silhouette hook outside
        // the main body. The previous random morphology sometimes became fully
        // hidden behind a dragon/avian wing, producing identical silhouettes.
        // An 8-way tip position × 4-way height gives selectable outlines,
        // while the underlying 10 species architectures stay unchanged.
        val atelierIndex = wild.designSeed.takeIf { it.startsWith("atelier-") }
            ?.removePrefix("atelier-")?.toIntOrNull()?.takeIf { it in 0..63 }
        if (atelierIndex != null) {
            val topX = 2 + atelierIndex % 8
            val topY = 1 + (atelierIndex / 8) % 4
            val (rootX, rootY) = when (type) {
                MonsterBodyType.AQUATIC, MonsterBodyType.SERPENT -> 23 to 29
                MonsterBodyType.AVIAN, MonsterBodyType.DRAGON -> 21 to 32
                MonsterBodyType.MECHA, MonsterBodyType.GOLEM -> 22 to 26
                else -> 20 to 28
            }
            // A single connected, tapered horn/antenna/fin rather than random
            // disconnected sparkles; keep all tip differences in empty sky.
            line(rootX, rootY, topX + 4, topY + 8, p[0], 3)
            triangle(topX, topY, topX + 4, topY + 8,
                topX + 6, topY + 9, p[0])
            line(topX + 3, topY + 7, topX + 1, topY + 2, p[4], 1)
        }

        // Palette clustering: varied midtones and light patches in 3px tiles,
        // rather than smooth gradients or noisy per-pixel speckles.
        for(y in 0..63)for(x in 0..63) {
            val i=y*64+x
            val old=data[i]
            val band=Math.floorMod(x/3 + y/4 + (seed and 7),13)
            when {
                old==p[1] && band==2 -> data[i]=p[5]
                old==p[1] && band==3 -> data[i]=p[6]
                old==p[2] && band in 5..6 -> data[i]=p[7]
                old==p[2] && band==8 -> data[i]=p[10]
                old==p[3] && band in 1..2 -> data[i]=p[8]
                old==p[3] && band==10 -> data[i]=p[9]
            }
        }
        val markX=18+Math.floorMod(seed,26)
        val markY=35+Math.floorMod(seed ushr 5,10)
        for(n in 0..2) if(data.getOrElse(markY*w+markX+n){0} != 0)
            set(markX+n,markY,p[4])
        return MonsterArmor.compose(PixelSprite.fromArgb(w,h,data), wild)
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
    /** Prefer a distinct readable glyph insignia when an authored family supplies one.
     * Other monsters keep the normal silhouette projection. This cannot be used
     * in place of the full-resolution in-game picture.
     */
    fun projectFor(monster: WildMonster, sprite: PixelSprite, pose: Int = 0): String =
        FeaturedGlyphIcon.find(monster, pose) ?: project(sprite)

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
