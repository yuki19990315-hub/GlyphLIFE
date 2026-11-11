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

/**
 * Optional hardware adapter. Compiled only if the proprietary Nothing AAR is
 * manually placed at app/libs/glyph-matrix-sdk-2.0.aar.
 * Verify the AAR API version against upstream SDK and test on actual hardware.
 */
internal class GlyphBridgeImpl(private val context: Context) : GlyphBridge {
    override val isSupported = true
    private var manager: GlyphMatrixManager? = null
    private var connected = false

    override fun connect(onStatus: (String) -> Unit) {
        if (manager != null) return
        try {
            val gm = GlyphMatrixManager.getInstance(context)
            manager = gm
            gm.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(componentName: ComponentName) {
                    runCatching {
                        gm.register(Glyph.DEVICE_25111p)
                        connected = true
                        onStatus("接続済み")
                    }.onFailure { onStatus("Glyph登録失敗: ${it.message}") }
                }
                override fun onServiceDisconnected(componentName: ComponentName) {
                    connected = false
                    onStatus("切断されました")
                }
            })
            onStatus("接続中…")
        } catch (e: Exception) {
            onStatus("SDK接続失敗: ${e.message}")
        }
    }

    override fun showFrame(pixels: List<Boolean>) {
        require(pixels.size == PixelArt.COUNT)
        if (!connected) return
        val bitmap = Bitmap.createBitmap(PixelArt.WIDTH, PixelArt.HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            pixels.forEachIndexed { index, lit ->
                bitmap.setPixel(index % PixelArt.WIDTH, index / PixelArt.WIDTH,
                    if (lit) Color.WHITE else Color.BLACK)
            }
            val glyphObject = GlyphMatrixObject.Builder()
                .setImageSource(bitmap)
                .setPosition(0, 0)
                .setScale(100)
                .build()
            val frame = GlyphMatrixFrame.Builder()
            frame.addTop(glyphObject)
            manager?.setAppMatrixFrame(frame.build(context))
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        connected = false
        runCatching { manager?.closeAppMatrix() }
        runCatching { manager?.unInit() }
        manager = null
    }
}
