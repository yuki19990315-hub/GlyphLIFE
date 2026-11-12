package dev.glyphlife.app.glyph

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import dev.glyphlife.app.model.PixelArt
import dev.glyphlife.app.model.GlyphMask

/**
 * Real-device adapter for the official Nothing 2.0 AAR. Only compiled when
 * app/libs/glyph-matrix-sdk-2.0.aar exists. On-device behavior is unverified.
 */
internal class GlyphBridgeImpl(private val context: Context) : GlyphBridge {
    override val isSupported = true
    private var manager: GlyphMatrixManager? = null
    private var connected = false
    private var latest: List<Int>? = null

    override fun connect(onStatus: (String) -> Unit) {
        if (manager != null) return
        try {
            val gm = GlyphMatrixManager.getInstance(context)
            manager = gm
            gm.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(componentName: ComponentName?) {
                    try {
                        gm.register(Glyph.DEVICE_25111p)
                        connected = true
                        onStatus("接続済み")
                        latest?.let { showBrightnessFrame(it) }
                    } catch (e: Exception) {
                        connected = false
                        onStatus("Glyph登録失敗: ${e.message}")
                    }
                }

                override fun onServiceDisconnected(componentName: ComponentName?) {
                    connected = false
                    onStatus("切断されました")
                }
            })
            onStatus("接続中…")
        } catch (e: Exception) {
            manager = null
            onStatus("SDK接続失敗: ${e.message}")
        }
    }

    override fun showFrame(pixels: List<Boolean>) {
        require(pixels.size == PixelArt.COUNT)
        showBrightnessFrame(GlyphMask.normalize(pixels).map { if (it) 255 else 0 })
    }

    override fun showBrightnessFrame(levels: List<Int>) {
        require(levels.size == PixelArt.COUNT && levels.all { it in 0..255 })
        val visible = levels.mapIndexed { i, v ->
            if (GlyphMask.isPhysical(i)) v else 0
        }
        latest = visible
        if (!connected) return
        val image = Bitmap.createBitmap(PixelArt.WIDTH, PixelArt.HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            visible.forEachIndexed { i, level ->
                image.setPixel(i % 13, i / 13, Color.rgb(level, level, level))
            }
            // The bitmap stores per-dot luminance; 255 is the overall cap.
            val item = GlyphMatrixObject.Builder()
                .setImageSource(image)
                .setScale(100)
                .setOrientation(0)
                .setPosition(0, 0)
                .setBrightness(255)
                .build()
            val rendered = GlyphMatrixFrame.Builder()
                .addTop(item)
                .build(context)
                .render()
            manager?.setAppMatrixFrame(rendered)
        } finally {
            image.recycle()
        }
    }

    override fun close() {
        connected = false
        latest = null
        runCatching { manager?.closeAppMatrix() }
        runCatching { manager?.unInit() }
        manager = null
    }
}
