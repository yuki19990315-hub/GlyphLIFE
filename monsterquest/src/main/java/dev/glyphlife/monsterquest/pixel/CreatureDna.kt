package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import kotlin.math.*
import kotlin.random.Random

/**
 * Independent, versioned numeric anatomy. No external artwork or model.
 * DNA fits the existing 64-character persisted designSeed field.
 * Legacy authored monsters keep their art; only new fusions use this renderer.
 */
object CreatureDna {
    // length, depth, upright, legPairs, legLength, neck, muzzle, tail,
    // wings, horns, plates, baseHue, accentHue
    private const val PREFIX = "dna1:"
    private val limits = listOf(9,9,9,3,9,9,9,9,9,9,9,6,6)
    private val elements = listOf("火","水","草","雷","光","闇","無")
    data class Genome(val genes: List<Int>, val variation: Int) {
        init {
            require(genes.size == limits.size && genes.indices.all { genes[it] in 0..limits[it] })
        }
        operator fun get(index: Int): Int = genes[index]
        fun encode(): String = PREFIX + genes.joinToString("") + ":" + variation.toUInt().toString(16)
    }

    fun decode(seed: String): Genome? {
        if (!seed.startsWith(PREFIX)) return null
        val fields = seed.removePrefix(PREFIX).split(':')
        if (fields.size != 2 || fields[0].length != limits.size) return null
        val g = fields[0].map { it.digitToIntOrNull() ?: return null }
        if (g.indices.any { g[it] !in 0..limits[it] }) return null
        val v = fields[1].toUIntOrNull(16)?.toInt() ?: return null
        return Genome(g,v)
    }

    fun fromMonster(w: WildMonster): Genome {
        decode(w.designSeed)?.let { return it }
        val profile = when (MonsterDesign.bodyType(w)) {
            MonsterBodyType.BEAST -> listOf(5,5,2,2,5,3,5,6,0,2,2)
            MonsterBodyType.DRAGON -> listOf(7,5,4,2,4,6,7,8,8,6,5)
            MonsterBodyType.BIPED -> listOf(2,4,9,1,8,3,2,1,0,2,5)
            MonsterBodyType.GOLEM -> listOf(3,9,8,1,4,1,1,0,0,4,9)
            MonsterBodyType.SERPENT -> listOf(9,2,1,0,0,3,4,9,0,2,2)
            MonsterBodyType.AVIAN -> listOf(4,3,6,1,6,5,7,4,9,2,1)
            MonsterBodyType.INSECTOID -> listOf(6,3,2,3,6,1,2,3,4,7,7)
            MonsterBodyType.AQUATIC -> listOf(7,5,1,0,0,1,4,7,3,0,2)
            MonsterBodyType.MECHA -> listOf(5,7,6,2,6,2,2,3,2,5,9)
            MonsterBodyType.SPIRIT -> listOf(3,4,7,0,0,1,1,4,4,2,1)
        }
        val hue = elements.indexOf(w.element).let { if(it < 0) 6 else it }
        return Genome(profile + listOf(hue,(hue+3)%7),
            (w.species + "|" + w.designSeed.ifBlank { w.id }).hashCode())
    }

    /** Parent order is deliberately significant, matching the existing fusion UI. */
    fun cross(a: WildMonster, b: WildMonster, seed: String): Genome {
        val x = fromMonster(a)
        val y = fromMonster(b)
        val rng = Random((seed + "|" + x.encode() + "|" + y.encode()).hashCode())
        val child = limits.indices.map { i ->
            val inherited = if(i >= 11) {
                if(i == 11) x[11] else y[11]
            } else {
                // Weighted interpolation, with bounded occasional mutation.
                val t = rng.nextDouble(0.25,0.75)
                (x[i]*(1-t)+y[i]*t).roundToInt() +
                    if(rng.nextInt(5)==0) rng.nextInt(-2,3) else 0
            }
            inherited.coerceIn(0,limits[i])
        }
        return Genome(child,rng.nextInt())
    }

    fun bodyType(g: Genome): MonsterBodyType = when {
        g[3] == 0 && g[0] >= 6 -> MonsterBodyType.SERPENT
        g[3] == 0 -> MonsterBodyType.SPIRIT
        g[3] == 3 -> MonsterBodyType.INSECTOID
        g[8] >= 6 -> if(g[0]>=5) MonsterBodyType.DRAGON else MonsterBodyType.AVIAN
        g[2] >= 7 -> if(g[10]>=7) MonsterBodyType.GOLEM else MonsterBodyType.BIPED
        else -> MonsterBodyType.BEAST
    }

    private data class Rig(val cx:Int,val cy:Int,val rx:Int,val ry:Int,
                           val hx:Int,val hy:Int,val hr:Int,val muzzle:Int)
    private fun rig(g:Genome,stage:Int):Rig {
        val growth=stage.coerceIn(1,3)-1
        val cx=31
        val cy=35-g[2]/3
        val rx=8+g[0]/2+growth
        val ry=5+g[1]/2+growth
        val hx=(cx+rx-2-g[2]/2+g[5]/3).coerceAtMost(45)
        val hy=(cy-ry/2-g[2]-g[5]/2).coerceAtLeast(18)
        return Rig(cx,cy,rx,ry,hx,hy,5+g[1]/4,2+g[6]/2)
    }

    /** Built-in armor sockets follow the generated anatomy, not old species coordinates. */
    fun anchor(w:WildMonster, slot:String):Pair<Int,Int>? {
        val g=decode(w.designSeed) ?: return null
        val r=rig(g,w.stage)
        return when(slot) {
            "HEAD" -> r.hx to (r.hy-3)
            "CORE" -> r.cx to r.cy
            "TAIL" -> (r.cx-r.rx-2) to r.cy
            "SHOULDER" -> (r.cx+r.rx/2) to (r.cy-r.ry/3)
            "BACK","WING" -> (r.cx-3) to (r.cy-r.ry+2)
            else -> null
        }
    }

    /** Native integer pixel rasterization; body segments share attachment points. */
    fun sprite(w:WildMonster,g:Genome):PixelSprite {
        val r=rig(g,w.stage)
        val a=MonsterDesign.palette(elements[g[11]])
        val b=MonsterDesign.palette(elements[g[12]])
        val p=intArrayOf(a[0],a[1],a[2],a[3],a[4],b[1],b[2],b[3],b[4])
        val out=IntArray(4096)
        fun put(x:Int,y:Int,c:Int) {
            if(x in 2..61 && y in 2..61) out[y*64+x]=c
        }
        fun disk(x:Int,y:Int,radius:Int,c:Int) {
            for(dy in -radius..radius)for(dx in -radius..radius)
                if(dx*dx+dy*dy<=radius*radius)put(x+dx,y+dy,c)
        }
        fun limb(x:Int,y:Int,ex:Int,ey:Int,radius:Int,c:Int) {
            val steps=max(abs(ex-x),abs(ey-y)).coerceAtLeast(1)
            for(i in 0..steps)disk(x+(ex-x)*i/steps,y+(ey-y)*i/steps,radius,p[0])
            for(i in 0..steps)disk(x+(ex-x)*i/steps,y+(ey-y)*i/steps,(radius-1).coerceAtLeast(0),c)
        }
        fun polygon(points:List<Pair<Int,Int>>,color:Int) {
            val xmin=points.minOf{it.first}; val xmax=points.maxOf{it.first}
            val ymin=points.minOf{it.second}; val ymax=points.maxOf{it.second}
            for(y in ymin..ymax)for(x in xmin..xmax) {
                var inside=false
                var j=points.lastIndex
                for(i in points.indices) {
                    val (xi,yi)=points[i]; val (xj,yj)=points[j]
                    if((yi>y)!=(yj>y) &&
                        x < (xj-xi).toDouble()*(y-yi)/(yj-yi)+xi)inside=!inside
                    j=i
                }
                if(inside)put(x,y,color)
            }
        }
        fun facet(points:List<Pair<Int,Int>>,color:Int,light:Int) {
            polygon(points,p[0])
            val mx=points.sumOf{it.first}/points.size
            val my=points.sumOf{it.second}/points.size
            val inset=points.map{ (x,y)-> (mx+(x-mx)*4/5) to (my+(y-my)*4/5) }
            polygon(inset,color)
            val top=inset.filter{it.second<=my}
            if(top.size>=2)polygon(top+listOf(mx to my),light)
        }
        val (cx,cy,rx,ry,hx,hy,hr,muzzle)=r
        // Tapered tail, attached to rear torso. Extreme values stay inside safe margins.
        if(g[7]>0) {
            val tx=(cx-rx-3-g[7]).coerceAtLeast(6)
            val ty=(cy+3-g[7]/2+g[2]/3).coerceIn(10,52)
            val joint=(cx-rx-3).coerceAtLeast(tx+1)
            limb(cx-rx+3,cy,joint,cy+2,3,p[2])
            limb(joint,cy+2,tx,ty,2,p[6])
            if(g[7]>=6)facet(listOf(tx to ty,(tx+4) to (ty-4),(tx+5) to (ty+2)),p[6],p[7])
        }
        // Wing membrane with shared shoulder root.
        if(g[8]>1) {
            val rootX=cx-3; val rootY=cy-ry+3
            val wx=(rootX-8-g[8]).coerceAtLeast(5)
            val wy=(rootY-5-g[8]).coerceAtLeast(5)
            facet(listOf(rootX to (rootY+6),wx to wy,(wx+8) to (wy+4),
                (rootX+5) to (wy+2),(rootX+3) to rootY),p[6],p[7])
            limb(rootX,rootY,wx,wy,1,p[7])
        }
        val pairs=g[3]
        fun legs(far:Boolean) {
            for(i in 0 until pairs) {
                val x=if(pairs==1)cx+g[2]/3 else cx-rx+4+i*(2*rx-8)/(pairs-1)
                val y=cy+ry-3
                val footY=(y+5+g[4]+if(far)-2 else 1).coerceAtMost(57)
                val kneeX=x+(if(i%2==0)-3 else 3)+if(far)-2 else 1
                val kneeY=(y+footY)/2
                val footX=(kneeX+3).coerceIn(5,57)
                val width=if(g[1]>=7)4 else 2+g[1]/5
                limb(x,y,kneeX,kneeY,width,if(far)p[1] else p[2])
                limb(kneeX,kneeY,footX,footY,width-1,if(far)p[1] else p[2])
                limb(footX-1,footY,footX+3,footY,2,if(far)p[1] else p[3])
            }
        }
        legs(true)
        facet(listOf((cx-rx) to (cy-ry/2),(cx-rx+4) to (cy-ry),
            (cx+rx-4) to (cy-ry),(cx+rx) to cy,
            (cx+rx-5) to (cy+ry),(cx-rx+3) to (cy+ry),
            (cx-rx-2) to cy),p[2],p[3])
        legs(false)
        // Neck and distinct faceted skull, with muzzle/eye positions derived from DNA.
        limb(cx+rx/2,cy-2,hx,hy,4+g[1]/4,p[2])
        val nose=(hx+hr+muzzle).coerceAtMost(58)
        facet(listOf((hx-hr) to (hy-2),(hx-hr+3) to (hy-hr),
            (hx+hr-2) to (hy-hr),(hx+hr) to (hy-3),
            nose to hy,(nose-1) to (hy+4),(hx-2) to (hy+hr),
            (hx-hr) to (hy+2)),p[2],p[3])
        if(g[9]>0) {
            val tipY=(hy-hr-2-g[9]/2).coerceAtLeast(4)
            facet(listOf((hx-hr+1) to (hy-hr+2),(hx-hr-3) to tipY,
                (hx+1) to (hy-hr+1)),p[6],p[8])
            if(g[9]>=5)facet(listOf(hx to (hy-hr+2),(hx+4) to tipY,
                (hx+5) to (hy-hr+2)),p[6],p[8])
        }
        disk(hx+hr/2,hy-1,2,p[0])
        put(hx+hr/2,hy-1,p[8]);put(hx+hr/2+1,hy-2,p[4])
        // Contiguous small plate motifs, not random disconnected noise.
        val count=1+g[10]/2
        for(i in 0 until count) {
            val x=cx-rx+5+i*(2*rx-10)/count
            val y=cy-ry/2+Math.floorMod(g.variation+i*3,4)
            for(dy in 0..2)for(dx in -1..1)
                if(out[y*64+x+dx+dy*64]!=0)put(x+dx,y+dy,if(dy==0)p[7] else p[5])
        }
        return PixelSprite.fromArgb(64,64,out)
    }

    /** Safe per-row deformation of the resolved master, including custom overlays.
     * Low bodies undulate, winged/legless bodies hover, upright bodies lean.
     * Not full skeletal IK; no source pixels are lost at the canvas edges.
     */
    fun pose(master:PixelSprite,g:Genome,phase:Int,attack:Boolean):PixelSprite {
        require(master.width==64 && master.height==64)
        if(phase==0)return master
        val bounds=GbaMonsterArtStandard.bounds(master) ?: return master
        val out=IntArray(4096)
        val direction=if(phase%2==0)1 else -1
        val hover=g[3]==0 || g[8]>=6
        val dy=(if(attack)-1 else if(hover)direction else 0)
            .coerceIn(-bounds.top,63-bounds.bottom)
        for(y in bounds.top..bounds.bottom) {
            val fraction=(y-bounds.top).toDouble()/bounds.height
            val shift=when {
                attack -> (if(phase==1)-2 else 3)+(if(fraction<0.5)1 else 0)
                g[3]==0 && g[0]>=6 -> (sin(fraction*PI*2+phase)*2).roundToInt()
                else -> (direction*(if(fraction>0.65)2 else if(fraction<0.4)1 else 0))
            }.coerceIn(-bounds.left,63-bounds.right)
            for(x in bounds.left..bounds.right) {
                val c=master.pixel(x,y)
                if(c!=0)out[(y+dy)*64+x+shift]=c
            }
        }
        return GbaMonsterArtStandard.fitInside(PixelSprite.fromArgb(64,64,out))
    }
}
