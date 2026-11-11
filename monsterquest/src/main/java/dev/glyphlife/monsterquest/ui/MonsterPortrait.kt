package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.model.WildMonster
import kotlin.math.abs

/** Original deterministic fallback portrait for creatures without AI-generated pixels. */
object MonsterArt {
    fun forWild(monster: WildMonster): String {
        val raw = monster.pixels
        return if (raw.length == 169 && raw.all { it == '0' || it == '1' })
            raw else generate(monster.species, monster.element, monster.isBoss)
    }

    fun generate(species: String, element: String, boss: Boolean = false): String {
        val pixels = BooleanArray(13 * 13)
        val hash = (species + element).hashCode()
        fun put(x: Int, y: Int, on: Boolean = true) {
            if (x in 0..12 && y in 0..12) pixels[y * 13 + x] = on
        }
        fun oval(cx: Int, cy: Int, rx: Int, ry: Int) {
            for (y in (cy - ry)..(cy + ry)) {
                for (x in (cx - rx)..(cx + rx)) {
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx * ry * ry + dy * dy * rx * rx <= rx * rx * ry * ry) put(x, y)
                }
            }
        }

        oval(6, 7, if (boss) 4 else 3, if (boss) 4 else 3)
        when (abs(hash % 4)) {
            0 -> {
                // Horns
                put(4, 3); put(3, 2); put(3, 1)
                put(8, 3); put(9, 2); put(9, 1)
            }
            1 -> {
                // Fins
                for (n in 0..2) {
                    put(3 - n, 6 - n)
                    put(9 + n, 6 - n)
                }
            }
            2 -> {
                // Wide ears
                for (n in 0..2) {
                    put(4 - n, 3 - n)
                    put(8 + n, 3 - n)
                }
            }
            else -> {
                // Floating antennae
                put(4, 3); put(4, 2); put(3, 1)
                put(8, 3); put(8, 2); put(9, 1)
            }
        }
        if (boss) {
            for (n in 0..3) {
                put(2 - n, 7 - n); put(10 + n, 7 - n)
                put(2 - n, 8 + n); put(10 + n, 8 + n)
            }
            put(6, 1)
        }
        // Eye cutouts and facial expression.
        put(4, 7, false)
        put(8, 7, false)
        if ((hash and 1) == 0) {
            put(5, 9, false); put(6, 9, false); put(7, 9, false)
        } else {
            put(6, 9, false)
        }
        return pixels.joinToString("") { if (it) "1" else "0" }
    }
}

/** A reusable 13x13 Android Canvas renderer; no imported artwork is required. */
@Composable
fun MonsterPortrait(
    pixels: String,
    element: String,
    modifier: Modifier = Modifier
) {
    val active = when (element) {
        "火" -> Color(0xFFFF946B)
        "水" -> Color(0xFF74D4FF)
        "草" -> Color(0xFFB1E58A)
        "雷" -> Color(0xFFF5DA78)
        "光" -> Color(0xFFFFF4C0)
        "闇" -> Color(0xFFC2ACF0)
        else -> Color(0xFFE2EFF5)
    }
    val valid = pixels.length == 169 && pixels.all { it == '0' || it == '1' }
    val sprite = if (valid) pixels else "0".repeat(169)
    Canvas(
        modifier = modifier.aspectRatio(1f)
            .background(Color(0xFF151B24), RoundedCornerShape(16.dp))
            .padding(14.dp)
            .semantics { contentDescription = "13×13のモンスターのドット絵" }
    ) {
        val stepX = size.width / 13f
        val stepY = size.height / 13f
        val gap = minOf(stepX, stepY) * 0.09f
        for (i in 0 until 169) {
            val on = sprite[i] == '1'
            drawRoundRect(
                color = if (on) active else Color(0xFF252F3D),
                topLeft = Offset((i % 13) * stepX + gap, (i / 13) * stepY + gap),
                size = Size(stepX - gap * 2, stepY - gap * 2),
                cornerRadius = CornerRadius(gap)
            )
        }
    }
}
