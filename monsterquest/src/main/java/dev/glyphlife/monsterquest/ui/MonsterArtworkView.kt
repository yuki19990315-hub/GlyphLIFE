package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.MonsterAnimation
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline
import kotlinx.coroutines.delay

/**
 * A genuinely native 64×64 animation master with 256×256 nearest-neighbour
 * display; runtime never upscales the 13×13 Glyph transfer bitmap.
 */
@Composable
fun MonsterArtworkView(
    monster: WildMonster,
    store: MonsterArtworkStore,
    revision: Int,
    modifier: Modifier = Modifier
) {
    val frames=remember(monster,store,revision) {
        val master=store.spriteFor(monster)
        MonsterAnimation.idle(master,monster).map { frame ->
            frame.durationMs to PixelAssetPipeline.display256(frame.sprite).asImageBitmap()
        }
    }
    var index by remember(monster.id,monster.stage,revision) { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        index=0
        while(true) {
            delay(frames[index].first.toLong())
            index=(index+1)%frames.size
        }
    }
    Image(
        bitmap=frames[index.coerceIn(frames.indices)].second,
        contentDescription="${monster.species}：64×64カラードット絵の待機アニメーション",
        modifier=modifier.clip(RoundedCornerShape(12.dp)),
        contentScale=ContentScale.Fit,
        filterQuality=FilterQuality.None
    )
}
