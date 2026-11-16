package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RoadGearRulesTest {
    private fun fresh()=QuestState(companion=Companion(id="hero",level=8))
        .copy(tapRun=TapRun(gold=1500))

    @Test fun twoGearSlotsAreIndependentAndPurchasesSpendCoinsOnce() {
        var s=fresh()
        assertEquals("twig_blade",RoadGearRules.worn(s,RoadGearSlot.WEAPON)?.id)
        assertEquals("traveler_coat",RoadGearRules.worn(s,RoadGearSlot.ARMOR)?.id)
        s=RoadGearRules.buy(s,"steel_blade")
        assertEquals(1400,s.tapRun.gold)
        assertTrue("steel_blade" in s.roadGearOwned)
        assertEquals(1400,RoadGearRules.buy(s,"steel_blade").tapRun.gold)
        s=RoadGearRules.equip(s,"steel_blade")
        assertEquals("steel_blade",s.roadWeapon)
        assertEquals("traveler_coat",s.roadArmor)
        assertEquals(5,RelicRules.stats(s).attack)
    }

    @Test fun oneSetUnlocksMultiplePassivesAndBothApplyToCombatStats() {
        var s=fresh()
        s=RoadGearRules.equip(RoadGearRules.buy(s,"steel_blade"),"steel_blade")
        s=RoadGearRules.equip(RoadGearRules.buy(s,"steel_guard"),"steel_guard")
        assertEquals(listOf("鉄壁の構え","鋼の連撃"),
            RoadGearRules.activeSkills(s).map{it.name})
        assertEquals(8,RelicRules.stats(s).attack)
        assertEquals(7,RelicRules.stats(s).defense)
        assertEquals(0,RelicRules.stats(s).charge)
    }

    @Test fun replacingOnlyArmorRemovesObsoleteSetPassives() {
        var s=fresh()
        for(id in listOf("storm_saber","storm_coat","steel_guard"))
            s=RoadGearRules.buy(s,id)
        s=RoadGearRules.equip(s,"storm_saber")
        s=RoadGearRules.equip(s,"storm_coat")
        assertEquals(2,RoadGearRules.activeSkills(s).size)
        assertEquals(1,RelicRules.stats(s).charge)
        assertEquals(15,RelicRules.stats(s).critical)
        s=RoadGearRules.equip(s,"steel_guard")
        assertEquals(2,RoadGearRules.activeSkills(s).size)
        assertEquals(0,RelicRules.stats(s).critical)
        assertEquals(1,RelicRules.stats(s).charge)
    }

    @Test fun cannotBuyOrEquipWithoutFundsOwnershipOrOutsidePrep() {
        val s=fresh().copy(tapRun=TapRun(gold=1))
        assertEquals(s,RoadGearRules.equip(s,"ember_blade"))
        val poor=RoadGearRules.buy(s,"ember_blade")
        assertEquals(1,poor.tapRun.gold)
        assertFalse("ember_blade" in poor.roadGearOwned)
        assertEquals(s,RoadGearRules.equip(s,"not-a-real-weapon"))
        val fighting=fresh().copy(tapRun=TapRun(phase=TapPhase.FIGHT,
            enemy=WildMonster("enemy","モコ","草",50,3)))
        assertEquals(fighting,RoadGearRules.buy(fighting,"steel_blade"))
        assertEquals(fighting,RoadGearRules.equip(fighting,"steel_blade"))
    }

    @Test fun forgedOrMismatchedItemCannotActivateSetBonus() {
        val fake=fresh().copy(roadWeapon="storm_saber",roadArmor="storm_coat")
        assertTrue(RoadGearRules.activeSkills(fake).isEmpty())
        assertEquals(0,RoadGearRules.stats(fake).attack)
        val wrongSlot=fake.copy(roadWeapon="traveler_coat")
        assertNull(RoadGearRules.worn(wrongSlot,RoadGearSlot.WEAPON))
    }

    @Test fun victoryHealingSetDoesNotCausePassiveTimeBasedRecovery() {
        var s=fresh().copy(playerHp=30)
        s=RoadGearRules.equip(RoadGearRules.buy(s,"moon_staff"),"moon_staff")
        s=RoadGearRules.equip(RoadGearRules.buy(s,"moon_cloak"),"moon_cloak")
        assertEquals(14,RelicRules.stats(s).heal)
        assertEquals(44,RelicRules.afterVictory(s,false).playerHp)
        assertEquals(30,MinuteRoadRules.tick(s,0,100,Random(1)).playerHp)
    }
}
