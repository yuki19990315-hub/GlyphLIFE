package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.model.FusionRules
import org.junit.Assert.*
import org.junit.Test

class ArmorArticulationTest {
    private fun sample(left:Int=10,top:Int=16):PixelSprite {
        val p=IntArray(4096)
        for(y in top until top+12)for(x in left until left+28) {
            p[y*64+x]=if(y<top+3)0xffa1dcf0.toInt() else 0xff435a7f.toInt()
        }
        return PixelSprite.fromArgb(64,64,p)
    }

    @Test fun identityDoesNotChangeOriginal() {
        val p=sample()
        val before=p.pixels()
        assertTrue(ArmorArticulation.deform(p,ArmorArticulation.Pose()).pixels().contentEquals(before))
        ArmorArticulation.deform(p,ArmorArticulation.Pose(30.0,6.0))
        assertTrue(p.pixels().contentEquals(before))
    }

    @Test fun bendAndRotationProduceDistinctBoundedPixelArt() {
        for(p in listOf(sample(),sample(35,1),sample(1,51))) {
            for(angle in listOf(-35.0,0.0,35.0))for(bend in listOf(-8.0,8.0)) {
                val q=ArmorArticulation.deform(p,ArmorArticulation.Pose(angle,bend))
                assertTrue(GbaMonsterArtStandard.audit(q).compliant)
                assertTrue(q.opaqueCount()>100)
                assertTrue(q.colors().all{it in p.colors()})
                assertFalse(q.pixels().contentEquals(p.pixels()))
            }
        }
    }

    @Test fun wingAndTailFollowGenomePhase() {
        val a=WildMonster("dragon","竜","火",30,6,bodyType="DRAGON")
        val b=WildMonster("bird","鳥","水",25,5,bodyType="AVIAN")
        val child=FusionRules.preview(a,b).copy(stage=3,customArmorParts=mapOf(
            "WING" to "10000000-0000-4000-8000-000000000001"))
        val rest=CreatureDna.armorPose(child,"WING",0,CreatureDna.Motion.REST)
        val up=CreatureDna.armorPose(child,"WING",1,CreatureDna.Motion.WALK)
        val down=CreatureDna.armorPose(child,"WING",3,CreatureDna.Motion.WALK)
        assertEquals(ArmorArticulation.Pose(),rest)
        assertTrue(up.degrees>0 && down.degrees<0)
        val g=CreatureDna.decode(child.designSeed)!!
        val layer=sample()
        val frames=(0..3).map { phase->
            val master=CreatureDna.sprite(child,g,phase,CreatureDna.Motion.WALK)
            CustomArmorLayers.composeArticulated(master,child,
                { slot->CreatureDna.anchor(child,slot,phase,CreatureDna.Motion.WALK) },
                { slot->CreatureDna.armorPose(child,slot,phase,CreatureDna.Motion.WALK) }) { layer }
        }
        assertTrue(frames.all{GbaMonsterArtStandard.audit(it).compliant})
        assertTrue(frames.map{it.pixels().contentHashCode()}.distinct().size>=3)
        assertTrue(frames.all{FullBodyGlyphProjector.project(it).size==169})
    }
}
