package dev.glyphlife.monsterquest.model

import kotlin.math.abs
import kotlin.random.Random

/** A projectile has a continuous landing point. Hopping happens automatically
 * during movement; there is no separate jump input. */
data class BossBarrageBullet(
    val targetX:Int,
    val ageMs:Int=0,
    val flightMs:Int=1250,
    val style:Int=0,
    val airborne:Boolean=false
)

/** Small horizontal action arena, using the existing adventure reward/death
 * flow. No discrete positions, virtual lane switch or platformer hazards. */
object BossBarrageRules {
    const val MIN_X=8
    const val MAX_X=82
    const val START_X=28
    const val ATTACK_X=72
    const val MOVE_STEP=6
    const val MOVE_INTERVAL_MS=100
    const val HOP_DURATION_MS=360
    const val FIRST_WAVE_MS=700
    const val HIT_RADIUS=9

    fun screenX(position:Int):Float=.10f+
        position.coerceIn(MIN_X,MAX_X)*.54f/100f

    /** Shared light-hearted hop for corridor travel in either direction. */
    fun travelHop(travelMs:Int,direction:Int):Float {
        if(direction==0)return 0f
        val t=(travelMs.coerceAtLeast(0)%380).toFloat()/380f
        return (4f*t*(1f-t)).coerceIn(0f,1f)
    }

    /** 0 on the ground, 1 at the top of each automatic running hop. */
    fun hopHeight(run:TapRun):Float {
        if(run.bossHopMs<=0)return 0f
        val t=1f-run.bossHopMs.toFloat()/HOP_DURATION_MS
        return (4f*t*(1f-t)).coerceIn(0f,1f)
    }

    fun move(state:QuestState,direction:Int):QuestState {
        val run=state.tapRun
        if(run.phase!=TapPhase.BOSS || direction !in listOf(-1,1))return state
        val next=(run.bossPosition+direction*MOVE_STEP).coerceIn(MIN_X,MAX_X)
        if(next==run.bossPosition)return state
        return state.copy(tapRun=run.copy(
            bossPosition=next,
            bossHopMs=if(run.bossHopMs<=MOVE_INTERVAL_MS)HOP_DURATION_MS
                else run.bossHopMs,
            message=if(next>=ATTACK_X)
                "攻撃射程！ ▶で攻撃、◀で跳ねながら後退"
                else "◀▶でぴょんぴょん移動！ 弾の着弾場所を避けよう"))
    }

    /** The timer runs only on the visible exploration tab. Every projectile
     * collides at its own arrival time and can be dodged by movement or height. */
    fun tick(state:QuestState,elapsedMs:Int,
             rng:Random=Random.Default):QuestState {
        val original=state.tapRun
        if(original.phase!=TapPhase.BOSS || elapsedMs<=0)return state
        val foe=original.enemy ?: return state
        val dt=elapsedMs.coerceIn(1,160)
        val run=original.copy(bossHopMs=(original.bossHopMs-dt).coerceAtLeast(0))
        val airborne=hopHeight(run)>.35f
        if(run.bossBullets.isNotEmpty()) {
            val advancing=run.bossBullets.map {it.copy(ageMs=it.ageMs+dt)}
            val landed=advancing.filter {it.ageMs>=it.flightMs}
            val flying=advancing.filter {it.ageMs<it.flightMs}
            val hitNow=!run.bossWaveTouched && landed.any { shot ->
                abs(shot.targetX-run.bossPosition)<=HIT_RADIUS &&
                    (shot.airborne==airborne)
            }
            val touched=run.bossWaveTouched || hitNow
            val completed=flying.isEmpty()
            val updated=run.copy(
                bossBullets=flying,
                bossWaveTouched=if(completed)false else touched,
                bossWaveCooldownMs=if(completed)
                    (if(run.enemyHp<=foe.maxHp/2)900 else 1350)
                    else run.bossWaveCooldownMs,
                bossVolleysDodged=run.bossVolleysDodged+
                    (if(completed && !touched)1 else 0),
                weakpointTaps=if(completed && !touched)3 else run.weakpointTaps,
                message=when {
                    hitNow -> "被弾！ 前後に跳ねて着弾場所から離れよう"
                    completed && !touched -> "弾幕回避！ 弱点露出：攻撃3倍＋奥義30%"
                    else -> run.message
                })
            val next=state.copy(
                tapRun=updated,
                journey=if(completed && !touched)state.journey.copy(
                    burstGauge=(state.journey.burstGauge+30)
                        .coerceAtMost(JourneyRules.BURST_COST)) else state.journey)
            if(!hitNow)return next
            val enraged=run.enemyHp<=foe.maxHp/2
            val raw=(foe.attack+(if(enraged)8 else 0)+
                rng.nextInt(4)-state.defenseUpgrade-
                RelicRules.stats(state).defense-
                TalentRules.teamStats(state).defense).coerceAtLeast(1)
            val harm=if(run.guardHits>0)(raw*3/10).coerceAtLeast(1) else raw
            return TapAdventure.barrageHit(next,harm)
        }
        val remaining=(run.bossWaveCooldownMs-dt).coerceAtLeast(0)
        if(remaining>0)return state.copy(tapRun=run.copy(
            bossWaveCooldownMs=remaining))
        val enraged=run.enemyHp<=foe.maxHp/2
        val pattern=run.bossWaveIndex%3
        val aimed=run.bossPosition
        // Recognizable bullet silhouettes: aimed ground burst, overhead
        // crossing shots, staggered ground sweep. Each attack can be avoided.
        val targets: List<Pair<Int,Boolean>> = when(pattern) {
            0 -> listOf(aimed to false,
                (aimed+25).coerceAtMost(MAX_X) to false)
            1 -> listOf(
                20 to true,54 to true,
                (aimed-16).coerceAtLeast(MIN_X) to false)
            else -> listOf(18 to false,43 to false,68 to false)
        } + (if(enraged)listOf(aimed to false) else emptyList())
        val flight=if(enraged)1050 else 1350
        val shots=targets.mapIndexed {index,(target,high)->
            BossBarrageBullet(targetX=target.coerceIn(MIN_X,MAX_X),
                ageMs=if(pattern==2) -index*145 else -index*75,
                flightMs=flight,style=pattern,airborne=high)
        }
        return state.copy(tapRun=run.copy(
            bossBullets=shots,bossWaveIndex=run.bossWaveIndex+1,
            bossWaveTouched=false,bossWaveCooldownMs=0,
            message=if(enraged)
                "⚠ 怒りの弾幕！ 前後に跳ねて着弾場所を避けよう！"
                else "⚠ 着弾予告！ ◀▶でぴょんぴょん移動して避けよう！"))
    }
}
