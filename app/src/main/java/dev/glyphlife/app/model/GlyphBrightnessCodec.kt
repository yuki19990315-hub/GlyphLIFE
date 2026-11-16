package dev.glyphlife.app.model

/**
 * Optional 8-bit grayscale extension to the v1 animation JSON.
 * 169 LEDs x two hex characters; legacy boolean "pixels" is retained.
 */
object GlyphBrightnessCodec {
    /** Use the same per-LED brightness for display and PNG export.
     * Imported grayscale animation levels take precedence over the legacy
     * boolean pixel preview; LEDs outside the physical matrix remain dark. */
    fun intensity(frame: GlyphFrame, index: Int): Int {
        require(index in 0 until 169)
        if (!GlyphMask.isPhysical(index)) return 0
        return frame.levels?.get(index) ?: if (frame.pixels[index]) 255 else 0
    }

    fun encode(levels: List<Int>): String {
        require(levels.size == 169 && levels.all { it in 0..255 })
        require(levels.withIndex().all { (i, v) -> v == 0 || GlyphMask.isPhysical(i) })
        return levels.joinToString("") { it.toString(16).padStart(2, '0') }
    }

    fun decode(hex: String): List<Int> {
        require(hex.length == 338 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
        val levels = List(169) { i -> hex.substring(i*2,i*2+2).toInt(16) }
        require(levels.withIndex().all { (i, v) -> v == 0 || GlyphMask.isPhysical(i) })
        return levels
    }
}
