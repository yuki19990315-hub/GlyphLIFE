package dev.glyphlife.monsterquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.glyphlife.monsterquest.R
import dev.glyphlife.monsterquest.model.PixelBiome
import dev.glyphlife.monsterquest.model.PixelBiomeRules
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class PixelLayers(val far:Int,val mid:Int,val ground:Int,val front:Int)

private fun artFor(area:PixelBiome):PixelLayers=when(area) {
    PixelBiome.MEADOW->PixelLayers(R.drawable.mq_bg_meadow_far,
        R.drawable.mq_bg_meadow_mid,R.drawable.mq_bg_meadow_ground,
        R.drawable.mq_bg_meadow_front)
    PixelBiome.FOREST->PixelLayers(R.drawable.mq_bg_forest_far,
        R.drawable.mq_bg_forest_mid,R.drawable.mq_bg_forest_ground,
        R.drawable.mq_bg_forest_front)
    PixelBiome.CAVE->PixelLayers(R.drawable.mq_bg_cave_far,
        R.drawable.mq_bg_cave_mid,R.drawable.mq_bg_cave_ground,
        R.drawable.mq_bg_cave_front)
    PixelBiome.RUINS->PixelLayers(R.drawable.mq_bg_ruins_far,
        R.drawable.mq_bg_ruins_mid,R.drawable.mq_bg_ruins_ground,
        R.drawable.mq_bg_ruins_front)
    PixelBiome.VOLCANO->PixelLayers(R.drawable.mq_bg_volcano_far,
        R.drawable.mq_bg_volcano_mid,R.drawable.mq_bg_volcano_ground,
        R.drawable.mq_bg_volcano_front)
    PixelBiome.ICE->PixelLayers(R.drawable.mq_bg_ice_far,
        R.drawable.mq_bg_ice_mid,R.drawable.mq_bg_ice_ground,
        R.drawable.mq_bg_ice_front)
    PixelBiome.COAST->PixelLayers(R.drawable.mq_bg_coast_far,
        R.drawable.mq_bg_coast_mid,R.drawable.mq_bg_coast_ground,
        R.drawable.mq_bg_coast_front)
    PixelBiome.WASTELAND->PixelLayers(R.drawable.mq_bg_wasteland_far,
        R.drawable.mq_bg_wasteland_mid,R.drawable.mq_bg_wasteland_ground,
        R.drawable.mq_bg_wasteland_front)
    PixelBiome.DARK->PixelLayers(R.drawable.mq_bg_dark_far,
        R.drawable.mq_bg_dark_mid,R.drawable.mq_bg_dark_ground,
        R.drawable.mq_bg_dark_front)
    PixelBiome.BOSS->PixelLayers(R.drawable.mq_bg_boss_far,
        R.drawable.mq_bg_boss_mid,R.drawable.mq_bg_boss_ground,
        R.drawable.mq_bg_boss_front)
}

/** Draws a repeating 640×180 PNG strip at an INTEGER physical-pixel scale.
 * On non-16:9 viewports, extra scenery is cropped equally at the center,
 * never stretched. Each 16×16 tile keeps its original square pixel grid. */
private fun DrawScope.drawStrip(image:ImageBitmap,logicalScroll:Int,
    scale:Int) {
    val width=image.width*scale
    val height=image.height*scale
    val shift=(logicalScroll*scale)%width
    val top=((size.height-height)/2f).roundToInt()
    val worldOffset=((size.width-PixelBiomeRules.CAMERA_WIDTH*scale)/2f)
        .roundToInt().coerceAtMost(0)
    var x=worldOffset-shift
    while(x>0)x-=width
    while(x+width<=0)x+=width
    while(x<size.width) {
        drawImage(image=image,
            dstOffset=IntOffset(x,top),dstSize=IntSize(width,height),
            filterQuality=FilterQuality.None)
        x+=width
    }
}

private fun DrawScope.drawWeather(area:PixelBiome,frame:Int,scale:Int) {
    val snow=area==PixelBiome.ICE
    val ash=area==PixelBiome.VOLCANO || area==PixelBiome.DARK
    val rain=area==PixelBiome.FOREST
    val surf=area==PixelBiome.COAST
    if(!snow && !ash && !rain && !surf)return
    val color=when {
        snow->Color(0xFFC9EDF1)
        ash->Color(0xFFE5A77A)
        rain->Color(0xFF91BFC0)
        else->Color(0xFFCDF7EC)
    }
    val offset=((size.width-PixelBiomeRules.CAMERA_WIDTH*scale)/2f)
        .roundToInt().coerceAtMost(0)
    val top=((size.height-PixelBiomeRules.CAMERA_HEIGHT*scale)/2f).roundToInt()
    val count=if(surf)19 else 32
    for(i in 0 until count) {
        val x=if(surf)(i*23+frame*2)%320
            else (i*43+(frame*(if(rain)5 else 2))+(i%5)*7)%320
        val y=if(surf)133+(i%4)*2
            else (i*31+frame*(if(snow)2 else if(ash)-1 else 6)+18000)%138
        drawRect(color.copy(alpha=if(surf).48f else .75f),
            topLeft=Offset((x*scale+offset).toFloat(),(y*scale+top).toFloat()),
            size=Size((if(rain)1 else 2)*scale.toFloat(),
                (if(rain)5 else 2)*scale.toFloat()))
    }
}

/** Back call: far, mid, collision floor, then ambient pixel effects.
 * Fore call: transparent foreground props ON TOP of monster sprites, but
 * below the independently-drawn attack telegraphs and projectile layer. */
@Composable
fun PixelParallaxStage(area:PixelBiome,position:Float,foreground:Boolean,
    modifier:Modifier=Modifier) {
    val ids=remember(area){artFor(area)}
    val far=if(!foreground)ImageBitmap.imageResource(ids.far) else null
    val mid=if(!foreground)ImageBitmap.imageResource(ids.mid) else null
    val ground=if(!foreground)ImageBitmap.imageResource(ids.ground) else null
    val front=if(foreground)ImageBitmap.imageResource(ids.front) else null
    var frame by remember(area,foreground){mutableIntStateOf(0)}
    LaunchedEffect(area,foreground) {
        if(!foreground)while(true) {
            delay(170L)
            frame=(frame+1)%10_000
        }
    }
    Canvas(modifier) {
        val scale=PixelBiomeRules.pixelScale(size.width.roundToInt(),
            size.height.roundToInt())
        if(foreground) {
            front?.let {drawStrip(it,PixelBiomeRules.cameraOffset(position,3),scale)}
        }else {
            far?.let {drawStrip(it,PixelBiomeRules.cameraOffset(position,0),scale)}
            mid?.let {drawStrip(it,PixelBiomeRules.cameraOffset(position,1),scale)}
            ground?.let {drawStrip(it,PixelBiomeRules.cameraOffset(position,2),scale)}
            drawWeather(area,frame,scale)
        }
    }
}
