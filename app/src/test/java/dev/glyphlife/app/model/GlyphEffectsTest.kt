package dev.glyphlife.app.model

import org.junit.Assert.*
import org.junit.Test

class GlyphEffectsTest {
    @Test fun allEffectsAreFrameBoundedAndRespectPhysicalMask() {
        for (effect in GlyphEffect.entries) {
            val animation = GlyphEffects.generate(effect, seed = 22, frameCount = 24)
            assertEquals(24, animation.frames.size)
            assertTrue(effect.name, animation.frames.any { f -> f.pixels.any { it } })
            animation.frames.forEach { frame ->
                assertEquals(169, frame.pixels.size)
                assertEquals(170, frame.durationMs)
                assertTrue(frame.pixels.withIndex().all { (index, on) ->
                    !on || GlyphMask.isPhysical(index)
                })
            }
        }
    }

    @Test fun generatedEffectIsDeterministicForSameSeed() {
        for (effect in GlyphEffect.entries) {
            val a = GlyphEffects.generate(effect, seed = 10)
            val b = GlyphEffects.generate(effect, seed = 10)
            assertEquals(a, b)
        }
    }

    @Test fun animationsAreNotAllIdenticalStillFrames() {
        for (effect in GlyphEffect.entries) {
            val frames = GlyphEffects.generate(effect).frames.map { it.pixels }
            assertTrue("No animation for $effect", frames.distinct().size > 1)
        }
    }

    @Test fun invalidDurationOrFrameCountIsRejected() {
        try {
            GlyphEffects.generate(GlyphEffect.RAIN, frameCount = 65)
            fail("Expected count rejection")
        } catch (_: IllegalArgumentException) {}
        try {
            GlyphEffects.generate(GlyphEffect.RAIN, durationMs = 90)
            fail("Expected duration rejection")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun differentSeedsChangeRainAndStars() {
        for (effect in listOf(GlyphEffect.RAIN, GlyphEffect.TWINKLE)) {
            assertNotEquals(
                GlyphEffects.generate(effect, seed = 1),
                GlyphEffects.generate(effect, seed = 4)
            )
        }
    }
}
