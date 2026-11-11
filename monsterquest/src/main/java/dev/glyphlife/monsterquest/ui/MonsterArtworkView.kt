package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import dev.glyphlife.monsterquest.data.MonsterArtworkStore
import dev.glyphlife.monsterquest.model.WildMonster

/**
 * Separates full-resolution game concept art from the tiny 13×13 Glyph portrait.
 * Falls back to Glyph pixels for creatures without a stored illustration.
 */
@Composable
fun MonsterArtworkView(
    monster: WildMonster,
    store: MonsterArtworkStore,
    revision: Int,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(monster.id, store, revision) { store.load(monster.id) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "${monster.species}の全身立ち絵",
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
    } else {
        MonsterPortrait(
            pixels = MonsterArt.forWild(monster),
            element = monster.element,
            modifier = modifier
        )
    }
}
