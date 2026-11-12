package dev.glyphlife.app.glyph

import android.content.Context

/** The vendor library is isolated from the UI and domain layers. */
interface GlyphBridge {
    val isSupported: Boolean
    fun connect(onStatus: (String) -> Unit)
    fun showFrame(pixels: List<Boolean>)
    /** 0..255 on each physical LED. Defaults to legacy binary on older adapters. */
    fun showBrightnessFrame(levels: List<Int>) {
        require(levels.size == 169 && levels.all { it in 0..255 })
        showFrame(levels.map { it >= 24 })
    }
    fun close()
}

fun createGlyphBridge(context: Context): GlyphBridge = GlyphBridgeImpl(context.applicationContext)
