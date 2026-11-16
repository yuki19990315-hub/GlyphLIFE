package dev.glyphlife.monsterquest.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterViewportTest {
    @Test fun pixelStageKeepsExactSixteenByNineWithoutStretchingOnWideScreens() {
        for ((width,limit) in listOf(240f to 158f,320f to 202f,
                390f to 252f,800f to 252f,1280f to 252f)) {
            val (stageWidth,stageHeight)=MonsterViewport.stageSize(width,limit)
            assertTrue("overflows available width",stageWidth<=width)
            assertTrue("overflows height limit",stageHeight<=limit)
            assertTrue("stage aspect ratio differs from 16:9",
                kotlin.math.abs(stageWidth/stageHeight-16f/9f)<.0001f)
        }
    }

    @Test fun monsterStaysInsideArenaAtBossMovementExtremes() {
        for (width in listOf(240f, 280f, 320f, 390f, 480f)) {
            val sprite = minOf(width * .31f, 158f)
            for (center in listOf(0f, width * .1f, width * .2f,
                    width * .5f, width * .85f, width)) {
                val left = MonsterViewport.clampLeft(center, sprite, width)
                assertTrue("outside left on $width: $left", left >= 0f)
                assertTrue("outside right on $width: $left", left + sprite <= width)
            }
        }
    }

    @Test fun fullSizeSpriteAndAutomaticHopNeverClipAtArenaTopOrBottom() {
        for ((height, width) in listOf(144f to 240f, 177f to 320f,
                214f to 390f)) {
            val sprite = minOf(width * .23f, if (height <= 144f) 76f else 104f)
            val groundOffset = MonsterViewport.floorOffset(height, sprite)
            // In the boss run the whole art square moves up to 14% of the
            // arena height. The square, not just its center, remains visible.
            val airborneTop = height - groundOffset - sprite - height * .14f
            val restingBottom = height - groundOffset
            assertTrue("head clipped at $height: $airborneTop", airborneTop >= 0f)
            assertTrue("feet clipped at $height: $restingBottom", restingBottom <= height)
        }
    }

    @Test fun groundOffsetKeepsOpaqueFeetOnBackdropFloor() {
        for ((height, sprite) in listOf(144f to 76f, 177f to 100f,
                214f to 104f)) {
            val imageBottom = height - MonsterViewport.floorOffset(height, sprite)
            val opaqueFeet = imageBottom - sprite * (8f / 64f)
            assertTrue(kotlin.math.abs(opaqueFeet - height * .79f) < .01f)
        }
    }
}
