package dev.glyphlife.monsterquest.model

/**
 * One source of truth for whether summoning is available.
 * UI and action handlers must check identical conditions, including
 * battle modes that do not set QuestState.inBattle immediately.
 */
object SummonAccess {
    fun commonBlock(state: QuestState, busy: Boolean): String? = when {
        busy -> "別の生成処理中です"
        RelicRules.isBattle(state) -> "戦闘・挑戦中は召喚できません"
        state.captures.size >= 100 -> "仲間が100体で満員です。合成して枠を空けてください"
        state.summonStones <= 0 -> "召喚結晶が0個です。探索やボス戦で獲得できます"
        else -> null
    }

    fun aiBlock(state: QuestState, busy: Boolean, apiKey: String): String? =
        commonBlock(state, busy) ?:
            if (apiKey.isBlank()) "AI召喚にはGemini APIキーが必要です。オフライン召喚はキー不要です"
            else null
}

/** Exactly one welcome crystal for a roster without any captured monsters. */
object StarterSummonGift {
    fun eligible(state: QuestState): Boolean =
        state.captures.isEmpty() && state.summonStones == 0
}
