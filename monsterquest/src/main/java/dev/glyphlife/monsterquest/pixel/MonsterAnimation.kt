package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import kotlin.math.max
import kotlin.math.min

data class MonsterPixelFrame(
    val sprite: PixelSprite,
    val durationMs: Int
)

/**
 * Small original pixel-animation set composed from each creature's 64x64 master.
 * Never generates animation from the reduced 13x13 Glyph output.
 */
object MonsterAnimation {
    fun idle(master: PixelSprite, wild: WildMonster): List<MonsterPixelFrame> {
        require(master.width == 64 && master.height == 64)
        return listOf(
            MonsterPixelFrame(master, 500),
            MonsterPixelFrame(translate(master, 0, -1), 250),
            MonsterPixelFrame(blink(master, wild), 140),
            MonsterPixelFrame(translate(master, 0, 1), 250)
        )
    }

    fun attack(master: PixelSprite, wild: WildMonster): List<MonsterPixelFrame> {
        require(master.width == 64 && master.height == 64)
        return listOf(
            MonsterPixelFrame(translate(master,-1,0),130),
            MonsterPixelFrame(attackPose(master,wild),170),
            MonsterPixelFrame(translate(master,1,0),150),
            MonsterPixelFrame(master,280)
        )
    }

    fun sheet(frames: List<MonsterPixelFrame>): PixelSprite {
        require(frames.size in 1..16)
        val width=frames.size*64
        val result=IntArray(width*64)
        frames.forEachIndexed { frame,spriteFrame ->
            val sprite=spriteFrame.sprite
            require(sprite.width==64 && sprite.height==64)
            for(y in 0 until 64)for(x in 0 until 64)
                result[y*width+frame*64+x]=sprite.pixel(x,y)
        }
        return PixelSprite.fromArgb(width,64,result)
    }

    private fun translate(source: PixelSprite, dx:Int, dy:Int): PixelSprite {
        // Never crop a horn, wingtip or tail while moving the entire sprite.
        val box = GbaMonsterArtStandard.bounds(source)
        val safeDx = if (box != null)
            dx.coerceIn(-box.left, 63-box.right) else dx
        val safeDy = if (box != null)
            dy.coerceIn(-box.top, 63-box.bottom) else dy
        val pixels=IntArray(4096)
        for(y in 0 until 64)for(x in 0 until 64) {
            val sx=x-safeDx;val sy=y-safeDy
            if(sx in 0..63 && sy in 0..63) pixels[y*64+x]=source.pixel(sx,sy)
        }
        return PixelSprite.fromArgb(64,64,pixels)
    }

    private fun blink(master:PixelSprite,wild:WildMonster):PixelSprite {
        val outline=master.colors().minByOrNull { color ->
            ((color ushr 16) and 255)*30 +
                ((color ushr 8) and 255)*59 + (color and 255)*11
        } ?: return master
        val pixels=master.pixels()
        val head=when(MonsterDesign.bodyType(wild)) {
            MonsterBodyType.BEAST,MonsterBodyType.DRAGON -> 20 to 23
            MonsterBodyType.AVIAN -> 33 to 20
            MonsterBodyType.AQUATIC -> 20 to 29
            MonsterBodyType.SERPENT -> 19 to 24
            MonsterBodyType.INSECTOID -> 26 to 21
            MonsterBodyType.SPIRIT -> 26 to 27
            else -> 28 to 19
        }
        val (eyeX,eyeY)=head
        for(x in eyeX-2..eyeX+2) {
            if(x in 0..63 && eyeY in 0..63) pixels[eyeY*64+x]=outline
        }
        return PixelSprite.fromArgb(64,64,pixels)
    }

    private fun attackPose(master:PixelSprite,wild:WildMonster):PixelSprite {
        val shades=master.colors().sortedBy { color ->
            ((color ushr 16) and 255)*30 +
                ((color ushr 8) and 255)*59 + (color and 255)*11
        }
        val primary=shades.lastOrNull() ?: return master
        val secondary=shades.getOrElse((shades.size*2/3).coerceAtLeast(0)) { primary }
        val pixels=translate(master,3,-1).pixels()
        // Three sharp impact/radiance pixels as a cohesive shape.
        for(n in 0..4) {
            val x=54+n;val y=25-n
            if(x in 0..63 && y in 0..63) {
                pixels[y*64+x]=primary
                if(y+1<64) pixels[(y+1)*64+x]=secondary
            }
        }
        return PixelSprite.fromArgb(64,64,pixels)
    }
}
