package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MetroidRulesTest {
    private val zeroRandom=object:Random(){
        override fun nextBits(bitCount:Int):Int=0
    }
    private fun fresh():QuestState=MetroidRules.normalized(
        QuestState(companion=Companion(id="hero",level=12),
            playerHp=88,tapRun=TapRun(floor=9,bestFloor=15,gold=240))
    )

    @Test fun newSideModeDoesNotReplaceLegacyDungeonOrExistingInventory() {
        val original=fresh()
        assertFalse(original.side.legacy)
        assertEquals(0,original.side.room)
        assertEquals(MetroidRules.maxEnemyHp(0),original.side.enemyHp)
        val moved=MetroidRules.tick(original,1,33)
        assertTrue(moved.side.x>original.side.x)
        assertEquals(original.tapRun,moved.tapRun)
        assertEquals(original.playerHp,moved.playerHp)
        val legacy=MetroidRules.switchLegacy(moved,true)
        assertTrue(legacy.side.legacy)
        assertEquals(legacy,MetroidRules.tick(legacy,1,33))
        assertFalse(MetroidRules.switchLegacy(legacy,false).side.legacy)
    }

    @Test fun jumpHasActualArcAndLandsAfterGravity() {
        var state=fresh()
        state=MetroidRules.jump(state)
        assertFalse(state.side.grounded)
        assertTrue(state.side.vy<0f)
        repeat(10) {state=MetroidRules.tick(state,0,33)}
        assertTrue(state.side.y<MetroidRules.FLOOR)
        repeat(55) {state=MetroidRules.tick(state,0,33)}
        assertEquals(MetroidRules.FLOOR,state.side.y,0.01f)
        assertTrue(state.side.grounded)
        assertEquals(0,state.side.room)
    }

    @Test fun abilityAcquisitionRequiresRevisitingForestForUpperRoute() {
        var s=fresh().copy(side=SideRun(room=1,x=5f,y=7f,
            grounded=true,enemyHp=80))
        val noWing=MetroidRules.interact(s)
        assertEquals(1,noWing.side.room)
        s=s.copy(side=SideRun(room=2,x=19f,
            enemyHp=MetroidRules.maxEnemyHp(2)))
        s=MetroidRules.interact(s)
        assertTrue("double" in s.side.acquired)
        s=s.copy(side=s.side.copy(room=1,x=5f,y=7f,enemyHp=70))
        s=MetroidRules.interact(s)
        assertEquals(3,s.side.room)
        assertEquals(3,s.side.checkpoint)
        s=s.copy(side=s.side.copy(x=19f))
        s=MetroidRules.interact(s)
        assertTrue("dash" in s.side.acquired)
        s=s.copy(side=s.side.copy(room=2,x=23.5f,
            y=MetroidRules.FLOOR,enemyHp=MetroidRules.maxEnemyHp(2)))
        s=MetroidRules.tick(s,1,33)
        assertEquals(4,s.side.room)
        assertTrue(s.side.acquired.containsAll(listOf("double","dash")))
    }

    @Test fun foundryGateRefusesEntryUntilDashIsFound() {
        val locked=fresh().copy(side=SideRun(room=2,x=23.5f,
            enemyHp=120))
        val no=MetroidRules.tick(locked,1,33)
        assertEquals(2,no.side.room)
        assertTrue(no.side.message.contains("封印の門"))
        assertEquals(locked,MetroidRules.dash(locked))
        val ready=locked.copy(side=locked.side.copy(acquired=setOf("dash")))
        val entered=MetroidRules.tick(ready,1,33)
        assertEquals(4,entered.side.room)
    }

    @Test fun captureUsesOwnIdentityAndPreservesDungeonProgress() {
        val original=fresh()
        val foe=MetroidRules.enemy(0)
        val state=original.copy(
            captureOrbs=3,
            side=SideRun(room=0,x=13f,enemyHp=1))
        val captured=MetroidRules.capture(state,zeroRandom)
        assertEquals(2,captured.captureOrbs)
        assertEquals(1,captured.captures.size)
        assertEquals(foe.species,captured.captures.single().species)
        assertNotEquals(foe.id,captured.captures.single().id)
        assertTrue(captured.captures.single().extraSkillId.isNotBlank())
        assertEquals(0,captured.side.enemyHp)
        assertTrue(0 in captured.side.cleared)
        assertEquals(original.tapRun,captured.tapRun)
    }

    @Test fun bossKillPaysActualExpGoldAndCanDropRelic() {
        val original=fresh()
        val side=SideRun(room=5,x=17.2f,enemyHp=1)
        val state=original.copy(side=side)
        val victory=MetroidRules.attack(state,zeroRandom)
        assertEquals(0,victory.side.enemyHp)
        assertTrue(5 in victory.side.cleared)
        assertEquals(1,victory.bossWins)
        assertEquals(1,victory.bossRelics.size)
        assertTrue(victory.tapRun.gold>original.tapRun.gold)
        assertTrue(victory.companion.level>=original.companion.level)
        assertEquals(original.tapRun.floor,victory.tapRun.floor)
        assertEquals(original.tapRun.bestFloor,victory.tapRun.bestFloor)
    }

    @Test fun partySkillWorksInSideCombatButNotOutOfRange() {
        val original=fresh()
        val s=original.copy(side=SideRun(room=0,x=13f,enemyHp=100))
        val used=MetroidRules.useSkill(s,"hero",bonus=false,zeroRandom)
        assertTrue(used.side.skillCooldowns.containsKey("hero"))
        assertEquals(100,used.side.enemyHp) // Light spirit heals instead of damage.
        assertEquals(s,MetroidRules.useSkill(s.copy(side=s.side.copy(x=2f)),
            "hero",bonus=false,zeroRandom))
    }

    @Test fun deathRestoresHealthCheckpointButKeepsAbilitiesAndLoot() {
        val state=fresh().copy(
            playerHp=1,
            side=SideRun(room=4,x=11.5f,y=10f,enemyHp=100,
                checkpoint=3,acquired=setOf("double","dash"),
                cleared=setOf(0,1),hurtCooldownMs=0))
        val revived=MetroidRules.tick(state,0,33)
        assertEquals(3,revived.side.room)
        assertEquals(1,revived.side.deaths)
        assertEquals(TalentRules.maxHp(revived),revived.playerHp)
        assertTrue(revived.side.acquired.containsAll(listOf("double","dash")))
        assertEquals(setOf(0,1),revived.side.cleared)
        assertEquals(state.tapRun,revived.tapRun)
    }

    @Test fun twoModesCannotTickSimultaneouslyWithArena() {
        val state=fresh().copy(challenge=ChallengeState(
            mode=ChallengeMode.TOWER,opponent=MetroidRules.enemy(0),
            opponentHp=50))
        assertEquals(state,MetroidRules.tick(state,1,33))
        assertEquals(state,MetroidRules.jump(state))
        assertEquals(state,MetroidRules.attack(state,zeroRandom))
    }
}
