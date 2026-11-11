package dev.glyphlife.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter

enum class GlyphToyMode(val label: String) {
    PET("ペット"),
    CLOCK("時計・バッテリー")
}

class GlyphToyPreferences(context: Context) {
    private val preferences =
        context.getSharedPreferences("glyph_life_toy_settings_v1", Context.MODE_PRIVATE)

    var mode: GlyphToyMode
        get() = if (preferences.getString("mode", "PET") == "CLOCK")
            GlyphToyMode.CLOCK else GlyphToyMode.PET
        set(value) { preferences.edit().putString("mode", value.name).apply() }
}

/** Battery level is read via Android's sticky broadcast; no extra permission required. */
object PhoneBattery {
    fun percentage(context: Context): Int {
        val status = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return 0
        val level = status.getIntExtra("level", -1)
        val scale = status.getIntExtra("scale", -1)
        if (level < 0 || scale <= 0) return 0
        return (level * 100 / scale).coerceIn(0, 100)
    }
}
