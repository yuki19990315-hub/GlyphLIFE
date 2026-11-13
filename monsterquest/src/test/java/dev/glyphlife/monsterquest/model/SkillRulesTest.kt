package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SkillRulesTest {
    private val lucky=object:Random(){
        override fun nextBits(bitCount:Int):Int=0
    }
    private val enemy=WildMonster(
        id="target",species="古代の歯車王",element="雷",
        maxHp=220,attack=35,isBoss=true
    )
    private val beast=WildMonster(
        id="wolf",species="ライゼル",element="雷",bodyType="BEAST",
        level=10,maxHp=50,attack=20
    )
    private val golem=WildMonster(
        id="golem",species="鉱晶ゴーレム",element="水",
        bodyType="GOLEM",level=8,maxHp=90,attack=12
    )
    private val wasp=WildMonster(
        id="wasp",species="翠刃の虫騎士",element="草",
        bodyType="INSECTOID",level=10,maxHp=40,attack=10
    )
    private fun exploring()=QuestState(
        companion=Companion(id="hero",name="ルナ",element="光",level=10),
        captures=listOf(beast,golem,wasp),partyIds=listOf("wolf","golem","wasp"),
        tapRun=TapRun(floor=5,steps=7,phase=TapPhase.BOSS,
            enemy=enemy,enemyHp=200,attackTimerMs=3500),
        playerHp=50
    )

    @Test fun differentSpeciesHaveDistinctActiveSkills() {
        val s=exploring()
        val skills=SkillRules.party(s).map { SkillRules.forMonster(it).id }
        assertEquals(4,skills.toSet().size)
        assertEquals("moonlight",skills[0])
        assertEquals("thunderfang",skills[1])
        assertEquals("golem",skills[2])
        assertEquals("venom",skills[3])
        assertEquals(s,TapAdventure.useSkill(s,"not-an-owner",lucky))
    }

    @Test fun healingHasPerMonsterCooldownReducedOnlyByNormalTaps() {
        var s=exploring()
        s=TapAdventure.useSkill(s,"hero",lucky)
        assertEquals(75,s.playerHp) // Moonlight: 20 + level/2
        assertEquals(8,s.tapRun.skillCooldowns["hero"])
        assertEquals(s,TapAdventure.useSkill(s,"hero",lucky))
        val before=s.tapRun.attackTimerMs
        s=TapAdventure.tap(s,lucky)
        assertEquals(7,s.tapRun.skillCooldowns["hero"])
        assertEquals(before,s.tapRun.attackTimerMs)
        repeat(7) {s=TapAdventure.tap(s,lucky)}
        assertFalse("hero" in s.tapRun.skillCooldowns)
    }

    @Test fun golemBarrierReducesNextTimedAttackNotByTapping() {
        var s=exploring().copy(tapRun=exploring().tapRun.copy(
            phase=TapPhase.FIGHT,enemy=enemy.copy(isBoss=false),enemyHp=200,
            attackTimerMs=100))
        s=TapAdventure.useSkill(s,"golem",lucky)
        assertEquals(1,s.tapRun.guardHits)
        s=TapAdventure.tap(s,lucky)
        assertEquals(1,s.tapRun.guardHits)
        val after=TapAdventure.tick(s,100,lucky)
        assertEquals(0,after.tapRun.guardHits)
        assertEquals(40,after.playerHp) // 35 raw -> 10 after shield
    }

    @Test fun venomAddsDamageOnNextTwoNormalAttacks() {
        val s=exploring()
        val active=TapAdventure.useSkill(s,"wasp",lucky)
        assertEquals(2,active.tapRun.venomCharges)
        val hp=active.tapRun.enemyHp
        val next=TapAdventure.tap(active,lucky)
        assertEquals(1,next.tapRun.venomCharges)
        // Basic: 6 + Lv10/3 + 3 allies boost + 0 rng;
        // venom guarantees an extra 8 vs same tap without poison.
        val clean=active.copy(tapRun=active.tapRun.copy(venomCharges=0))
        val plain=TapAdventure.tap(clean,lucky)
        assertEquals(8,plain.tapRun.enemyHp-next.tapRun.enemyHp)
        assertTrue(next.tapRun.enemyHp<hp)
    }

    @Test fun skillsCanKillBossAndTriggerLootWithoutExtraNormalAttack() {
        val s=exploring().copy(tapRun=exploring().tapRun.copy(
            enemyHp=1,attackTimerMs=3800))
        val won=TapAdventure.useSkill(s,"wolf",lucky)
        assertEquals(TapPhase.WALK,won.tapRun.phase)
        assertEquals(6,won.tapRun.floor)
        assertEquals(2,won.summonStones)
        assertEquals(1,won.bossRelics.size)
        assertEquals(1,won.bossWins)
        assertTrue(won.tapRun.message.contains("ボス限定装備"))
    }

    @Test fun skillsWorkInArenaAndDoNotDamageExplorationHp() {
        val s=exploring().copy(
            challenge=ChallengeState(
                mode=ChallengeMode.ARENA,
                arenaWave=1,opponent=enemy.copy(isBoss=false),
                opponentHp=190,playerHp=70,attackTimerMs=3000)
        )
        val used=ChallengeRules.useSkill(s,"hero",lucky)
        assertEquals(95,used.challenge.playerHp)
        assertEquals(50,used.playerHp)
        assertEquals(8,used.challenge.skillCooldowns["hero"])
        val attacked=ChallengeRules.attack(used,lucky)
        assertEquals(7,attacked.challenge.skillCooldowns["hero"])
    }

    @Test fun bossesCanBeBeatenBySkillsInTowerWithoutDuplicateLoot() {
        val s=exploring().copy(
            tapRun=TapRun(floor=2),
            challenge=ChallengeState(
                mode=ChallengeMode.TOWER,towerFloor=5,
                opponent=enemy,opponentHp=1,playerHp=90,attackTimerMs=3400)
        )
        val won=ChallengeRules.useSkill(s,"wolf",lucky)
        assertEquals(6,won.challenge.towerFloor)
        assertEquals(1,won.bossRelics.size)
        assertEquals(2,won.summonStones)
        assertTrue(won.challenge.message.contains("ボス限定装備"))
    }
}
