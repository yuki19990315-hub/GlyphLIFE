package dev.glyphlife.monsterquest.ai

import dev.glyphlife.monsterquest.model.WildMonster
import dev.glyphlife.monsterquest.pixel.ArmorSlot
import org.junit.Assert.*
import org.junit.Test

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
}
