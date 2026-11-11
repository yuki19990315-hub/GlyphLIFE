package dev.glyphlife.app.glyph

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Disabled in non-Nothing SDK builds. Never claims to render a Glyph Toy. */
class PetAodToyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
