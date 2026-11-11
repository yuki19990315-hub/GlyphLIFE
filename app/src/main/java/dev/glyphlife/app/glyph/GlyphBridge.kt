package dev.glyphlife.app.glyph

import android.content.Context

/** The vendor library is isolated from the UI and domain layers. */
interface GlyphBridge {
    val isSupported: Boolean
    fun connect(onStatus: (String) -> Unit)
    fun showFrame(pixels: List<Boolean>)
    fun close()
}

fun createGlyphBridge(context: Context): GlyphBridge = GlyphBridgeImpl(context.applicationContext)
