package dev.glyphlife.monsterquest.pixel

/** Mechanical checks, not a guarantee of artistic quality. */
object CreatureQuality {
    data class Report(val valid:Boolean,val score:Int,val connected:Int,val opaque:Int)
    private fun component(sprite:PixelSprite):BooleanArray {
        require(sprite.width==64 && sprite.height==64)
        val p=sprite.pixels()
        val seen=BooleanArray(4096)
        var best=IntArray(0)
        for(start in p.indices) {
            if(seen[start] || p[start]==0)continue
            val q=IntArray(4096);var head=0;var size=1
            q[0]=start;seen[start]=true
            while(head<size) {
                val i=q[head++];val x=i%64;val y=i/64
                for(dy in -1..1)for(dx in -1..1) {
                    val nx=x+dx;val ny=y+dy
                    if(nx !in 0..63 || ny !in 0..63)continue
                    val j=ny*64+nx
                    if(!seen[j] && p[j]!=0) { seen[j]=true;q[size++]=j }
                }
            }
            if(size>best.size)best=q.copyOf(size)
        }
        return BooleanArray(4096).also { mask->best.forEach{mask[it]=true} }
    }
    fun connected(sprite:PixelSprite):PixelSprite {
        val p=sprite.pixels();val mask=component(sprite)
        return PixelSprite.fromArgb(64,64,IntArray(4096){if(mask[it])p[it] else 0})
    }
    fun inspect(sprite:PixelSprite):Report {
        val audit=GbaMonsterArtStandard.audit(sprite)
        val b=audit.opaqueBounds
        val opaque=sprite.opaqueCount()
        val connected=component(sprite).count{it}
        val width=b?.width?:0;val height=b?.height?:0
        val valid=audit.compliant && opaque in 150..2600 &&
            connected*100>=opaque*97 && width>=16 && height>=16 && audit.opaqueColors>=4
        val score=(if(valid)10000 else 0)+connected*100/opaque.coerceAtLeast(1)+
            width+height+audit.opaqueColors*4-(opaque-connected)*3
        return Report(valid,score,connected,opaque)
    }
}
