package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.pixel.GlyphProjector

/** Simulation of actual LED luminance; unconnected pixels are omitted. */
@Composable
fun MonsterBrightnessPreview(levels: List<Int>, modifier: Modifier = Modifier) {
    require(levels.size == 169 && levels.all { it in 0..255 })
    Canvas(
        modifier = modifier.aspectRatio(1f)
            .background(Color(0xFF10141B), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .semantics { contentDescription = "全身を13×13に縮小したモノクロLED光度プレビュー" }
    ) {
        val pitch = size.minDimension / 13f
        for (i in 0 until 169) {
            if (!GlyphProjector.isPhysical(i)) continue
            val x = i % 13
            val y = i / 13
            val level = levels[i] / 255f
            val gray = (0.08f + level * 0.92f).coerceIn(0f, 1f)
            drawCircle(
                color = Color(gray, gray, gray, 1f),
                radius = pitch * 0.39f,
                center = Offset((x + 0.5f) * pitch, (y + 0.5f) * pitch)
            )
        }
    }
}
