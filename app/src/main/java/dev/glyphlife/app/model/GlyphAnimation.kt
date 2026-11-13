package dev.glyphlife.app.model

/** A frame is a row-major 13 × 13 monochrome image. */
data class GlyphFrame(
    val pixels: List<Boolean>,
    val durationMs: Int = 350,
    /** Optional per-LED luminance from 64px monster art, absent in legacy frames. */
    val levels: List<Int>? = null
) {
    init {
        require(pixels.size == PixelArt.COUNT) { "A Glyph frame must contain 169 pixels" }
        require(durationMs in 100..2000) { "Duration must be between 100 and 2000 ms" }
        levels?.let { brightness ->
            require(brightness.size == 169 && brightness.all { it in 0..255 })
            require(brightness.withIndex().all { (i, v) ->
                v == 0 || GlyphMask.isPhysical(i)
            })
        }
    }
}

data class GlyphAnimation(
    val frames: List<GlyphFrame> = listOf(GlyphFrame(PixelArt.defaultPet())),
    val selected: Int = 0
) {
    init {
        require(frames.isNotEmpty() && frames.size <= 64) { "Animation must have 1..64 frames" }
        require(selected in frames.indices)
    }

    val current: GlyphFrame get() = frames[selected]

    fun choose(index: Int): GlyphAnimation =
        if (index in frames.indices) copy(selected = index) else this

    fun replace(pixels: List<Boolean>): GlyphAnimation = copy(
        frames = frames.toMutableList().apply {
            set(selected, current.copy(pixels = GlyphMask.normalize(pixels), levels = null))
        }
    )

    fun setDuration(duration: Int): GlyphAnimation = copy(
        frames = frames.toMutableList().apply {
            set(selected, current.copy(durationMs = duration.coerceIn(100, 2000)))
        }
    )

    fun addFrame(duplicate: Boolean = false): GlyphAnimation {
        if (frames.size >= 64) return this
        val newFrame = if (duplicate) current.copy(pixels = current.pixels.toList())
                       else GlyphFrame(PixelArt.empty(), current.durationMs)
        return copy(
            frames = frames.toMutableList().apply { add(selected + 1, newFrame) },
            selected = selected + 1
        )
    }

    fun deleteFrame(): GlyphAnimation {
        if (frames.size == 1) return this
        val next = frames.toMutableList().apply { removeAt(selected) }
        return copy(frames = next, selected = selected.coerceAtMost(next.lastIndex))
    }

    fun transform(op: (List<Boolean>) -> List<Boolean>): GlyphAnimation = replace(op(current.pixels))
}

object GlyphOps {
    private const val S = PixelArt.WIDTH
    private fun ix(x: Int, y: Int) = y * S + x

    fun set(p: List<Boolean>, index: Int, state: Boolean): List<Boolean> {
        require(p.size == PixelArt.COUNT && index in p.indices)
        if (!GlyphMask.isPhysical(index)) return p
        return p.toMutableList().apply { this[index] = state }
    }

    fun mirrorHorizontal(p: List<Boolean>): List<Boolean> =
        List(PixelArt.COUNT) { i -> p[ix(S - 1 - i % S, i / S)] }

    fun mirrorVertical(p: List<Boolean>): List<Boolean> =
        List(PixelArt.COUNT) { i -> p[ix(i % S, S - 1 - i / S)] }

    fun rotateRight(p: List<Boolean>): List<Boolean> =
        List(PixelArt.COUNT) { i -> p[ix(i / S, S - 1 - i % S)] }

    fun invert(p: List<Boolean>): List<Boolean> =
        p.mapIndexed { i, on -> GlyphMask.isPhysical(i) && !on }

    fun shift(p: List<Boolean>, dx: Int, dy: Int): List<Boolean> =
        List(PixelArt.COUNT) { i ->
            val sx = i % S - dx
            val sy = i / S - dy
            GlyphMask.isPhysical(i) && sx in 0 until S && sy in 0 until S &&
                p[ix(sx, sy)]
        }

    fun floodFill(p: List<Boolean>, index: Int, value: Boolean): List<Boolean> {
        require(p.size == PixelArt.COUNT && index in p.indices)
        if (!GlyphMask.isPhysical(index)) return p
        val source = p[index]
        if (source == value) return p
        val out = p.toMutableList()
        val queue = ArrayDeque<Int>()
        queue.add(index)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            if (!GlyphMask.isPhysical(i) || out[i] != source) continue
            out[i] = value
            val x = i % S
            val y = i / S
            if (x > 0) queue.add(i - 1)
            if (x < S - 1) queue.add(i + 1)
            if (y > 0) queue.add(i - S)
            if (y < S - 1) queue.add(i + S)
        }
        return out
    }

    fun encode(p: List<Boolean>): String {
        require(p.size == PixelArt.COUNT)
        return p.mapIndexed { i, on -> if (on && GlyphMask.isPhysical(i)) "1" else "0" }
            .joinToString("")
    }

    fun decode(raw: String): List<Boolean> {
        require(raw.length == PixelArt.COUNT && raw.all { it in "01" })
        return raw.mapIndexed { i, c -> c == '1' && GlyphMask.isPhysical(i) }
    }
}
