package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.GbaMonsterArtStandard
import dev.glyphlife.monsterquest.pixel.BossRelicPixels
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
    modifier: Modifier = Modifier,
    /** Authored assets face right; enemies are mirrored to face left. */
    facingRight: Boolean = true,
    /** Overlay is only visual; source and native PNG stay unmodified. */
    bossRelicKind:String?=null
) {
    val frames=remember(monster,store,revision,bossRelicKind) {
        // Source art, including imported PNGs, might have wings or tails
        // touching the original edge. Fit the FULL opaque silhouette before
        // animation, then fit again after the boss-relic overlay. Never
        // overwrite user-owned source files.
        val master=GbaMonsterArtStandard.fitWithMargin(
            store.spriteFor(monster),9)
        MonsterAnimation.idle(master,monster).map { frame ->
            val equipped=BossRelicPixels.compose(frame.sprite,bossRelicKind)
            frame.durationMs to PixelAssetPipeline.display256(
                GbaMonsterArtStandard.fitWithMargin(equipped,8)
            ).asImageBitmap()
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
        modifier=modifier.graphicsLayer { scaleX = if(facingRight) 1f else -1f },
        contentScale=ContentScale.Fit,
        filterQuality=FilterQuality.None
    )
}
