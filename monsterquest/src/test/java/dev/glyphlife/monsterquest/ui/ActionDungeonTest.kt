package dev.glyphlife.monsterquest.ui

import org.junit.Assert.*
import org.junit.Test

class ActionDungeonTest {
    @Test fun generationAlwaysHasTraversableCentralRoute() {
        for(floor in 1..100) {
            val r=ActionDungeon.start(floor,13827)
            assertFalse(ActionDungeon.wall(r.px,r.py,r.seed+floor*7919))
            assertFalse(ActionDungeon.wall(r.ex,r.ey,r.seed+floor*7919))
            for(y in 2..10) assertFalse(ActionDungeon.wall(4,y,r.seed+floor*7919))
        }
    }
    @Test fun collisionPreventsMovingOutsideMap() {
        val r=ActionDungeon.start(1,13827).copy(px=1,py=1)
        assertEquals(r,ActionDungeon.move(r,-1,0))
        assertEquals(r,ActionDungeon.move(r,0,-1))
    }
    @Test fun combatRequiresRangeAndEnemyCanBeDefeated() {
        val r=ActionDungeon.start(1,13827)
        assertEquals(r.enemyHp,ActionDungeon.attack(r).enemyHp)
        var near=r.copy(px=4,py=3)
        near=ActionDungeon.attack(near)
        assertTrue(near.enemyHp<r.enemyHp)
        repeat(10){near=ActionDungeon.attack(near)}
        assertTrue(near.won)
        assertEquals(1,near.kills)
    }
    @Test fun captureRequiresResourcesAndLowHp() {
        val r=ActionDungeon.start(1,13827).copy(enemyHp=10)
        assertEquals(1,ActionDungeon.capture(r).captures)
        assertEquals(0,ActionDungeon.capture(r).shards)
        assertTrue(ActionDungeon.capture(r).won)
        assertEquals(0,ActionDungeon.capture(r.copy(shards=0)).captures)
    }
    @Test fun dodgeHasInvulnerabilityAndCost() {
        val r=ActionDungeon.start(1,13827)
        val n=ActionDungeon.dodge(r,0,-1)
        assertTrue(n.invulnerableMs>0)
        assertTrue(n.stamina<r.stamina)
        assertTrue(n.py<r.py)
    }
    @Test fun enemyApproachesWithoutEnteringWall() {
        var r=ActionDungeon.start(1,13827)
        repeat(8){r=ActionDungeon.tick(r,700)}
        assertFalse(ActionDungeon.wall(r.ex,r.ey,r.seed+r.floor*7919))
    }

    @Test fun benchedCreaturesAttackUsingTheirRealStats() {
        val lead=dev.glyphlife.monsterquest.model.WildMonster("lead","雷獣","雷",60,10)
        val weak=dev.glyphlife.monsterquest.model.WildMonster("weak","鳥","雷",50,5)
        val strong=dev.glyphlife.monsterquest.model.WildMonster("strong","融合竜","雷",80,32,stage=3)
        val r=ActionDungeon.start(2,13827).copy(px=4,py=3,enemyHp=100,
            active=0,supportCooldownMs=0)
        val basic=ActionDungeon.support(r,listOf(lead,weak),"水")
        val boosted=ActionDungeon.support(r,listOf(lead,strong),"水")
        assertTrue(basic.enemyHp<r.enemyHp)
        assertTrue(boosted.enemyHp<basic.enemyHp)
        assertEquals(boosted,ActionDungeon.support(boosted,listOf(lead,strong),"水"))
    }
    @Test fun generatedOrFusedCreatureStatsAffectManualAttack() {
        val r=ActionDungeon.start(1,13827).copy(px=4,py=3,enemyHp=200)
        val basic=dev.glyphlife.monsterquest.model.WildMonster("a","獣","火",50,5)
        val fused=basic.copy(id="b",attack=28,stage=3,level=20)
        assertTrue(ActionDungeon.attack(r,monster=fused).enemyHp <
            ActionDungeon.attack(r,monster=basic).enemyHp)
    }
}
