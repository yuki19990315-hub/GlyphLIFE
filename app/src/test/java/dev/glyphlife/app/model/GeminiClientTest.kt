package dev.glyphlife.app.model

import dev.glyphlife.app.ai.GeminiClient
import org.junit.Assert.*
import org.junit.Test

class GeminiClientTest {
    @Test fun parsesStrictValidSprite() {
        val frame = GlyphOps.encode(PixelArt.defaultPet())
        val generated = """{"frames":[{"pixels":"$frame","durationMs":350}]}"""
        val result = GeminiClient.parseSprite(generated)
        assertEquals(1, result.frames.size)
        assertEquals(PixelArt.defaultPet(), result.frames[0].pixels)
    }

    @Test fun rejectsBadSpriteLength() {
        try {
            GeminiClient.parseSprite("""{"frames":[{"pixels":"01010","durationMs":200}]}""")
            fail("Expected exception")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun rejectsTooManyFrames() {
        val frame = """{"pixels":"${"0".repeat(169)}","durationMs":200}"""
        try {
            GeminiClient.parseSprite("""{"frames":[${List(9) { frame }.joinToString(",")}]}""")
            fail("Expected exception")
        } catch (_: IllegalArgumentException) {}
    }
}
