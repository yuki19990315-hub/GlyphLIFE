package dev.glyphlife.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.model.GlyphMask
import dev.glyphlife.app.model.PixelArt

/**
 * 13×13 grid using Canvas instead of 169 individual Compose nodes.
 * Tap and drag both work. Outside the 137 real LEDs the cells are inert.
 */
@Composable
fun PixelGrid(
    pixels: List<Boolean>,
    modifier: Modifier = Modifier,
    onClick: ((Int) -> Unit)? = null
) {
    require(pixels.size == PixelArt.COUNT)

    fun hit(position: Offset, width: Float, height: Float): Int? {
        val cellWidth = width / 13f
        val cellHeight = height / 13f
        if (position.x < 0f || position.y < 0f ||
            position.x >= width || position.y >= height) return null
        val x = (position.x / cellWidth).toInt().coerceIn(0, 12)
        val y = (position.y / cellHeight).toInt().coerceIn(0, 12)
        val index = y * 13 + x
        return index.takeIf { GlyphMask.isPhysical(it) }
    }

    val editorModifier = if (onClick == null) Modifier else {
        Modifier.pointerInput(onClick) {
            detectTapGestures(onTap = { point ->
                hit(point, size.width.toFloat(), size.height.toFloat())?.let(onClick)
            })
        }.pointerInput(onClick) {
            var previous = -1
            detectDragGestures(
                onDragStart = { point ->
                    previous = -1
                    hit(point, size.width.toFloat(), size.height.toFloat())?.let { index ->
                        previous = index
                        onClick(index)
                    }
                },
                onDrag = { change, _ ->
                    hit(change.position, size.width.toFloat(), size.height.toFloat())?.let { index ->
                        if (index != previous) {
                            previous = index
                            onClick(index)
                        }
                    }
                    change.consume()
                }
            )
        }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color(0xFF090B0B), RoundedCornerShape(14.dp))
            .padding(12.dp)
            .semantics { contentDescription = "Glyph 13×13 ドット絵プレビュー" }
            .then(editorModifier)
    ) {
        val stepX = size.width / 13f
        val stepY = size.height / 13f
        val gap = minOf(stepX, stepY) * 0.075f
        for (index in 0 until PixelArt.COUNT) {
            val x = index % 13
            val y = index / 13
            val color = when {
                !GlyphMask.isPhysical(index) -> Color(0xFF111414)
                pixels[index] -> Color(0xFFF6F6F0)
                else -> Color(0xFF303737)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x * stepX + gap, y * stepY + gap),
                size = Size(stepX - gap * 2f, stepY - gap * 2f),
                cornerRadius = CornerRadius(gap * 1.5f)
            )
        }
    }
}
