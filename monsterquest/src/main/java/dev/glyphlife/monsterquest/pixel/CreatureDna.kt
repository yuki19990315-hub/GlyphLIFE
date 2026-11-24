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
    data class Genome(val genes: List<Int>, val variation: Int, val style:List<Int> = emptyList()) {
        init {
            require(genes.size == limits.size && genes.indices.all { genes[it] in 0..limits[it] })
            require(style.isEmpty() || (style.size==4 && style[0] in 0..9 && style.drop(1).all{it in 0..4}))
        }
        operator fun get(index: Int): Int = genes[index]
        fun encode(): String = if(style.isEmpty()) PREFIX + genes.joinToString("") + ":" + variation.toUInt().toString(16)
            else "dna2:" + genes.joinToString("") + ":" + style.joinToString("") + ":" + variation.toUInt().toString(16)
    }

    fun decode(seed: String): Genome? {
        val v2=seed.startsWith("dna2:")
        if(!v2 && !seed.startsWith(PREFIX))return null
        val fields=seed.substring(5).split(':')
        if(fields.size!=(if(v2)3 else 2) || fields[0].length!=limits.size)return null
        val g=fields[0].map { it.digitToIntOrNull() ?: return null }
        if(g.indices.any{g[it] !in 0..limits[it]})return null
        val style=if(v2)fields[1].map { it.digitToIntOrNull() ?: return null } else emptyList()
        if(v2 && (style.size!=4 || style[0] !in 0..9 || style.drop(1).any{it !in 0..4}))return null
        val v=fields.last().toUIntOrNull(16)?.toInt() ?: return null
        return Genome(g,v,style)
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
        val seed=(w.species+"|"+w.designSeed.ifBlank{w.id}).hashCode()
        return Genome(profile+listOf(hue,(hue+3)%7),seed,founderStyle(MonsterDesign.bodyType(w),seed))
    }

    private fun founderStyle(type:MonsterBodyType,seed:Int):List<Int> {
        val family=when(type) {
            MonsterBodyType.BEAST -> 0
            MonsterBodyType.DRAGON -> 1
            MonsterBodyType.BIPED -> 2
            MonsterBodyType.GOLEM -> 3
            MonsterBodyType.SERPENT -> 4
            MonsterBodyType.AVIAN -> 5
            MonsterBodyType.INSECTOID -> 6
            MonsterBodyType.AQUATIC -> 7
            MonsterBodyType.MECHA -> 8
            MonsterBodyType.SPIRIT -> 9
        }
        val head=listOf(0,1,2,3,1,4,3,4,3,2)[family]
        val material=listOf(0,1,2,3,1,0,1,1,4,2)[family]
        return listOf(family,head,material,Math.floorMod(seed,5))
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
        val sx=x.style.ifEmpty{founderStyle(bodyType(x),x.variation)}
        val sy=y.style.ifEmpty{founderStyle(bodyType(y),y.variation)}
        // The family is a categorical inherited topology, not an average.
        // Choose independently from the face, surface and appendage alleles.
        val family=if(rng.nextBoolean())sx[0] else sy[0]
        val styles=listOf(family)+ (1..3).map { i ->
            if(rng.nextInt(10)==0)rng.nextInt(5) else if(rng.nextBoolean())sx[i] else sy[i]
        }
        val legPairs=listOf(2,2,1,1,0,1,3,0,2,0)[family]
        val candidates=(0..3).map { n ->
            val genes=if(n==0)child else child.mapIndexed { i,v ->
                if(i<11 && rng.nextInt(4)==0)
                    (v+rng.nextInt(-1,2)).coerceIn(0,limits[i]) else v
            }
            Genome(genes.mapIndexed{i,v->if(i==3)legPairs else v},rng.nextInt(),styles)
        }
        val template=a.copy(stage=maxOf(a.stage,b.stage),armorParts=emptyMap())
        return candidates.maxByOrNull { CreatureQuality.inspect(sprite(template,it)).score }!!
    }

    fun bodyType(g: Genome): MonsterBodyType = if(g.style.isNotEmpty()) listOf(
        MonsterBodyType.BEAST,MonsterBodyType.DRAGON,MonsterBodyType.BIPED,MonsterBodyType.GOLEM,
        MonsterBodyType.SERPENT,MonsterBodyType.AVIAN,MonsterBodyType.INSECTOID,MonsterBodyType.AQUATIC,
        MonsterBodyType.MECHA,MonsterBodyType.SPIRIT)[g.style[0]] else when {
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
        val rx=(7+g[0]/2+growth-g[2]/4).coerceAtLeast(6)
        val ry=5+g[1]/2+growth+g[2]/4
        val hx=(cx+rx-2-g[2]/2+g[5]/3).coerceAtMost(45)
        val hy=(cy-ry/2-g[2]-g[5]/2).coerceAtLeast(18)
        return Rig(cx,cy,rx,ry,hx,hy,5+g[1]/4,2+g[6]/2)
    }

    /** Built-in armor sockets follow the generated anatomy, not old species coordinates. */
    fun anchor(w:WildMonster, slot:String,phase:Int=0,motion:Motion=Motion.REST):Pair<Int,Int>? {
        val g=decode(w.designSeed) ?: return null
        if(g.style.isNotEmpty())return CreatureArtV2.socket(g,w.stage,phase,motion,slot)
        val r=motionRig(g,w.stage,phase,motion)
        val beat=sin(Math.floorMod(phase,4)*PI/2)
        return when(slot) {
            "HEAD" -> r.hx to (r.hy-3)
            "CORE" -> r.cx to r.cy
            "TAIL" -> (r.cx-r.rx-2) to (r.cy+(beat*2).roundToInt())
            "SHOULDER" -> (r.cx+r.rx/2) to (r.cy-r.ry/3)
            "BACK" -> (r.cx-3) to (r.cy-r.ry+2)
            "WING" -> (r.cx-3) to (r.cy-r.ry+2+(beat*2).roundToInt())
            else -> null
        }
    }

    /** Local skinning pose for custom equipment, driven by the same phase
     * as the underlying articulated creature. */
    fun armorPose(w:WildMonster,slot:String,phase:Int,motion:Motion):ArmorArticulation.Pose {
        val g=decode(w.designSeed) ?: return ArmorArticulation.Pose()
        val step=Math.floorMod(phase,4)
        val beat=sin(step*PI/2)
        val strike=if(motion==Motion.ATTACK)listOf(0.0,-1.0,1.0,0.4)[step] else 0.0
        val wing=g[8]/9.0
        val tail=g[7]/9.0
        return when(slot) {
            "WING" -> ArmorArticulation.Pose(beat*(8+22*wing),beat*6*wing)
            "TAIL" -> ArmorArticulation.Pose(beat*(8+12*tail),beat*6*tail)
            "HEAD" -> ArmorArticulation.Pose(strike*12+beat*2)
            "SHOULDER" -> ArmorArticulation.Pose(strike*15+beat*6)
            "BACK" -> ArmorArticulation.Pose(beat*5)
            else -> ArmorArticulation.Pose(strike*4)
        }
    }

    /** Native integer pixel rasterization; body segments share attachment points. */
    enum class Motion { REST, WALK, ATTACK }
    data class CombatTraits(val hpBonus:Int,val attackBonus:Int,val skill:String,
        val power:Int,val locomotion:String)
    fun combat(g:Genome):CombatTraits {
        val family=g.style.firstOrNull()
        val skill=when {
            g[10]>=7 && g[1]>=6 -> "golem"
            family==7 -> "tide"
            family==5 || g[8]>=6 -> "gale"
            g[3]==3 -> "venom"
            g[6]>=7 && g[9]>=4 -> "flame"
            g[3]==0 && g[0]>=6 -> "tide"
            g[3]==0 -> "heal"
            else -> "claw"
        }
        return CombatTraits((g[1]+g[10])/3,(g[6]+g[9]+g[3])/6,skill,
            when(skill) {
                "golem","heal" -> 0
                "gale" -> g[8]/2
                "venom" -> g[3]+g[9]/3
                "flame" -> g[6]/2
                "tide" -> g[7]/2
                else -> g[9]/3+g[4]/3
            }.coerceIn(0,6),
            when {
                family==7 -> "遊泳"
                family==4 -> "蛇行"
                family==9 -> "浮遊"
                family==5 || g[8]>=6 -> "飛行"
                g[3]==0 && g[0]>=6 -> "蛇行"
                g[3]==0 -> "浮遊"
                else -> "歩行"
            })
    }

    /** Equal-length two-link inverse kinematics, with a reachable foot. */
    internal fun knee(x:Int,y:Int,fx:Int,fy:Int,length:Double,bend:Int):Pair<Int,Int> {
        val dx=(fx-x).toDouble();val dy=(fy-y).toDouble()
        val d=hypot(dx,dy).coerceAtLeast(0.001)
        val half=d/2
        val across=sqrt((length*length-half*half).coerceAtLeast(0.0))*bend
        return ((x+fx)/2.0-dy/d*across).roundToInt() to
            ((y+fy)/2.0+dx/d*across).roundToInt()
    }

    private fun motionRig(g:Genome,stage:Int,phase:Int,motion:Motion):Rig {
        val step=Math.floorMod(phase,4)
        val beat=sin(step*PI/2)
        val attack=motion==Motion.ATTACK
        val flying=g[8]>=6
        val shift=if(attack)listOf(0,-2,3,1)[step] else 0
        val hover=if(!attack && (flying||g[3]==0))(beat*2).roundToInt() else 0
        val base=rig(g,stage)
        return base.copy(cx=base.cx+shift,cy=base.cy+hover,
            hx=(base.hx+shift+(if(attack && step==2)2 else 0)).coerceAtMost(47),
            hy=base.hy+hover+(if(attack && step==1)1 else 0))
    }
    fun sprite(w:WildMonster,g:Genome,phase:Int=0,motion:Motion=Motion.REST):PixelSprite {
        if(g.style.isNotEmpty())return CreatureArtV2.render(g,w.stage,phase,motion)
        val step=Math.floorMod(phase,4)
        val beat=sin(step*PI/2)
        val attack=motion==Motion.ATTACK
        val flying=g[8]>=6
        val r=motionRig(g,w.stage,phase,motion)
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
            val ty=(cy+3-g[7]/2+g[2]/3+(beat*3).roundToInt()).coerceIn(10,52)
            val joint=(cx-rx-3).coerceAtLeast(tx+1)
            limb(cx-rx+3,cy,joint,cy+2,3,p[2])
            limb(joint,cy+2,tx,ty,2,p[6])
            if(g[7]>=6)facet(listOf(tx to ty,(tx+4) to (ty-4),(tx+5) to (ty+2)),p[6],p[7])
        }
        // Wing membrane with shared shoulder root.
        if(g[8]>1) {
            val rootX=cx-3; val rootY=cy-ry+3
            val wx=(rootX-8-g[8]).coerceAtLeast(5)
            val wy=(rootY-5-g[8]+(beat*5).roundToInt()).coerceAtLeast(5)
            facet(listOf(rootX to (rootY+6),wx to wy,(wx+8) to (wy+4),
                (rootX+5) to (wy+2),(rootX+3) to rootY),p[6],p[7])
            limb(rootX,rootY,wx,wy,1,p[7])
            limb(rootX,rootY,wx+8,wy+4,1,p[5])
            if(g[8]>=6)limb(rootX,rootY,rootX+5,wy+2,1,p[7])
        }
        val pairs=g[3]
        fun legs(far:Boolean) {
            for(i in 0 until pairs) {
                val x=if(pairs==1)cx+g[2]/3 else cx-rx+4+i*(2*rx-8)/(pairs-1)
                val y=cy+ry-3
                val stride=if(!attack)sin(step*PI/2+(i+(if(far)1 else 0))*PI) else 0.0
                val nominalY=(y+5+g[4]+(if(far)-2 else 1)).coerceAtMost(56)
                val footY=(nominalY-(max(0.0,stride)*3).roundToInt()
                    -(if(flying && step!=0)2 else 0)).coerceAtLeast(y+3)
                val footX=(x+(if(i%2==0)0 else 5)+(if(far)-2 else 1)
                    +(stride*3).roundToInt()).coerceIn(5,56)
                val span=hypot((footX-x).toDouble(),(footY-y).toDouble())
                val length=max(4.0,hypot(8.0,(nominalY-y).toDouble())/2+1.5)
                val (kneeX,kneeY)=knee(x,y,footX,footY,max(length,span/2+0.5),
                    if(i%2==0)1 else -1)
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
        if(g[2]>=6 && pairs>0) {
            val sx=cx+rx/2;val sy=cy-ry/2
            val fx=(sx+6+g[4]/3+(if(attack && step==2)3 else 0)).coerceAtMost(56)
            val fy=(sy+6+(beat*2).roundToInt()).coerceIn(12,53)
            val ex=(sx+fx)/2-2;val ey=(sy+fy)/2+3
            limb(sx,sy,ex,ey,3,p[1]);limb(ex,ey,fx,fy,3,p[2])
            facet(listOf((fx-3) to (fy-2),(fx+2) to (fy-3),(fx+4) to fy,
                (fx+1) to (fy+3),(fx-3) to (fy+2)),p[6],p[7])
        }
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
        if(g[10]>=4)for(i in 0 until 1+g[10]/3) {
            val x=cx-rx+4+i*(2*rx-7)/(1+g[10]/3)
            val y=cy-ry+2
            facet(listOf((x-3) to (y+3),x to (y-2-g[10]/4),
                (x+4) to y,(x+3) to (y+4)),p[6],p[7])
        }
        if(g[6]>=5) {
            limb(hx+hr/2,hy+3,nose-2,hy+3,1,p[1])
            put(nose-4,hy+4,p[4])
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
        return CreatureQuality.connected(PixelSprite.fromArgb(64,64,out))
    }

    /** Joint animation for generated masters; preserve imported PNGs and overlays. */
    fun motionFrame(master:PixelSprite,w:WildMonster,g:Genome,phase:Int,motion:Motion):PixelSprite {
        if(phase==0)return master
        val native=MonsterDesign.sprite(w)
        val exact=master.pixels().contentEquals(native.pixels())
        val fitted=master.pixels().contentEquals(
            GbaMonsterArtStandard.fitWithMargin(native,9).pixels())
        if((exact||fitted) && w.customArmorParts.isEmpty()) {
            val body=sprite(w,g,phase,motion)
            if(CreatureQuality.inspect(body).valid) {
                val frame=GbaMonsterArtStandard.canonical(MonsterArmor.composeAt(body,w) { slot ->
                    anchor(w,slot,phase,motion)
                })
                return if(fitted && !exact)GbaMonsterArtStandard.fitWithMargin(frame,9) else frame
            }
        }
        return pose(master,g,phase,motion==Motion.ATTACK)
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
