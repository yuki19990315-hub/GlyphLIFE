package dev.glyphlife.app.model

/**
 * Phone (4a) Pro: 169 logical positions but 137 physically populated LEDs.
 * Out-of-circle cells are deliberately uneditable and always dark.
 */
object GlyphMask {
    private val leftEdge = intArrayOf(4, 2, 1, 1, 0, 0, 0, 0, 0, 1, 1, 2, 4)

    fun isPhysical(index: Int): Boolean {
        if (index !in 0 until PixelArt.COUNT) return false
        val row = index / PixelArt.WIDTH
        val col = index % PixelArt.WIDTH
        return col in leftEdge[row]..(12 - leftEdge[row])
    }

    fun normalize(pixels: List<Boolean>): List<Boolean> {
        require(pixels.size == PixelArt.COUNT)
        return pixels.mapIndexed { i, on -> on && isPhysical(i) }
    }
}
