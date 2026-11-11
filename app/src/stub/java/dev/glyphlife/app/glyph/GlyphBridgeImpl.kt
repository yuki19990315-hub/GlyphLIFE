package dev.glyphlife.app.glyph

import android.content.Context

/** Fallback when the Nothing SDK is not installed in the Android build. */
internal class GlyphBridgeImpl(@Suppress("UNUSED_PARAMETER") context: Context) : GlyphBridge {
    override val isSupported: Boolean = false
    override fun connect(onStatus: (String) -> Unit) {
        onStatus("Nothing SDK未導入：画面上のプレビューのみ利用できます")
    }
    override fun showFrame(pixels: List<Boolean>) = Unit
    override fun close() = Unit
}
