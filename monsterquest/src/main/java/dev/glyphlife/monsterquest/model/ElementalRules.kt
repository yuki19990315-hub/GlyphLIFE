package dev.glyphlife.monsterquest.model

/** Small, legible elemental matchup that never soft-locks a party:
 * advantageous attacks are +25%; disadvantageous attacks deal 90%.
 * Neutral and matching elements keep normal damage. */
object ElementalRules {
    private val wins=setOf(
        "水" to "火","火" to "草","草" to "水",
        "雷" to "水","草" to "雷",
        "光" to "闇","闇" to "光"
    )

    fun percentage(attacker:String,defender:String):Int = when {
        attacker to defender in wins -> 125
        defender to attacker in wins -> 90
        else -> 100
    }

    fun label(attacker:String,defender:String):String = when(
        percentage(attacker,defender)) {
        125 -> "有利 +25%"
        90 -> "不利 -10%"
        else -> "等倍"
    }
}
