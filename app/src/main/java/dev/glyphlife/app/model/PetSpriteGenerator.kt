package dev.glyphlife.app.model

/**
 * Original procedural pet sprites for the 137-LED circular Glyph shape.
 * This is deliberately deterministic and works offline, without claiming AI.
 */
object PetSpriteGenerator {
    fun stage(level: Int): Int = when {
        level >= 12 -> 3
        level >= 5 -> 2
        else -> 1
    }

    fun stageName(level: Int): String = when (stage(level)) {
        1 -> "幼体"
        2 -> "成長体"
        else -> "進化体"
    }

    fun create(pet: PetState): GlyphAnimation = GlyphAnimation(
        frames = listOf(
            GlyphFrame(draw(pet, 0), 650),
            GlyphFrame(draw(pet, 1), 120),
            GlyphFrame(draw(pet, 2), 350)
        )
    )

    private fun draw(pet: PetState, pose: Int): List<Boolean> {
        val pixels = BooleanArray(PixelArt.COUNT)
        val dy = if (pose == 2) -1 else 0
        val size = stage(pet.level)

        fun put(x: Int, y: Int, value: Boolean = true) {
            if (x !in 0..12 || y !in 0..12) return
            val index = y * 13 + x
            if (GlyphMask.isPhysical(index)) pixels[index] = value
        }

        fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int) {
            for (y in (cy - ry)..(cy + ry)) for (x in (cx - rx)..(cx + rx)) {
                val dx = x - cx
                val dy2 = y - cy
                if (dx * dx * ry * ry + dy2 * dy2 * rx * rx <= rx * rx * ry * ry) {
                    put(x, y)
                }
            }
        }

        when (pet.species) {
            "ドラゴン" -> {
                ellipse(6, 7 + dy, 3, 3)
                for (y in 5 + dy..8 + dy) {
                    put(2, y); put(10, y)
                    if (y <= 6 + dy) { put(1, y); put(11, y) }
                }
                put(4, 3 + dy); put(5, 2 + dy)
                put(8, 3 + dy); put(7, 2 + dy)
                put(6, 11 + dy)
            }
            "キツネ" -> {
                ellipse(6, 7 + dy, 4, 3)
                for (n in 0..2) {
                    put(3 + n, 4 - n + dy)
                    put(9 - n, 4 - n + dy)
                }
                put(10, 9 + dy); put(11, 8 + dy)
            }
            "ロボット" -> {
                for (y in 5 + dy..9 + dy) for (x in 3..9) put(x, y)
                for (x in 4..8) put(x, 4 + dy)
                put(6, 3 + dy); put(6, 2 + dy)
                for (y in 6 + dy..8 + dy) { put(2, y); put(10, y) }
            }
            "精霊" -> {
                ellipse(6, 7 + dy, 3, 4)
                put(2, 5 + dy); put(10, 5 + dy)
                put(3, 2 + dy); put(9, 2 + dy)
                put(6, 1 + dy)
            }
            else -> {
                ellipse(6, 8 + dy, 4, 3)
                for (x in 4..8) put(x, 4 + dy)
                put(3, 5 + dy); put(9, 5 + dy)
            }
        }

        // Growth adds increasingly elaborate but deterministic ornaments.
        if (size >= 2) {
            put(3, 2 + dy); put(9, 2 + dy)
            put(6, 1 + dy)
        }
        if (size >= 3) {
            put(2, 4 + dy); put(10, 4 + dy)
            put(1, 5 + dy); put(11, 5 + dy)
            put(5, 1 + dy); put(7, 1 + dy)
        }

        // Expression: low energy closes the eyes; playback's second frame blinks.
        val blink = pose == 1 || pet.energy < 25
        if (blink) {
            put(4, 7 + dy, false); put(8, 7 + dy, false)
            put(5, 7 + dy, false); put(9, 7 + dy, false)
        } else {
            put(4, 7 + dy, false); put(8, 7 + dy, false)
            put(4, 8 + dy, false); put(8, 8 + dy, false)
        }
        put(6, 9 + dy, false)
        if (pet.happiness < 30) {
            put(5, 10 + dy, false); put(7, 10 + dy, false)
        } else {
            put(5, 9 + dy, false); put(7, 9 + dy, false)
        }
        return GlyphMask.normalize(pixels.toList())
    }
}
