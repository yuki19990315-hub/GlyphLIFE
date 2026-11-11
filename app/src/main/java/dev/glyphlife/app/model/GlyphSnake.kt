package dev.glyphlife.app.model

/**
 * Tiny offline snake game mapped onto Phone (4a) Pro's circular 137-pixel mask.
 * Pure Kotlin rules; suitable for unit testing and later Glyph streaming.
 */
enum class SnakeDirection(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0)
}

data class SnakeState(
    val body: List<Int>,
    val direction: SnakeDirection,
    val food: Int,
    val score: Int = 0,
    val alive: Boolean = true,
    val tick: Int = 0
) {
    init {
        require(body.isNotEmpty() && body.size <= 137)
        require(body.all(GlyphMask::isPhysical))
        require(body.distinct().size == body.size)
        require(food == -1 || GlyphMask.isPhysical(food))
    }
}

object GlyphSnake {
    private val physicalIndices = (0 until PixelArt.COUNT).filter(GlyphMask::isPhysical)

    fun initial(seed: Int = 1): SnakeState {
        val initialBody = listOf(6 * 13 + 6, 6 * 13 + 5, 6 * 13 + 4)
        return SnakeState(
            body = initialBody,
            direction = SnakeDirection.RIGHT,
            food = chooseFood(initialBody, seed)
        )
    }

    private fun chooseFood(occupied: List<Int>, seed: Int): Int {
        val free = physicalIndices.filter { it !in occupied }
        if (free.isEmpty()) return -1
        val index = Math.floorMod(seed.toLong() * 1103515245 + 12345, free.size.toLong()).toInt()
        return free[index]
    }

    fun step(
        state: SnakeState,
        requestedDirection: SnakeDirection = state.direction,
        seed: Int = 0
    ): SnakeState {
        if (!state.alive) return state
        val direction = if (
            requestedDirection.dx + state.direction.dx == 0 &&
            requestedDirection.dy + state.direction.dy == 0
        ) state.direction else requestedDirection

        val head = state.body.first()
        val nx = head % 13 + direction.dx
        val ny = head / 13 + direction.dy
        if (nx !in 0..12 || ny !in 0..12) return state.copy(alive = false)
        val next = ny * 13 + nx
        if (!GlyphMask.isPhysical(next)) return state.copy(alive = false)
        val eating = next == state.food
        // Moving into the cell vacated by the tail is legal when not eating.
        val occupied = if (eating) state.body else state.body.dropLast(1)
        if (next in occupied) return state.copy(alive = false)

        val body = (listOf(next) + state.body).take(
            state.body.size + if (eating) 1 else 0
        )
        val newFood = if (eating) chooseFood(body, seed + state.tick + 17)
                      else state.food
        return state.copy(
            body = body,
            direction = direction,
            food = newFood,
            score = state.score + if (eating) 1 else 0,
            tick = state.tick + 1,
            alive = newFood != -1
        )
    }

    fun pixels(state: SnakeState): List<Boolean> {
        val out = BooleanArray(PixelArt.COUNT)
        state.body.forEach { out[it] = true }
        if (state.food >= 0) out[state.food] = true
        return GlyphMask.normalize(out.toList())
    }
}
