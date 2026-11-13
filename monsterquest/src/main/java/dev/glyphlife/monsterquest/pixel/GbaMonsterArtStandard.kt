package dev.glyphlife.monsterquest.pixel

/**
 * GBA-inspired monster asset contract:
 * real 64x64 pixels, at most sixteen ARGB values INCLUDING transparency,
 * and a full-body alpha silhouette that does not touch the canvas edges.
 *
 * A failed audit is a factual diagnostic; it cannot certify artistic merit
 * (e.g. whether a creature is recognizable or resembles a reference sketch).
 */
object GbaMonsterArtStandard {
    const val SIZE = 64
    const val MAX_TOTAL_COLORS = 16
    const val MAX_OPAQUE_COLORS = 15

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    data class Audit(
        val is64: Boolean,
        val opaqueColors: Int,
        val totalColors: Int,
        val hasTransparency: Boolean,
        val hasPartialAlpha: Boolean,
        val opaqueBounds: Bounds?,
        val croppedAtEdge: Boolean
    ) {
        val compliant: Boolean get() = is64 &&
            totalColors <= MAX_TOTAL_COLORS && opaqueColors <= MAX_OPAQUE_COLORS &&
            hasTransparency && !hasPartialAlpha && opaqueBounds != null && !croppedAtEdge
    }

    fun audit(sprite: PixelSprite): Audit {
        val pixels = sprite.pixels()
        val bounds = bounds(sprite)
        val colors = pixels.toSet()
        val transparent = pixels.any { (it ushr 24) == 0 }
        val opaque = pixels.filter { (it ushr 24) != 0 }.toSet().size
        val partial = pixels.any {
            val alpha = it ushr 24
            alpha != 0 && alpha != 255
        }
        return Audit(
            is64 = sprite.width == SIZE && sprite.height == SIZE,
            opaqueColors = opaque,
            totalColors = colors.size,
            hasTransparency = transparent,
            hasPartialAlpha = partial,
            opaqueBounds = bounds,
            croppedAtEdge = bounds != null && (
                bounds.left == 0 || bounds.top == 0 ||
                    bounds.right == sprite.width - 1 || bounds.bottom == sprite.height - 1
                )
        )
    }

    fun bounds(sprite: PixelSprite): Bounds? {
        val p = sprite.pixels()
        var l = sprite.width; var t = sprite.height
        var r = -1; var b = -1
        for (y in 0 until sprite.height) for (x in 0 until sprite.width) {
            if ((p[y * sprite.width + x] ushr 24) == 0) continue
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
        }
        return if (r < l) null else Bounds(l, t, r, b)
    }

    /**
     * Keep the complete existing alpha silhouette inside a one-pixel safety
     * border. Nearest-neighbor sampling writes only real 64x64 grid pixels.
     * An entirely opaque bitmap is not touched: guessing where its background
     * ends would destroy user content; the audit reports it instead.
     */
    fun fitInside(sprite: PixelSprite): PixelSprite {
        require(sprite.width == SIZE && sprite.height == SIZE)
        val b = bounds(sprite) ?: return sprite
        if (b.left >= 1 && b.top >= 1 &&
            b.right <= SIZE - 2 && b.bottom <= SIZE - 2) return sprite
        if (b.width == SIZE && b.height == SIZE &&
            sprite.pixels().none { (it ushr 24) == 0 }) return sprite
        val scale = minOf(1.0,
            (SIZE - 2).toDouble() / b.width,
            (SIZE - 2).toDouble() / b.height)
        val dw = (b.width * scale).toInt().coerceIn(1, SIZE-2)
        val dh = (b.height * scale).toInt().coerceIn(1, SIZE-2)
        val x0 = (SIZE - dw) / 2
        val y0 = (SIZE - dh) / 2
        val out = IntArray(SIZE * SIZE)
        for (y in 0 until dh) for (x in 0 until dw) {
            val sx = b.left + ((x + 0.5) * b.width / dw).toInt()
                .coerceIn(0, b.width - 1)
            val sy = b.top + ((y + 0.5) * b.height / dh).toInt()
                .coerceIn(0, b.height - 1)
            out[(y+y0)*SIZE+(x+x0)] = sprite.pixel(sx,sy)
        }
        return PixelSprite.fromArgb(SIZE,SIZE,out)
    }

    /**
     * Normalize imported/overlaid artwork on display/export without rewriting
     * its original saved PNG; preserve exact colors when already compliant.
     * Cannot safely guess where an opaque photo background ends.
     */
    fun canonical(sprite: PixelSprite): PixelSprite {
        require(sprite.width == SIZE && sprite.height == SIZE)
        val fitted = fitInside(sprite)
        val p = fitted.pixels()
        val opaqueColors = p.filter { (it ushr 24) != 0 }.toSet().size
        val hasPartialAlpha = p.any { val a = it ushr 24; a != 0 && a != 255 }
        if (opaqueColors <= MAX_OPAQUE_COLORS && !hasPartialAlpha &&
            p.all { it == 0 || (it ushr 24) == 255 }) return fitted
        // Never modify the source PNG on disk; only this rendering copy.
        return PixelSprite.fromArgb(SIZE, SIZE, PixelAssetPipeline.paletteReduce(p))
    }
}
