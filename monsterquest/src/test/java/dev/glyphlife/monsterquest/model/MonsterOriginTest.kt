package dev.glyphlife.monsterquest.model

import org.junit.Assert.*
import org.junit.Test

class MonsterOriginTest {
    @Test fun aiConditionsRecordExactPromptAndDesignSettings() {
        val actual=MonsterOrigin.ai("炎の翼を持つ黒い竜",false,"火","DRAGON","黒い翼","水晶の角")
        assertTrue(actual.contains("入力指示：炎の翼を持つ黒い竜"))
        assertTrue(actual.contains("参考写真：なし"))
        assertTrue(actual.contains("主モチーフ：黒い翼"))
        assertEquals("AI画像召喚", MonsterOrigin.label(MonsterOrigin.AI_IMAGE))
    }
    @Test fun fusionConditionsHaveBothParents() {
        val a=WildMonster("a","赤竜","火",25,4,stage=2)
        val b=WildMonster("b","氷狐","水",25,4)
        val text=MonsterOrigin.fusion(a,b)
        assertTrue(text.contains("赤竜（火属性・進化2）"))
        assertTrue(text.contains("氷狐（水属性・進化1）"))
        assertTrue(MonsterOrigin.label("").contains("記録なし"))
    }
}
