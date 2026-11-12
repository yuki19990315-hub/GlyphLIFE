package dev.glyphlife.monsterquest.pixel

import dev.glyphlife.monsterquest.model.MonsterArtFamilies
import dev.glyphlife.monsterquest.model.WildMonster

/**
 * Demo companion Luna: a distinct moonlit palette treatment of our authored
 * Raizel silhouette, not a generic oval generated from a UUID.
 *
 * Both characters share the same 64px hand-curated source family so the first
 * screen matches the bestiary's art direction. No external network or AI call.
 * This never applies to imported GlyphLIFE companions or other species.
 */
object FeaturedCompanionArt {
    fun find(monster: WildMonster): PixelSprite? {
        if (monster.artFamily != MonsterArtFamilies.LUNA ||
            monster.species != "精霊" || monster.element != "光") return null

        val base = FeaturedPixelArt.stageSprite(monster.stage)
        val colors = base.pixels()
        val moonlit = IntArray(colors.size) { i ->
            val c = colors[i]
            if ((c ushr 24) < 96) 0 else {
                val r = (c ushr 16) and 255
                val g = (c ushr 8) and 255
                val b = c and 255
                val lum = (r * 30 + g * 59 + b * 11) / 100
                val warmHighlight = r > b + 34 && g > b + 20
                val nr = if (warmHighlight) (lum + 52).coerceAtMost(255)
                    else (lum * 4 / 5 + 20).coerceAtMost(255)
                val ng = if (warmHighlight) (lum + 38).coerceAtMost(255)
                    else (lum * 4 / 5 + 18).coerceAtMost(255)
                val nb = if (warmHighlight) (lum + 23).coerceAtMost(255)
                    else (lum * 4 / 5 + 55).coerceAtMost(255)
                (0xff shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        return PixelSprite.fromArgb(64, 64, moonlit)
    }
}
