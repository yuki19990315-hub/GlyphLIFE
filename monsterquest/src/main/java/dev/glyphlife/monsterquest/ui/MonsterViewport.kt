package dev.glyphlife.monsterquest.ui

/** Geometry shared by the responsive arena and its sprite placement.
 * All measurements are expressed in the same unit (dp in Compose callers).
 * The 64px assets have an eight-pixel transparent presentation margin, so
 * the feet can align to the floor without cropping or stretching the Image.
 */
internal object MonsterViewport {
    private const val GROUND_Y = .79f
    private const val FOOT_MARGIN = 8f / 64f

    /** The artwork is authored at 320x180. On wide devices keep the stage
     * centered instead of turning it into a panoramic, cropped strip. */
    fun stageSize(availableWidth: Float, heightLimit: Float): Pair<Float, Float> {
        require(availableWidth > 0f && heightLimit > 0f)
        val width = minOf(availableWidth, heightLimit * 16f / 9f)
        return width to (width * 9f / 16f)
    }

    fun floorOffset(stageHeight: Float, spriteSize: Float): Float {
        require(stageHeight > 0f && spriteSize > 0f)
        return ((1f - GROUND_Y) * stageHeight - FOOT_MARGIN * spriteSize)
            .coerceAtLeast(0f)
    }

    fun clampLeft(center: Float, spriteSize: Float, stageWidth: Float,
                  inset: Float = 4f): Float {
        require(stageWidth > 0f && spriteSize > 0f && inset >= 0f)
        val free = (stageWidth - spriteSize).coerceAtLeast(0f)
        val margin = inset.coerceAtMost(free / 2f)
        return (center - spriteSize / 2f).coerceIn(margin, free - margin)
    }
}
