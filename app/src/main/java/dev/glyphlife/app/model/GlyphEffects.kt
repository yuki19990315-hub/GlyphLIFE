package dev.glyphlife.app.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline procedural animations designed for the physical 137-LED Glyph Matrix.
 * No model, camera, microphone or network permission is needed.
 */
enum class GlyphEffect(val label: String, val description: String) {
    RAIN("雨", "降る雨粒としずくの軌跡"),
    TWINKLE("星空", "星がランダムにまたたく"),
    HEART("ハート", "鼓動するハート"),
    FIRE("炎", "立ち上る炎のゆらめき"),
    WAVE("波紋", "流れるサイン波の水面"),
    RADAR("レーダー", "円形に回転する光のスキャン"),
    METEOR("流星", "斜めに走る流れ星")
}

object GlyphEffects {
    private const val SIZE = 13
    private const val CENTER = 6.0
    private const val TAU = PI * 2

    fun generate(
        effect: GlyphEffect,
        seed: Int = 42,
        durationMs: Int = 170,
        frameCount: Int = 24
    ): GlyphAnimation {
        require(frameCount in 2..64)
        require(durationMs in 100..2000)
        return GlyphAnimation((0 until frameCount).map { frame ->
            GlyphFrame(draw(effect, seed, frame, frameCount), durationMs)
        })
    }

    private fun noise(x: Int, y: Int, seed: Int): Int {
        var h = seed xor (x * 374761393) xor (y * 668265263)
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    private fun draw(effect: GlyphEffect, seed: Int, frame: Int, total: Int): List<Boolean> {
        val pixels = BooleanArray(SIZE * SIZE)
        val phase = frame.toDouble() / total
        fun pixel(x: Int, y: Int, enabled: Boolean = true) {
            if (x !in 0..12 || y !in 0..12) return
            val i = y * SIZE + x
            if (GlyphMask.isPhysical(i) && enabled) pixels[i] = true
        }
        when (effect) {
            GlyphEffect.RAIN -> {
                repeat(16) { drop ->
                    val x = Math.floorMod(noise(drop, 1, seed), 13)
                    val start = Math.floorMod(noise(drop, 2, seed), 19)
                    val speed = 1 + Math.floorMod(noise(drop, 3, seed), 3)
                    val y = Math.floorMod(start + frame * speed, 18) - 3
                    pixel(x, y)
                    if (drop % 3 == 0) pixel(x, y - 1)
                }
            }
            GlyphEffect.TWINKLE -> {
                for (y in 0..12) for (x in 0..12) {
                    val h = noise(x, y, seed)
                    if (Math.floorMod(h, 9) == 0) {
                        val rate = 2 + Math.floorMod(h ushr 4, 5)
                        val lit = Math.floorMod(frame + (h ushr 8), rate) != 0
                        pixel(x, y, lit)
                    }
                }
            }
            GlyphEffect.HEART -> {
                // Implicit cardioid-like heart; shrink/grow for two beats per loop.
                val beat = 1 + 0.08 * sin(TAU * phase * 2) +
                    0.035 * sin(TAU * phase * 4)
                for (y in 0..12) for (x in 0..12) {
                    val u = (x - CENTER) / (4.7 * beat)
                    val v = (CENTER + 0.25 - y) / (4.5 * beat)
                    val a = u * u + v * v - 1
                    if (a * a * a - u * u * v * v * v <= 0.0) pixel(x, y)
                }
            }
            GlyphEffect.FIRE -> {
                val t = frame.toDouble() / total
                for (x in 1..11) {
                    val wave = sin((x + seed % 7) * 1.9 + t * TAU * 3)
                    val crest = 5 + (wave * 2).toInt()
                    for (y in 12 downTo crest) {
                        if (Math.floorMod(noise(x, y + frame, seed), 5) != 0) {
                            pixel(x, y)
                        }
                    }
                }
            }
            GlyphEffect.WAVE -> {
                for (x in 0..12) {
                    val y = (6.0 + sin(x * 0.65 - phase * TAU) * 2.8).toInt()
                    pixel(x, y)
                    pixel(x, y + 3)
                    if (frame % 4 < 2) pixel(x, y + 4)
                }
            }
            GlyphEffect.RADAR -> {
                val angle = phase * TAU
                val sx = sin(angle)
                val sy = -cos(angle)
                for (y in 0..12) for (x in 0..12) {
                    val dx = (x - CENTER)
                    val dy = (y - CENTER)
                    val radius = sqrt(dx * dx + dy * dy)
                    val parallel = dx * sx + dy * sy
                    val perpendicular = abs(dx * sy - dy * sx)
                    val ray = parallel >= 0 && perpendicular < 0.62
                    val circle = abs(radius - 5) < 0.46 && frame % 3 != 0
                    if (radius < 0.7 || ray || circle) pixel(x, y)
                }
            }
            GlyphEffect.METEOR -> {
                for (meteor in 0..2) {
                    val start = Math.floorMod(noise(meteor, 5, seed), 18) - 7
                    val head = Math.floorMod(frame + meteor * (total / 3) + start, 24) - 4
                    for (trail in 0..4) {
                        val x = head - trail
                        val y = (x + meteor * 3 + 2) % 13
                        // Use a diagonal path without wrap-around across the matrix.
                        if (x in 0..12 && y in 0..12) pixel(x, y)
                    }
                }
            }
        }
        return GlyphMask.normalize(pixels.toList())
    }
}
