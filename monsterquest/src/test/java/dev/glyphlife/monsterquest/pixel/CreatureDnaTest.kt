package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.FusionRules
import dev.glyphlife.monsterquest.model.WildMonster
import org.junit.Assert.*
import org.junit.Test

class CreatureDnaTest {
    private fun parent(type:MonsterBodyType,n:Int=0)=WildMonster(
        "id-${type.name}-$n",type.name,if(n%2==0)"火" else "水",30,6,
        bodyType=type.name,designSeed="founder-$n")

    @Test fun everyBodyPairHasValidStableArt() {
        val distinct=mutableSetOf<String>()
        for(a in MonsterBodyType.entries)for(b in MonsterBodyType.entries) {
            val x=parent(a); val y=parent(b,1)
            val child=FusionRules.preview(x,y)
            val g=CreatureDna.decode(child.designSeed)!!
            assertEquals(g,CreatureDna.decode(g.encode()))
            assertTrue(g.encode().length<=64)
            val master=MonsterDesign.sprite(child)
            assertTrue(GbaMonsterArtStandard.audit(master).compliant)
            assertTrue(master.opaqueCount()>100)
            assertTrue(master.pixels().contentEquals(MonsterDesign.sprite(child.copy(id="new-id")).pixels()))
            assertEquals(GlyphProjector.project(master),child.pixels)
            assertTrue(child.pixels.any{it=='1'})
            distinct.add(master.pixels().contentHashCode().toString())
        }
        assertTrue(distinct.size>=50)
    }

    @Test fun repeatedBreedingKeepsGenomeAndFramesSafe() {
        var x=parent(MonsterBodyType.SERPENT)
        for(i in 1..60) {
            x=FusionRules.preview(x,parent(MonsterBodyType.entries[i%MonsterBodyType.entries.size],i))
            val g=CreatureDna.decode(x.designSeed)!!
            assertEquals(g,CreatureDna.fromMonster(x))
            val master=MonsterDesign.sprite(x)
            val frames=MonsterAnimation.idle(master,x)+MonsterAnimation.attack(master,x)
            for(frame in frames) {
                assertTrue(GbaMonsterArtStandard.audit(frame.sprite).compliant)
                assertTrue(frame.sprite.opaqueCount()>100)
                val leds=FullBodyGlyphProjector.project(frame.sprite)
                assertEquals(169,leds.size)
                assertTrue(leds.any{it>0})
                assertTrue(leds.indices.all{GlyphProjector.isPhysical(it)||leds[it]==0})
            }
            assertTrue(frames.any{!it.sprite.pixels().contentEquals(master.pixels())})
        }
    }

    @Test fun malformedSeedsAndLegacyArtDoNotOptIn() {
        for(s in listOf("","fusion-legacy","dna1:9999999999999:0","dna1:123:x","dna1:0000000000000:gg"))
            assertNull(CreatureDna.decode(s))
    }
}
