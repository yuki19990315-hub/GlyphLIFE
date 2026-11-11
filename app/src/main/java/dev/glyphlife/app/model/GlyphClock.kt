package dev.glyphlife.app.model

/**
 * A tiny two-line clock plus a five-dot battery gauge for a 13×13 Glyph grid.
 *
 * Top: HH (y=1..5); bottom: MM (y=7..11);
 * centre: time separator; bottom edge: battery gauge (0 to 5 LEDs).
 * Uses only physically populated positions on Phone (4a) Pro.
 */
object GlyphClock {
    private val digits = arrayOf(
        arrayOf("111", "101", "101", "101", "111"), // 0
        arrayOf("010", "110", "010", "010", "111"), // 1
        arrayOf("111", "001", "111", "100", "111"), // 2
        arrayOf("111", "001", "111", "001", "111"), // 3
        arrayOf("101", "101", "111", "001", "001"), // 4
        arrayOf("111", "100", "111", "001", "111"), // 5
        arrayOf("111", "100", "111", "101", "111"), // 6
        arrayOf("111", "001", "001", "001", "001"), // 7
        arrayOf("111", "101", "111", "101", "111"), // 8
        arrayOf("111", "101", "111", "001", "111")  // 9
    )

    fun draw(hour: Int, minute: Int, batteryPercent: Int): List<Boolean> {
        require(hour in 0..23 && minute in 0..59)
        require(batteryPercent in 0..100)
        val out = BooleanArray(169)
        fun put(x: Int, y: Int) {
            val index = y * 13 + x
            if (GlyphMask.isPhysical(index)) out[index] = true
        }
        fun digit(number: Int, originX: Int, originY: Int) {
            digits[number].forEachIndexed { dy, line ->
                line.forEachIndexed { dx, bit ->
                    if (bit == '1') put(originX + dx, originY + dy)
                }
            }
        }
        digit(hour / 10, 3, 1)
        digit(hour % 10, 7, 1)
        digit(minute / 10, 3, 7)
        digit(minute % 10, 7, 7)
        put(6, 6)
        val bars = (batteryPercent * 5 / 100.0).toInt()
        repeat(bars) { put(4 + it, 12) }
        return out.toList()
    }
}
