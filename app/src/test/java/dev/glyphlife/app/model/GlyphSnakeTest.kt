package dev.glyphlife.app.model

import org.junit.Assert.*
import org.junit.Test

class GlyphSnakeTest {
    @Test fun initialBodyAndFoodFit137LedMask() {
        repeat(40) { seed ->
            val snake = GlyphSnake.initial(seed)
            assertEquals(3, snake.body.size)
            assertTrue(snake.food !in snake.body)
            assertTrue(GlyphMask.isPhysical(snake.food))
            assertEquals(4, GlyphSnake.pixels(snake).count { it })
        }
    }

    @Test fun snakeMovesOneCellAndCannotReverseDirection() {
        val start = GlyphSnake.initial()
        val next = GlyphSnake.step(start, SnakeDirection.LEFT)
        assertEquals(start.body.first() + 1, next.body.first())
        assertEquals(SnakeDirection.RIGHT, next.direction)
    }

    @Test fun snakeCanTurnAndFoodDoesNotSpawnOnBody() {
        val start = GlyphSnake.initial().copy(food = 6 * 13 + 7)
        val eaten = GlyphSnake.step(start, SnakeDirection.RIGHT)
        assertEquals(4, eaten.body.size)
        assertEquals(1, eaten.score)
        assertTrue(eaten.food !in eaten.body)
        assertEquals(5, GlyphSnake.pixels(eaten).count { it })
    }

    @Test fun snakeCanDieAtPhysicalEdge() {
        val start = SnakeState(
            body = listOf(6 * 13 + 12, 6 * 13 + 11),
            direction = SnakeDirection.RIGHT,
            food = 4 * 13 + 6
        )
        assertFalse(GlyphSnake.step(start).alive)
    }

    @Test fun gameOverDoesNotAdvanceState() {
        val dead = GlyphSnake.initial().copy(alive = false)
        assertEquals(dead, GlyphSnake.step(dead))
    }

    @Test fun legalTailCellMoveDoesNotCountCollision() {
        val body = listOf(6 * 13 + 6, 6 * 13 + 7, 7 * 13 + 7, 7 * 13 + 6)
        val start = SnakeState(body, SnakeDirection.LEFT, 4 * 13 + 4)
        val moved = GlyphSnake.step(start, SnakeDirection.DOWN)
        assertTrue(moved.alive)
        assertEquals(7 * 13 + 6, moved.body.first())
    }
}
