package dev.glyphlife.monsterquest.ai

import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.ArmorSlot
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ArmorImageAiTest {
    private val monster=WildMonster(
        id="00000000-0000-4000-8000-000000000001",
        species="原生竜",element="雷",maxHp=50,attack=10,bodyType="DRAGON"
    )
    @Test fun promptRequestsOnlySpecificSlotNeverWholeCreature() {
        val prompts=ArmorSlot.entries.map { ArmorImageAi.prompt(monster,it) }
        assertEquals(ArmorSlot.entries.size,prompts.distinct().size)
        prompts.forEachIndexed { index,prompt ->
            assertTrue(prompt.contains(ArmorSlot.entries[index].name))
            assertTrue(prompt.contains("MAGENTA"))
            assertTrue(prompt.contains("64x64"))
            assertTrue(prompt.contains("not a"))
            assertTrue(prompt.contains("ONE"))
        }
    }
    private fun fixturePng() = byteArrayOf(
        137.toByte(), 80, 78, 71, 13, 10, 26, 10
    ) + ByteArray(128) { (it % 251).toByte() }

    @Test fun imageRequestIncludesActualReferenceWithCorrectMimeAndBytes() {
        val input = fixturePng()
        val request = ArmorImageAi.requestBody(monster, ArmorSlot.HEAD, input)
        val content = request.getJSONArray("contents").getJSONObject(0)
        assertEquals("user", content.getString("role"))
        val parts = content.getJSONArray("parts")
        assertEquals(2, parts.length())
        val instructions = parts.getJSONObject(0).getString("text")
        assertTrue(instructions.contains("REFERENCE IMAGE"))
        assertTrue(instructions.contains("NOT redraw"))
        assertTrue(instructions.contains("HEAD"))
        val image = parts.getJSONObject(1).getJSONObject("inlineData")
        assertEquals("image/png", image.getString("mimeType"))
        assertArrayEquals(input, Base64.getDecoder().decode(image.getString("data")))
        val config = request.getJSONObject("generationConfig")
        assertEquals("IMAGE", config.getJSONArray("responseModalities").getString(0))
        assertEquals("1:1", config.getJSONObject("responseFormat")
            .getJSONObject("image").getString("aspectRatio"))
    }

    @Test fun invalidReferenceBytesCannotBeSentToImageModel() {
        val invalid = listOf(
            ByteArray(0), ByteArray(200), byteArrayOf(1,2,3,4,5,6,7,8) + ByteArray(128),
            fixturePng() + ByteArray(2_000_000)
        )
        invalid.forEach { bytes ->
            try {
                ArmorImageAi.requestBody(monster, ArmorSlot.CORE, bytes)
                fail("Expected invalid reference rejection")
            } catch (_: IllegalArgumentException) { }
        }
    }

}
