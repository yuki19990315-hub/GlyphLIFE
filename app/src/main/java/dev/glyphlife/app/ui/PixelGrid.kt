package dev.glyphlife.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.glyphlife.app.model.PixelArt

@Composable
fun PixelGrid(
    pixels: List<Boolean>,
    modifier: Modifier = Modifier,
    onClick: ((Int) -> Unit)? = null
) {
    require(pixels.size == PixelArt.COUNT)
    Column(
        modifier = modifier.background(Color(0xFF090B0B), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (y in 0 until PixelArt.HEIGHT) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (x in 0 until PixelArt.WIDTH) {
                    val index = y * PixelArt.WIDTH + x
                    val base = Modifier.weight(1f).aspectRatio(1f)
                        .background(
                            if (pixels[index]) Color(0xFFF7F7F0) else Color(0xFF303737),
                            RoundedCornerShape(2.dp)
                        )
                    Spacer(modifier = if (onClick != null) base.clickable { onClick(index) } else base)
                }
            }
        }
    }
}
