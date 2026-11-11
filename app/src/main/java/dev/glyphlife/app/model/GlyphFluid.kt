package dev.glyphlife.app.model

/** Direction chosen by the user; tilt support can update this in real time. */
enum class FluidGravity { DOWN, UP, LEFT, RIGHT }

/**
 * Pixel-scale falling-water cellular automaton for Phone (4a) Pro's 137 LEDs.
 * The simulation is deterministic for a given state and tick, offline, and
 * conserves lit pixels unless the user explicitly adds/removes them.
 *
 * It is intentionally not a Navier-Stokes fluid solver. 13x13 pixels cannot
 * represent that level of detail, so we use a water-like discrete approximation.
 */
object GlyphFluid {
    fun initial(): List<Boolean> = MutableList(PixelArt.COUNT) { false }.apply {
        for (y in 2..5) for (x in 3..9) {
            val index = y * 13 + x
            if (GlyphMask.isPhysical(index) && (x + y) % 3 != 0) this[index] = true
        }
    }

    fun tap(pixels: List<Boolean>, index: Int): List<Boolean> {
        require(pixels.size == PixelArt.COUNT)
        if (!GlyphMask.isPhysical(index)) return pixels
        return pixels.toMutableList().apply { this[index] = !this[index] }
    }

    fun addDrop(pixels: List<Boolean>, frame: Int): List<Boolean> {
        require(pixels.size == PixelArt.COUNT)
        val x = 4 + (frame % 5)
        val y = 2
        val i = y * 13 + x
        return pixels.toMutableList().apply {
            if (GlyphMask.isPhysical(i)) this[i] = true
        }
    }

    fun step(
        pixels: List<Boolean>,
        gravity: FluidGravity = FluidGravity.DOWN,
        tick: Int = 0
    ): List<Boolean> {
        require(pixels.size == PixelArt.COUNT)
        val out = GlyphMask.normalize(pixels).toMutableList()
        val moved = BooleanArray(PixelArt.COUNT)

        val order = when (gravity) {
            FluidGravity.DOWN -> (168 downTo 0).toList()
            FluidGravity.UP -> (0..168).toList()
            FluidGravity.RIGHT -> (0..168).sortedByDescending { it % 13 }
            FluidGravity.LEFT -> (0..168).sortedBy { it % 13 }
        }
        val side = if (tick % 2 == 0) 1 else -1
        val offsets = when (gravity) {
            FluidGravity.DOWN -> listOf(
                0 to 1, side to 1, -side to 1, side to 0, -side to 0
            )
            FluidGravity.UP -> listOf(
                0 to -1, side to -1, -side to -1, side to 0, -side to 0
            )
            FluidGravity.LEFT -> listOf(
                -1 to 0, -1 to side, -1 to -side, 0 to side, 0 to -side
            )
            FluidGravity.RIGHT -> listOf(
                1 to 0, 1 to side, 1 to -side, 0 to side, 0 to -side
            )
        }
        for (index in order) {
            if (!out[index] || moved[index]) continue
            val x = index % 13
            val y = index / 13
            for ((dx, dy) in offsets) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0..12 || ny !in 0..12) continue
                val dest = ny * 13 + nx
                if (!GlyphMask.isPhysical(dest) || out[dest]) continue
                out[index] = false
                out[dest] = true
                moved[dest] = true
                break
            }
        }
        return out
    }
}
