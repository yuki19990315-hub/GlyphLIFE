package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.GbaMonsterArtStandard
import dev.glyphlife.monsterquest.pixel.MonsterDesign
import dev.glyphlife.monsterquest.pixel.PixelAssetPipeline

/**
 * Low-memory, static 64x64 comparison thumbnail. No animation frames or
 * 256x256 image buffers are allocated for each candidate.
 */
@Composable
fun MonsterStaticSprite(
    monster: WildMonster, modifier: Modifier = Modifier,
    store: MonsterArtworkStore? = null, revision: Int = 0
) {
    val sprite = remember(monster, store, revision) {
        val master = store?.spriteFor(monster) ?: MonsterDesign.sprite(monster)
        PixelAssetPipeline.toBitmap(
            GbaMonsterArtStandard.fitWithMargin(master,9)
        ).asImageBitmap()
    }
    Image(
        bitmap = sprite,
        contentDescription = "${monster.species}・属性${monster.element}・進化${monster.stage}の64×64ドット絵",
        modifier = modifier.aspectRatio(1f),
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None
    )
}
