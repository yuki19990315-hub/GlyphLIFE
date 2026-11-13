package dev.glyphlife.app.model

/**
 * Optional 8-bit grayscale extension to the v1 animation JSON.
 * 169 LEDs x two hex characters; legacy boolean "pixels" is retained.
 */
object GlyphBrightnessCodec {
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
