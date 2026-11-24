package dev.glyphlife.monsterquest.pixel

import kotlin.math.*

/** Original geometry library. Topology and feature alleles are inherited
 * independently; continuous genes modulate proportions and motion. */
object CreatureArtV2 {
    private data class P(val x:Int,val y:Int) {
        operator fun plus(b:P)=P(x+b.x,y+b.y)
        fun move(dx:Int,dy:Int)=P(x+dx,y+dy)
    }
    private data class Rig(val family:Int,val c:P,val h:P,val rx:Int,val ry:Int,
        val shoulder:P,val hip:P,val tail:P,val wing:P)
    private val elements=listOf("火","水","草","雷","光","闇","無")
    private fun rig(g:CreatureDna.Genome,stage:Int,phase:Int,motion:CreatureDna.Motion):Rig {
        val family=g.style[0];val growth=stage.coerceIn(1,3)-1
        val step=Math.floorMod(phase,4)
        val beat=sin(step*PI/2)
        val dx=if(motion==CreatureDna.Motion.ATTACK)listOf(0,-2,3,1)[step] else 0
        val dy=if(family in listOf(5,7,9) || g[8]>=7)(beat*2).roundToInt() else 0
        val sizes=listOf(14 to 8,12 to 9,9 to 14,12 to 14,9 to 8,
            11 to 9,15 to 9,14 to 10,13 to 11,10 to 13)
        val (rw,rh)=sizes[family]
        val rx=(rw+g[0]/4-1+growth).coerceIn(7,17)
        val ry=(rh+g[1]/4-1+growth).coerceIn(6,17)
        val centers=listOf(P(30,35),P(29,34),P(31,32),P(31,32),P(28,39),
            P(30,34),P(29,34),P(30,34),P(29,32),P(30,34))
        val heads=listOf(P(45,27),P(43,20),P(34,16),P(33,17),P(43,20),
            P(42,23),P(44,29),P(42,31),P(36,17),P(34,18))
        val c=centers[family].move(dx,dy)
        val rawHead=heads[family].move(dx+(if(motion==CreatureDna.Motion.ATTACK && step==2)2 else 0),
            dy-g[5]/4)
        val radius=(if(family in listOf(2,3,8,9))8 else 6)+g[1]/5
        val muzzle=(3+g[6]/2).coerceAtMost(7)
        // Reserve actual space for the longest inherited skull/beak and horns,
        // rather than silently truncating them at the raster boundary.
        val h=P(rawHead.x.coerceIn(radius+10,60-radius-muzzle-1),
            rawHead.y.coerceIn(radius+10,47-radius))
        val upright=family in listOf(2,3,8,9)
        return Rig(family,c,h,rx,ry,c.move(if(upright)rx/2 else rx-4,-ry/3),
            c.move(-rx+5,ry/2),c.move(-rx+3,2),c.move(-4,-ry/2))
    }
    fun socket(g:CreatureDna.Genome,stage:Int,phase:Int,motion:CreatureDna.Motion,slot:String):Pair<Int,Int>? {
        val r=rig(g,stage,phase,motion)
        val p=when(slot) {
            "HEAD"->r.h.move(0,-3)
            "CORE"->r.c
            "TAIL"->r.tail
            "SHOULDER"->r.shoulder
            "BACK"->r.c.move(-3,-r.ry/2)
            "WING"->r.wing
            else->return null
        }
        return p.x to p.y
    }

    private class Painter(val g:CreatureDna.Genome) {
        val out=IntArray(4096)
        val main:IntArray=MonsterDesign.palette(elements[g[11]])
        val accent:IntArray=MonsterDesign.palette(elements[g[12]])
        // Keep the cohesive primary ramp, and use a small accent ramp for
        // anatomical details. A subdued shared outline ties materials together.
        private fun tint(a:Int,b:Int,percent:Int):Int {
            fun c(shift:Int)=(((a ushr shift) and 255)*(100-percent)+
                ((b ushr shift) and 255)*percent)/100
            return 0xff000000.toInt() or (c(16) shl 16) or (c(8) shl 8) or c(0)
        }
        // The light source must not turn purple skin into a yellow stripe.
        // Preserve primary hue for body highlights; bright accent colors
        // remain reserved for eyes, armor edges and small motifs.
        val palette:IntArray=intArrayOf(main[0],main[1],main[2],
            tint(main[2],0xfff6f0df.toInt(),24),
            tint(main[2],0xfff6f0df.toInt(),47),main[4],
            accent[1],accent[2],tint(accent[2],accent[4],25),
            tint(accent[2],accent[4],53),accent[4])
        fun put(x:Int,y:Int,color:Int) {
            if(x in 2..61 && y in 2..61)out[y*64+x]=palette[color]
        }
        fun point(p:P,color:Int)=put(p.x,p.y,color)
        fun shape(points:List<P>,material:Boolean=false,far:Boolean=false) {
            if(points.size<3)return
            val x0=points.minOf{it.x}.coerceAtLeast(2)
            val x1=points.maxOf{it.x}.coerceAtMost(61)
            val y0=points.minOf{it.y}.coerceAtLeast(2)
            val y1=points.maxOf{it.y}.coerceAtMost(61)
            if(x1<x0||y1<y0)return
            val mask=BooleanArray(4096)
            for(y in y0..y1)for(x in x0..x1) {
                var inside=false
                var j=points.lastIndex
                for(i in points.indices) {
                    val a=points[i];val b=points[j]
                    if((a.y>y+.5)!=(b.y>y+.5) &&
                        x+.5<(b.x-a.x)*(y+.5-a.y)/(b.y-a.y)+a.x)inside=!inside
                    j=i
                }
                if(inside)mask[y*64+x]=true
            }
            val span=max(1,y1-y0)
            val width=max(1,x1-x0)
            for(y in y0..y1)for(x in x0..x1) {
                val i=y*64+x
                if(!mask[i])continue
                val edge=!mask[i-1]||!mask[i+1]||!mask[i-64]||!mask[i+64]
                val shade=(y-y0)*100/span+(x-x0)*17/width
                val col=when {
                    edge->0
                    far->if(material)6 else 1
                    shade<30->if(material)9 else 4
                    shade<48->if(material)8 else 3
                    shade<83->if(material)7 else 2
                    else->if(material)6 else 1
                }
                put(x,y,col)
            }
        }
        fun poly(vararg coords:Int,accent:Boolean=false,far:Boolean=false) {
            shape(coords.toList().chunked(2).map{P(it[0],it[1])},accent,far)
        }
        fun oval(c:P,rx:Int,ry:Int,accent:Boolean=false,far:Boolean=false) {
            shape((0..11).map { i->val t=i*PI/6
                c.move((cos(t)*rx).roundToInt(),(sin(t)*ry).roundToInt()) },accent,far)
        }
        fun link(a:P,b:P,r1:Int,r2:Int,accent:Boolean=false,far:Boolean=false) {
            val dx=(b.x-a.x).toDouble();val dy=(b.y-a.y).toDouble()
            val len=hypot(dx,dy).coerceAtLeast(1.0)
            fun edge(p:P,r:Int,k:Int)=p.move((-dy/len*r*k).roundToInt(),(dx/len*r*k).roundToInt())
            shape(listOf(edge(a,r1,1),edge(b,r2,1),edge(b,r2,-1),edge(a,r1,-1)),accent,far)
            oval(b,max(2,r2),max(2,r2),accent,far)
        }
        fun line(a:P,b:P,col:Int) {
            val n=max(abs(a.x-b.x),abs(a.y-b.y)).coerceAtLeast(1)
            for(i in 0..n)put(a.x+(b.x-a.x)*i/n,a.y+(b.y-a.y)*i/n,col)
        }
        fun inside(p:P,color:Int) {
            if(p.x in 2..61&&p.y in 2..61 && out[p.y*64+p.x]!=0)point(p,color)
        }
        fun claw(p:P,dx:Int=3,dy:Int=1) {
            shape(listOf(p.move(-1,-2),p.move(dx,-1),p.move(dx+2,dy+1),p.move(0,dy+2)),true)
        }
        fun eye(p:P,mask:Boolean=false) {
            if(mask) {
                for(i in -2..2){inside(p.move(i,0),0);inside(p.move(i,-1),0)}
                inside(p.move(1,-1),10);inside(p.move(2,-1),9)
            } else {
                for(y in -1..1)for(x in -2..2)inside(p.move(x,y),0)
                inside(p.move(0,-1),5);inside(p.move(1,0),10)
            }
        }
    }

    fun render(g:CreatureDna.Genome,stage:Int,phase:Int,motion:CreatureDna.Motion):PixelSprite {
        val r=rig(g,stage,phase,motion)
        val f=r.family;val c=r.c;val h=r.h;val rx=r.rx;val ry=r.ry
        val p=Painter(g)
        val beat=sin(Math.floorMod(phase,4)*PI/2)
        val step=Math.floorMod(phase,4)
        val strike=motion==CreatureDna.Motion.ATTACK&&step==2
        val shell=g.style[2]
        val appendage=g.style[3]
        val head=g.style[1]
        val plated=shell==3||shell==4||g[10]>=6
        // Distinct rear anatomy. Long coils, broad fins and organic tails
        // are geometry, not a reused sprite or a detached accessory.
        when(f) {
            4 -> {
                val bend=(beat*3).roundToInt()
                val q=listOf(P(11,47+bend),P(19,52),P(31,49),c,c.move(7,-11),h.move(-4,4))
                for(i in 0 until q.lastIndex)p.link(q[i],q[i+1],if(i==0)3 else 6,if(i<3)7 else 5)
                for(i in 1..3)p.line(q[i].move(-3,0),q[i].move(1,3),7)
            }
            7 -> {
                val a=c.move(-rx+3,0);val t=P(10,c.y+(beat*3).roundToInt())
                p.link(a,t,6,3,true)
                p.shape(listOf(t,t.move(-7,-11),t.move(-3,-2),t.move(-8,11),t.move(2,4)),true)
            }
            9 -> {
                val a=c.move(-3,8);val t=P(19,53+(beat*3).roundToInt())
                p.shape(listOf(c.move(-rx,-3),c.move(rx,5),c.move(7,16),t,
                    t.move(6,-8),c.move(-12,7)),true)
                p.link(a,t,7,2,true)
            }
            else -> if(g[7]>0 || f==1) {
                val root=r.tail;val length=7+g[7]
                val joint=root.move(-length/2,if(appendage%2==0)6 else -6)
                val tip=P((root.x-length).coerceAtLeast(5),
                    (joint.y-(if(appendage%2==0)7 else 8)+(beat*4).roundToInt()).coerceIn(8,55))
                p.link(root,joint,4+g[7]/4,3)
                p.link(joint,tip,3,2,true)
                if(appendage==0||appendage==3) {
                    p.shape(listOf(tip.move(3,5),tip.move(-4,0),tip.move(-3,-7),tip.move(2,-3),tip.move(5,0)),true)
                } else if(appendage==2)p.claw(tip,-4,-2)
            }
        }
        // Wing/fins have articulated outer geometry and a shared root.
        val wingPower=max(g[8],if(f==5)6 else if(f==1)4 else 0)
        if(wingPower>=3 && f!=7) {
            val root=r.wing
            val tip=P((root.x-11-wingPower).coerceAtLeast(5),
                (root.y-9-wingPower+(beat*6).roundToInt()).coerceAtLeast(8))
            val elbow=root.move(-5,-8+(beat*2).roundToInt())
            if(f==5||appendage==0) {
                for(i in 0..3) {
                    val end=tip.move(i*4,i*5)
                    p.shape(listOf(root.move(2,4),elbow,end.move(-2,-3),end.move(-2,5),
                        end.move(3,8),root.move(-3,8)),i%2==0,i==3)
                }
                p.link(root,elbow,4,3,true)
            } else {
                p.shape(listOf(root.move(2,5),elbow,tip,tip.move(9,4),tip.move(7,10),
                    root.move(-1,2),root.move(5,-7),root.move(4,7)),true)
                p.line(root,tip,6);p.line(root,tip.move(9,4),6)
                p.line(elbow,tip.move(1,1),9)
            }
        }
        // Legs of quadrupeds, birds, insects and upright monsters have
        // different silhouettes and independent phase-shifted knees.
        fun leg(i:Int,far:Boolean) {
            val n=g[3]
            if(n==0)return
            val upright=f in listOf(2,3,8)
            val x=if(n==1)c.x+(if(far)-5 else 4) else c.x-rx+5+i*(2*rx-10)/max(1,n-1)+(if(far)-3 else 1)
            val root=P(x,c.y+ry/2)
            val stride=if(motion==CreatureDna.Motion.ATTACK)0.0 else
                sin(step*PI/2+(i+(if(far)1 else 0))*PI)
            val foot=P((x+(if(upright)3 else if(f==6)6 else 2)+(stride*4).roundToInt()).coerceIn(5,55),
                (51+g[4]/3-(if(far)3 else 0)-(max(0.0,stride)*4).roundToInt()).coerceAtMost(58))
            val dx=(foot.x-root.x).toDouble();val dy=(foot.y-root.y).toDouble()
            val span=hypot(dx,dy)
            val (kx,ky)=CreatureDna.knee(root.x,root.y,foot.x,foot.y,span/2+2.3,if(i%2==0)1 else -1)
            val knee=P(kx,ky)
            val thick=when(f){3,8->5;6,5->2;2->4;else->4}
            p.link(root,knee,thick,if(f==6)2 else thick-1,plated,far)
            p.link(knee,foot,max(2,thick-1),if(f==5||f==6)2 else 3,plated,far)
            if(!far) {
                if(f==6||f==5){p.claw(foot,4,0);p.claw(foot.move(-2,0),-3,0)}
                else {
                    p.shape(listOf(foot.move(-4,-3),foot.move(2,-3),foot.move(6,0),
                        foot.move(5,3),foot.move(-4,3)),plated)
                    p.claw(foot.move(3,1),3,0)
                    if(g[9]>=4)p.claw(foot.move(0,1),3,1)
                }
            }
        }
        for(i in 0 until g[3])leg(i,true)
        // Core anatomy: never use the same torso contour across all families.
        when(f) {
            0 -> {
                p.oval(c.move(-7,1),rx/2+3,ry)
                p.shape(listOf(c.move(-rx,-ry/2),c.move(-rx+5,-ry),c.move(2,-ry+1),
                    c.move(rx,-ry/2),c.move(rx,4),c.move(3,ry),c.move(-8,ry-2)))
                p.oval(r.shoulder.move(0,2),6,ry,plated)
            }
            1 -> {
                p.shape(listOf(c.move(-rx,3),c.move(-rx+3,-ry),c.move(3,-ry-2),
                    c.move(rx,-2),c.move(rx-3,ry),c.move(-8,ry)))
                p.link(c.move(rx/2,-2),h.move(-3,4),7,5)
                p.shape(listOf(c.move(0,ry-1),c.move(rx-3,-6),h.move(-3,5),
                    h.move(3,5),c.move(rx-2,ry+1)),true)
            }
            2,8 -> {
                p.shape(listOf(c.move(-rx-3,-ry+4),c.move(-rx/2,-ry),
                    c.move(rx/2,-ry),c.move(rx+3,-ry+5),c.move(rx/2,2),
                    c.move(rx-2,ry),c.move(-rx+2,ry),c.move(-rx/2,2)),plated)
                p.oval(c.move(0,-3),rx/2,ry/3,true)
            }
            3 -> {
                p.shape(listOf(c.move(-rx,-ry+3),c.move(-rx/2,-ry),c.move(rx/2,-ry),
                    c.move(rx+3,-ry/2),c.move(rx,ry-2),c.move(0,ry+2),
                    c.move(-rx,ry-1)),true)
                p.shape(listOf(c.move(-8,-7),c.move(7,-10),c.move(10,3),
                    c.move(0,10),c.move(-8,4)))
            }
            4 -> {p.oval(c,rx,ry);p.link(c.move(5,-1),h.move(-2,4),7,5)}
            5 -> {
                p.shape(listOf(c.move(-rx-2,4),c.move(-rx,-5),c.move(-4,-ry),
                    c.move(rx-1,-ry+2),c.move(rx,1),c.move(5,ry+4),c.move(-6,ry)))
                p.oval(c.move(5,2),7,9,true)
                p.link(c.move(6,-4),h.move(-3,3),5,4)
            }
            6 -> {
                p.oval(c.move(-10,0),10,ry+2,plated)
                p.oval(c.move(1,1),7,ry)
                p.oval(c.move(9,-1),7,ry-1,plated)
            }
            7 -> {
                p.shape(listOf(c.move(-rx-2,0),c.move(-rx/2,-ry),c.move(3,-ry-1),
                    c.move(rx,-5),c.move(rx+3,3),c.move(5,ry+1),c.move(-rx/2,ry)))
                p.shape(listOf(c.move(-6,-ry+2),c.move(-3,-ry-9),c.move(5,-ry-5),c.move(9,-ry+3)),true)
                p.shape(listOf(c.move(-2,3),c.move(-11,ry+7),c.move(8,ry-1)),true)
            }
            else -> {
                p.shape(listOf(c.move(-rx,-ry+5),c.move(-4,-ry),c.move(7,-ry+2),
                    c.move(rx+2,-2),c.move(7,ry+7),c.move(-1,ry+2),
                    c.move(-rx-3,ry+3),c.move(-rx,0)))
                p.oval(c.move(0,-2),6,9,true)
            }
        }
        for(i in 0 until g[3])leg(i,false)
        // Forelimbs: armored gauntlets and insect pincers rather than
        // identical feet placed at shoulder height.
        if(f in listOf(2,3,6,8)) {
            for(far in listOf(true,false)) {
                val root=r.shoulder.move(if(far)-rx else 1,if(far)-2 else 0)
                val elbow=root.move(if(far)-5 else 7,9)
                val fist=elbow.move(if(strike)9 else if(far)-1 else 4,-3+(beat*2).roundToInt())
                p.link(root,elbow,if(f==3)6 else 4,4,plated,far)
                p.link(elbow,fist,4,if(f==6)3 else 5,plated,far)
                if(f==6) {
                    p.shape(listOf(fist.move(-3,-4),fist.move(7,-7),fist.move(6,-2),fist.move(1,0)),true)
                    p.shape(listOf(fist.move(-2,2),fist.move(6,1),fist.move(8,5),fist.move(0,5)),true)
                } else if(!far) {
                    p.oval(fist,5,5,true)
                    p.line(fist.move(-2,-1),fist.move(2,-2),9)
                    p.claw(fist.move(2,1),4,1)
                }
            }
        }
        if(f in listOf(0,6))p.link(r.shoulder,h.move(-3,3),6,5)
        // Independently inherited head anatomy, adapted to each body.
        val hr=(if(f in listOf(2,3,8,9))8 else 6)+g[1]/5
        val snout=(3+g[6]/2).coerceAtMost(7)
        when(head) {
            0 -> {
                p.shape(listOf(h.move(-hr,-2),h.move(-hr+2,-hr),h.move(2,-hr+1),
                    h.move(hr-2,-3),h.move(hr+snout,0),h.move(hr+snout-1,4),
                    h.move(0,hr),h.move(-hr,4)))
                p.shape(listOf(h.move(-5,-hr+1),h.move(-9,-hr-8),h.move(-1,-hr-3),h.move(1,-hr+2)),true)
                p.shape(listOf(h.move(1,-hr+1),h.move(0,-hr-6),h.move(6,-hr+1)),true)
            }
            1 -> {
                p.shape(listOf(h.move(-hr,-3),h.move(-hr+4,-hr),h.move(3,-hr),
                    h.move(hr+snout,-2),h.move(hr+snout,3),h.move(3,hr),
                    h.move(-hr,hr-2)))
                p.shape(listOf(h.move(-hr,-4),h.move(-hr-3,-hr-6),h.move(-3,-hr+1)),true)
                p.shape(listOf(h.move(-1,-hr+1),h.move(2,-hr-7),h.move(5,-hr+2)),true)
            }
            2 -> {
                p.shape(listOf(h.move(-hr,-1),h.move(-hr+2,-hr),h.move(2,-hr-2),
                    h.move(hr,-hr/2),h.move(hr+1,3),h.move(3,hr+2),
                    h.move(-4,hr),h.move(-hr,4)))
                p.shape(listOf(h.move(-5,-3),h.move(6,-5),h.move(8,2),h.move(2,6),h.move(-4,3)),true)
            }
            3 -> {
                p.shape(listOf(h.move(-hr,-hr+1),h.move(2,-hr-3),h.move(hr,-hr+2),
                    h.move(hr+2,3),h.move(4,hr+2),h.move(-hr,hr)),true)
                p.shape(listOf(h.move(-5,-3),h.move(hr,-4),h.move(hr,4),h.move(-4,4)))
            }
            else -> {
                p.oval(h,hr,hr)
                p.shape(listOf(h.move(3,-3),h.move(hr+snout+1,-1),
                    h.move(hr+snout-2,5),h.move(2,4)),true)
                if(f==5)p.shape(listOf(h.move(-hr,-4),h.move(-hr-3,-hr-6),h.move(0,-hr)),true)
            }
        }
        if(g[9]>=5 && head!=0 && head!=1) {
            p.shape(listOf(h.move(-5,-hr+2),h.move(-7,-hr-7),h.move(1,-hr+2)),true)
        }
        val eye=h.move(if(head==3)3 else 2,-2)
        p.eye(eye,head==2||head==3)
        if(head==0||head==1) {
            val nose=h.move(min(hr+snout-2,61-h.x),1)
            p.inside(nose,0)
            p.line(h.move(4,4),nose.move(-1,2),0)
            if(g[9]>=3)p.inside(h.move(6,4),5)
        }
        // Restrained clustered texture, accents and specular highlights.
        // Only mark pixels within the actual body.
        // Surface genes change large coherent regions, not just isolated dots.
        if(shell==0 && f in listOf(0,5,6)) {
            val mane=r.shoulder.move(-2,-3)
            p.shape(listOf(mane.move(-8,3),mane.move(-6,-7),mane.move(-1,-9),
                mane.move(5,-6),mane.move(7,1),mane.move(3,3),mane.move(5,8),
                mane.move(-1,6),mane.move(-3,9)),true)
        } else if(shell==1 && f in listOf(0,1,4,7)) {
            for(i in 0..3) {
                val q=c.move(-rx/2+i*max(3,rx/3),ry/3)
                p.line(q.move(-2,-1),q.move(1,1),1)
                p.inside(q.move(0,-1),3)
            }
        } else if(shell==2 && f in listOf(2,3,8,9)) {
            p.shape(listOf(c.move(-5,-7),c.move(5,-7),c.move(6,0),
                c.move(2,7),c.move(-3,5),c.move(-6,0)),true)
            p.inside(c.move(0,-4),10)
        }
        if(plated) {
            for(i in 0..2) {
                val q=c.move(-rx/2+i*max(4,rx/2),-ry+4)
                p.shape(listOf(q.move(-3,1),q.move(0,-4),q.move(4,-1),q.move(3,4),q.move(-2,4)),true)
            }
            p.oval(c.move(1,-2),4,4,true)
            p.inside(c.move(0,-3),10)
        } else if(shell==0) {
            for(i in 0..3) {
                val x=c.x-rx+5+i*max(3,rx/3)
                p.inside(P(x,c.y-ry/3),4)
                p.inside(P(x-1,c.y-ry/3+1),3)
                p.inside(P(x,c.y+ry/3),1)
            }
        } else {
            // Stable seed-based marking placement adds individuality without
            // changing the inherited body plan or the saved identity.
            val offset=Math.floorMod(g.variation,3)-1
            for(i in 0..2) {
                val q=c.move(-5+i*5+offset,if(i%2==0)-2 else 3)
                p.inside(q,8);p.inside(q.move(1,0),9);p.inside(q.move(0,1),7)
            }
        }
        return CreatureQuality.connected(PixelSprite.fromArgb(64,64,p.out))
    }
}
