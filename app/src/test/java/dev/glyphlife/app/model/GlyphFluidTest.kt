package dev.glyphlife.app.model

import org.junit.Assert.*
import org.junit.Test

class GlyphFluidTest {
    @Test fun simulationConservesWaterAndRespectsPhysicalMask() {
        for (direction in FluidGravity.entries) {
            var field = GlyphFluid.initial()
            val count = field.count { it }
            assertTrue(count > 0)
            repeat(160) { tick ->
                field = GlyphFluid.step(field, direction, tick)
                assertEquals(count, field.count { it })
                field.forEachIndexed { index, on ->
                    if (on) assertTrue(GlyphMask.isPhysical(index))
                }
            }
        }
    }

    @Test fun fallDownMovesSingleParticleDownOneCell() {
        val origin = 5 * 13 + 6
        val field = GlyphOps.set(PixelArt.empty(), origin, true)
        val next = GlyphFluid.step(field, FluidGravity.DOWN, 0)
        assertFalse(next[origin])
        assertTrue(next[origin + 13])
    }

    @Test fun topBoundaryAndCornersAreInert() {
        val grid = GlyphFluid.initial()
        assertEquals(grid, GlyphFluid.tap(grid, 0))
        assertEquals(grid, GlyphFluid.tap(grid, 168))
        var all = PixelArt.empty()
        repeat(100) { all = GlyphFluid.addDrop(all, it) }
        assertTrue(all.count { it } <= 5)
    }

    @Test fun gravityLeftMovesOneCell() {
        val origin = 6 * 13 + 6
        val field = GlyphOps.set(PixelArt.empty(), origin, true)
        val next = GlyphFluid.step(field, FluidGravity.LEFT)
        assertTrue(next[origin - 1])
    }
}
