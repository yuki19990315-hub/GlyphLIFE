package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline

/**
 * Always draws high-resolution-looking COLOR pixel art in game screens.
 * Native master = 64x64; nearest-neighbour presentation = 256x256.
 * 13x13 binary Glyph data is never used as the game display fallback.
 */
@Composable
fun MonsterArtworkView(
    monster: WildMonster,
    store: MonsterArtworkStore,
    revision: Int,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(monster, store, revision) {
        store.load(monster.id,monster.stage)
            ?: PixelAssetPipeline.display256(MonsterDesign.sprite(monster))
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "${monster.species}：64×64ドット絵の256×256拡大表示",
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None
    )
}
