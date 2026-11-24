package dev.glyphlife.monsterquest.model

import dev.glyphlife.monsterquest.pixel.GbaMonsterArtStandard
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import org.junit.Assert.*
import org.junit.Test

class OfflineMonsterForgeTest {
    @Test fun understandsJapaneseMonsterDescriptionsLocally() {
        val p=OfflineMonsterForge.plan("氷の狐。大きな角と結晶の尾")
        assertEquals("水",p.element)
        assertEquals(dev.glyphlife.monsterquest.pixel.MonsterBodyType.BEAST,p.bodyType)
        assertEquals("SPIKE",p.armor["HEAD"])
        assertEquals("CRYSTAL",p.armor["CORE"])
        assertEquals("CRYSTAL",p.armor["TAIL"])
    }
    @Test fun supportsEnglishAndCustomNameWithoutNetwork() {
        val p=OfflineMonsterForge.plan("name: TerraFox thunder dragon with crystal armor")
        assertEquals("TerraFox",p.name)
        assertEquals("雷",p.element)
        assertEquals(dev.glyphlife.monsterquest.pixel.MonsterBodyType.DRAGON,p.bodyType)
        assertEquals("CRYSTAL",p.armor["CORE"])
    }
    @Test fun oneShotMonsterIsExactGbaAssetAndConditionsAreSaved() {
        val m=OfflineMonsterForge.design("雷の鳥。翼と装甲", "f63e9d")
        assertEquals(MonsterOrigin.LOCAL_FORGE,m.originKind)
        assertTrue(m.originConditions.contains("雷の鳥"))
        assertTrue(m.designSeed.startsWith("local-"))
        assertEquals(169,m.pixels.length)
        assertTrue(GbaMonsterArtStandard.audit(MonsterDesign.sprite(m)).compliant)
    }
    @Test fun sameSeedPreservesPixelIdentityButNotInstanceIdentity() {
        val a=OfflineMonsterForge.design("火の竜", "A")
        val b=OfflineMonsterForge.design("火の竜", "A")
        assertNotEquals(a.id,b.id)
        assertArrayEquals(MonsterDesign.sprite(a).pixels(),MonsterDesign.sprite(b).pixels())
    }
    @Test fun differentAttributesKeepPaletteAtMostSixteenColors() {
        listOf("火のドラゴン", "氷の狐", "雷の機械騎士", "闇の蛇",
            "森の精霊", "光の鳥", "水の魚", "巨大な岩のゴーレム")
            .forEach {
                val sprite=MonsterDesign.sprite(OfflineMonsterForge.design(it,it))
                val audit=GbaMonsterArtStandard.audit(sprite)
                assertTrue("Audit failed for $it: $audit",audit.compliant)
                assertTrue(audit.totalColors <=16)
            }
    }
}
