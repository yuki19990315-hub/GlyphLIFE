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

    @Test fun qualityAndCombatAreDerivedFromGenes() {
        for(a in MonsterBodyType.entries)for(b in MonsterBodyType.entries) {
            val child=FusionRules.preview(parent(a),parent(b,1))
            val g=CreatureDna.decode(child.designSeed)!!
            val art=MonsterDesign.sprite(child)
            assertTrue(CreatureQuality.inspect(art).valid)
            val traits=CreatureDna.combat(g)
            assertEquals(traits.skill,dev.glyphlife.monsterquest.model.SkillRules.forMonster(child).id)
            assertTrue(traits.attackBonus in 0..4)
            assertTrue(traits.hpBonus in 0..6)
            assertTrue(traits.power in 0..6)
            val walk=MonsterAnimation.walk(art,child)
            assertEquals(4,walk.size)
            assertTrue(walk.any{!it.sprite.pixels().contentEquals(art.pixels())})
            assertTrue(walk.all{GbaMonsterArtStandard.audit(it.sprite).compliant})
        }
    }

    @Test fun socketsMoveAndArmorFollowsGeneratedBody() {
        val child=FusionRules.preview(parent(MonsterBodyType.DRAGON),
            parent(MonsterBodyType.BEAST,1)).copy(stage=3,
            armorParts=mapOf("HEAD" to "PLATE"))
        val g=CreatureDna.decode(child.designSeed)!!
        val rest=CreatureDna.anchor(child,"HEAD")!!
        val hit=CreatureDna.anchor(child,"HEAD",2,CreatureDna.Motion.ATTACK)!!
        assertNotEquals(rest,hit)
        val native=MonsterDesign.sprite(child)
        val frames=MonsterAnimation.attack(native,child)
        assertTrue(frames.all{GbaMonsterArtStandard.audit(it.sprite).compliant})
        assertTrue(frames.any{!it.sprite.pixels().contentEquals(native.pixels())})
        val custom=child.copy(customArmorParts=mapOf("HEAD" to
            "10000000-0000-4000-8000-000000000001"))
        val raw=IntArray(4096)
        for(y in 29..34)for(x in 24..39)raw[y*64+x]=0xff99ffee.toInt()
        val layer=PixelSprite.fromArgb(64,64,raw)
        val base=CreatureDna.sprite(child,g,2,CreatureDna.Motion.ATTACK)
        val mounted=CustomArmorLayers.composeAt(base,custom,
            { slot->CreatureDna.anchor(child,slot,2,CreatureDna.Motion.ATTACK) }) { layer }
        assertTrue(GbaMonsterArtStandard.audit(mounted).compliant)
        assertFalse(mounted.pixels().contentEquals(base.pixels()))
    }

    @Test fun topologyAllelesKeepAllTenSilhouettesDistinct() {
        val seen=mutableSetOf<Int>()
        val silhouettes=mutableListOf<BooleanArray>()
        for(type in MonsterBodyType.entries) {
            val child=FusionRules.preview(parent(type),parent(type,1))
            val g=CreatureDna.decode(child.designSeed)!!
            assertEquals(type,CreatureDna.bodyType(g))
            assertTrue(child.designSeed.startsWith("dna2:"))
            assertEquals(g,CreatureDna.decode(g.encode()))
            val art=MonsterDesign.sprite(child)
            assertTrue("Invalid "+type,CreatureQuality.inspect(art).valid)
            val pixels=art.pixels()
            val mask=BooleanArray(4096){pixels[it]!=0}
            assertTrue(seen.add(mask.contentHashCode()))
            silhouettes.add(mask)
            for(phase in 0..3) {
                val frame=CreatureArtV2.render(g,child.stage,phase,CreatureDna.Motion.WALK)
                assertTrue("Invalid frame "+type+" "+phase,CreatureQuality.inspect(frame).valid)
            }
        }
        // Exact pixel identity alone is insufficient; compare outline masks.
        for(i in silhouettes.indices)for(j in i+1 until silhouettes.size) {
            val a=silhouettes[i];val b=silhouettes[j]
            val union=a.indices.count{a[it]||b[it]}
            val intersection=a.indices.count{a[it]&&b[it]}
            assertTrue("Near-identical outline "+i+"/"+j,intersection*100<union*94)
        }
        val v1=CreatureDna.Genome(listOf(5,5,2,2,5,3,5,6,0,2,2,0,1),123)
        assertEquals(v1,CreatureDna.decode(v1.encode()))
        assertTrue(v1.encode().startsWith("dna1:"))
    }

    @Test fun animationUsesOneCommonViewport() {
        fun sample(wing:Boolean):PixelSprite {
            val data=IntArray(4096)
            for(y in 30..42)for(x in 24..35)data[y*64+x]=0xff99ffee.toInt()
            if(wing)for(y in 12..35)for(x in 2..24)data[y*64+x]=0xff445588.toInt()
            return PixelSprite.fromArgb(64,64,data)
        }
        val fitted=GbaMonsterArtStandard.fitAnimationWithMargin(listOf(sample(false),sample(true)),9)
        val first=fitted[0].pixels()
        val second=fitted[1].pixels()
        assertTrue(first.indices.any{first[it]==0xff99ffee.toInt()})
        // All body pixels that are not overpainted by the wing retain their location.
        assertTrue(first.indices.filter{first[it]==0xff99ffee.toInt() && second[it]!=0xff445588.toInt()}
            .all{second[it]==first[it]})
        for(frame in fitted) {
            val b=GbaMonsterArtStandard.bounds(frame)!!
            assertTrue(b.left>=9 && b.right<=54 && b.top>=9 && b.bottom<=54)
        }
    }

    @Test fun malformedSeedsAndLegacyArtDoNotOptIn() {
        for(s in listOf("","fusion-legacy","dna1:9999999999999:0","dna1:123:x","dna1:0000000000000:gg"))
            assertNull(CreatureDna.decode(s))
    }
}
