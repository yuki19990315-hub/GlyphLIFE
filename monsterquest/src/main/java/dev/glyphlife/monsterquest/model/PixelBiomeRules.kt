package dev.glyphlife.monsterquest.model

/** Nine exploration biomes and a separate, desaturated bullet-hell arena.
 * Floor groups intentionally repeat after floor 18 for longer adventures. */
enum class PixelBiome(val displayName:String) {
    MEADOW("草原"), FOREST("森林"), CAVE("洞窟"), RUINS("古代遺跡"),
    VOLCANO("火山"), ICE("雪山・氷洞"), COAST("海岸"),
    WASTELAND("荒野"), DARK("闇の迷宮"), BOSS("月蝕の祭壇")
}

object PixelBiomeRules {
    const val WORLD_WIDTH=640
    const val CAMERA_WIDTH=320
    const val CAMERA_HEIGHT=180
    const val TILE=16
    const val FLOOR_Y=142

    /** Physical screen pixels per logical art pixel; avoids blur or skew. */
    fun pixelScale(viewWidth:Int,viewHeight:Int):Int {
        require(viewWidth>0 && viewHeight>0)
        return maxOf((viewWidth+CAMERA_WIDTH-1)/CAMERA_WIDTH,
            (viewHeight+CAMERA_HEIGHT-1)/CAMERA_HEIGHT)
    }

    fun zone(floor:Int,isBoss:Boolean):PixelBiome {
        if(isBoss)return PixelBiome.BOSS
        val index=((floor.coerceAtLeast(1)-1)/2)%9
        return PixelBiome.entries[index]
    }

    /** Camera distances in logical 320×180 pixels, not device pixels.
     * The floor tracks the hero; closer layers move faster for depth. */
    fun cameraOffset(position:Float,depth:Int):Int {
        val speed=when(depth) {0->.18f;1->.48f;2->1f;else->1.25f}
        val pixels=(position*speed).toInt()
        return ((pixels%WORLD_WIDTH)+WORLD_WIDTH)%WORLD_WIDTH
    }
}
