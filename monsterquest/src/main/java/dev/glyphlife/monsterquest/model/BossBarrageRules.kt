package dev.glyphlife.monsterquest.model

import kotlin.math.abs
import kotlin.random.Random

/** A projectile has a continuous landing point. Ground shots are jumped;
 * high shots fly over a grounded hero but punish mistimed jumps. */
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
    const val JUMP_DURATION_MS=900
    const val JUMP_COOLDOWN_MS=250
    const val FIRST_WAVE_MS=700
    const val HIT_RADIUS=9

    fun screenX(position:Int):Float=.10f+
        position.coerceIn(MIN_X,MAX_X)*.54f/100f

    /** 0 on the ground, 1 at the top of a symmetrical jump. */
    fun jumpHeight(run:TapRun):Float {
        if(run.bossJumpMs<=0)return 0f
        val t=1f-run.bossJumpMs.toFloat()/JUMP_DURATION_MS
        return (4f*t*(1f-t)).coerceIn(0f,1f)
    }

    fun jump(state:QuestState):QuestState {
        val run=state.tapRun
        if(run.phase!=TapPhase.BOSS || run.bossJumpMs>0 ||
            run.bossJumpCooldownMs>0)return state
        return state.copy(tapRun=run.copy(
            bossJumpMs=JUMP_DURATION_MS,
            message="ジャンプ！ 地上弾を飛び越えよう（空中弾に注意）"))
    }

    fun move(state:QuestState,direction:Int):QuestState {
        val run=state.tapRun
        if(run.phase!=TapPhase.BOSS || direction !in listOf(-1,1))return state
        val next=(run.bossPosition+direction*MOVE_STEP).coerceIn(MIN_X,MAX_X)
        if(next==run.bossPosition)return state
        return state.copy(tapRun=run.copy(
            bossPosition=next,
            message=if(next>=ATTACK_X)
                "攻撃射程！ ▶で攻撃、◀で後退、↑でジャンプ"
                else "前進・後退・ジャンプで弾幕を避けよう！"))
    }

    private fun nextJump(run:TapRun,dt:Int):TapRun {
        val remaining=(run.bossJumpMs-dt).coerceAtLeast(0)
        val cooldown=when {
            run.bossJumpMs>0 && remaining==0 -> JUMP_COOLDOWN_MS
            else -> (run.bossJumpCooldownMs-dt).coerceAtLeast(0)
        }
        return run.copy(bossJumpMs=remaining,bossJumpCooldownMs=cooldown)
    }

    /** The timer runs only on the visible exploration tab. Every projectile
     * collides at its own arrival time and can be dodged by movement or height. */
    fun tick(state:QuestState,elapsedMs:Int,
             rng:Random=Random.Default):QuestState {
        val original=state.tapRun
        if(original.phase!=TapPhase.BOSS || original.enemy==null ||
            elapsedMs<=0)return state
        val dt=elapsedMs.coerceIn(1,160)
        val run=nextJump(original,dt)
        val airborne=jumpHeight(run)>.35f
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
                    (if(run.enemyHp<=run.enemy.maxHp/2)900 else 1350)
                    else run.bossWaveCooldownMs,
                bossVolleysDodged=run.bossVolleysDodged+
                    (if(completed && !touched)1 else 0),
                weakpointTaps=if(completed && !touched)3 else run.weakpointTaps,
                message=when {
                    hitNow -> "被弾！ 赤はジャンプ、青は着地・移動で避けよう"
                    completed && !touched -> "弾幕回避！ 弱点露出：攻撃3倍＋奥義30%"
                    else -> run.message
                })
            val next=state.copy(
                tapRun=updated,
                journey=if(completed && !touched)state.journey.copy(
                    burstGauge=(state.journey.burstGauge+30)
                        .coerceAtMost(JourneyRules.BURST_COST)) else state.journey)
            if(!hitNow)return next
            val enraged=run.enemyHp<=run.enemy.maxHp/2
            val raw=(run.enemy.attack+(if(enraged)8 else 0)+
                rng.nextInt(4)-state.defenseUpgrade-
                RelicRules.stats(state).defense-
                TalentRules.teamStats(state).defense).coerceAtLeast(1)
            val harm=if(run.guardHits>0)(raw*3/10).coerceAtLeast(1) else raw
            return TapAdventure.barrageHit(next,harm)
        }
        val remaining=(run.bossWaveCooldownMs-dt).coerceAtLeast(0)
        if(remaining>0)return state.copy(tapRun=run.copy(
            bossWaveCooldownMs=remaining))
        val enraged=run.enemyHp<=run.enemy.maxHp/2
        val pattern=run.bossWaveIndex%3
        val aimed=run.bossPosition
        // Recognizable bullet silhouettes: aimed ground burst, overhead
        // crossing shots, staggered ground sweep. Each attack can be avoided.
        val targets:List<Pair<Int,Boolean>>=when(pattern) {
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
                "⚠ 怒りの弾幕！ 赤はジャンプ、青は着地で回避！"
                else "⚠ 着弾予告！ 移動かジャンプで避けよう！"))
    }
}
