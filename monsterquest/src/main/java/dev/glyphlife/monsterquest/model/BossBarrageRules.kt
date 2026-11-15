package dev.glyphlife.monsterquest.model

import kotlin.random.Random

/** A row of five real positions. Boss projectiles mark a landing position,
 * travel visibly through the arena, and hurt only on impact. The last slot
 * is close enough to the boss to attack; retreating sacrifices damage time. */
data class BossBarrageBullet(
    val lane:Int,
    val ageMs:Int=0,
    val flightMs:Int=1300,
    val style:Int=0
)

object BossBarrageRules {
    const val LANE_COUNT=5
    const val ATTACK_LANE=4
    const val FIRST_WAVE_MS=650
    const val MOVE_INTERVAL_MS=205

    fun laneX(lane:Int):Float=.12f+lane.coerceIn(0,ATTACK_LANE)*.11f

    fun move(state:QuestState,direction:Int):QuestState {
        val run=state.tapRun
        if(run.phase!=TapPhase.BOSS || direction !in listOf(-1,1))return state
        val next=(run.bossArenaLane+direction).coerceIn(0,ATTACK_LANE)
        if(next==run.bossArenaLane)return state
        return state.copy(tapRun=run.copy(
            bossArenaLane=next,
            message=if(next==ATTACK_LANE)
                "接近成功！ ▶を押すと攻撃できる。着弾前には後退しよう"
                else "位置 ${next+1}/$LANE_COUNT：着弾マークを見て前後へ移動！"))
    }

    /** Uses the journey's own timer, never the legacy/classic boss timer. */
    fun tick(state:QuestState,elapsedMs:Int,
             rng:Random=Random.Default):QuestState {
        val run=state.tapRun
        if(run.phase!=TapPhase.BOSS || run.enemy==null || elapsedMs<=0)return state
        val dt=elapsedMs.coerceIn(1,160)
        if(run.bossBullets.isNotEmpty()) {
            val advancing=run.bossBullets.map { it.copy(ageMs=it.ageMs+dt) }
            val landed=advancing.filter { it.ageMs>=it.flightMs }
            val flying=advancing.filter { it.ageMs<it.flightMs }
            val hitNow=!run.bossWaveTouched &&
                landed.any { it.lane==run.bossArenaLane }
            val touched=run.bossWaveTouched || hitNow
            val completed=flying.isEmpty()
            val updatedRun=run.copy(
                bossBullets=flying,
                bossWaveTouched=if(completed)false else touched,
                bossWaveCooldownMs=if(completed)
                    (if(run.enemyHp<=run.enemy.maxHp/2)1050 else 1520)
                    else run.bossWaveCooldownMs,
                bossVolleysDodged=run.bossVolleysDodged+
                    (if(completed && !touched)1 else 0),
                weakpointTaps=if(completed && !touched)3 else run.weakpointTaps,
                message=when {
                    hitNow -> "被弾！ 前後に移動して着弾地点を避けよう"
                    completed && !touched -> "PERFECT DODGE！ 弱点露出：攻撃3倍＋奥義30%"
                    else -> run.message
                }
            )
            val updated=state.copy(
                tapRun=updatedRun,
                journey=if(completed && !touched)state.journey.copy(
                    burstGauge=(state.journey.burstGauge+30)
                        .coerceAtMost(JourneyRules.BURST_COST)) else state.journey)
            if(!hitNow)return updated
            val enraged=run.enemyHp<=run.enemy.maxHp/2
            val raw=(run.enemy.attack+(if(enraged)8 else 0)+
                rng.nextInt(4)-state.defenseUpgrade-
                RelicRules.stats(state).defense-
                TalentRules.teamStats(state).defense).coerceAtLeast(1)
            val harm=if(run.guardHits>0)(raw*3/10).coerceAtLeast(1) else raw
            return TapAdventure.barrageHit(updated,harm)
        }
        val remaining=(run.bossWaveCooldownMs-dt).coerceAtLeast(0)
        if(remaining>0)return state.copy(tapRun=run.copy(bossWaveCooldownMs=remaining))
        val enraged=run.enemyHp<=run.enemy.maxHp/2
        // Every pattern guarantees one or more safe positions. A simple
        // seeded shuffle varies the safe lane without unfair full-row hits.
        val pattern=run.bossWaveIndex%3
        val threatened=when {
            enraged -> {
                val safe=rng.nextInt(LANE_COUNT)
                (0 until LANE_COUNT).filter{it!=safe}
            }
            pattern==1 -> {
                // A readable three-shot sweep; two adjacent safe positions.
                val start=rng.nextInt(LANE_COUNT-2)
                (start..start+2).toList()
            }
            else -> {
                val safe=rng.nextInt(LANE_COUNT)
                (0 until LANE_COUNT).filter{it!=safe}
                    .shuffled(rng).take(if(pattern==0)2 else 3).sorted()
            }
        }
        val travel=if(enraged)1100 else 1400
        val missiles=threatened.mapIndexed { index,lane ->
            BossBarrageBullet(lane=lane,
                ageMs=if(pattern==1)-index*135 else 0,
                flightMs=travel,style=pattern)
        }
        return state.copy(tapRun=run.copy(
            bossBullets=missiles,bossWaveIndex=run.bossWaveIndex+1,
            bossWaveTouched=false,bossWaveCooldownMs=0,
            message=if(enraged)"⚠ 怒りの弾幕！ 着弾マークのない地点へ！"
                else "⚠ ボスの弾幕！ 前後に移動して安全な位置へ！"))
    }
}
