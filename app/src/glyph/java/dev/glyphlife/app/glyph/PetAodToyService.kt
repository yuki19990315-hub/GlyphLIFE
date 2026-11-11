package dev.glyphlife.app.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
import dev.glyphlife.app.data.GlyphToyMode
import dev.glyphlife.app.data.GlyphToyPreferences
import dev.glyphlife.app.data.PetRepository
import dev.glyphlife.app.data.PhoneBattery
import dev.glyphlife.app.model.GlyphMask
import dev.glyphlife.app.model.GlyphClock
import java.time.LocalDateTime

/**
 * Phone (4a) Pro AOD Glyph Toy.
 *
 * Unlike foreground app rendering, this Service uses setMatrixFrame() and is
 * driven by Nothing's EVENT_AOD (~1/minute). It NEVER starts a rapid
 * background animation loop. This adapter requires the proprietary official
 * SDK AAR, and needs on-device verification.
 */
class PetAodToyService : Service() {
    private var manager: GlyphMatrixManager? = null
    private var ready = false
    private var stopped = false
    private var frameIndex = 0

    private val events = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what == GlyphToy.MSG_GLYPH_TOY) {
                when (message.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                    GlyphToy.EVENT_AOD -> {
                        frameIndex++
                        renderSavedFrame()
                    }
                }
            } else super.handleMessage(message)
        }
    }
    private val messenger = Messenger(events)

    override fun onBind(intent: Intent?): IBinder {
        stopped = false
        frameIndex = 0
        connect()
        return messenger.binder
    }

    private fun connect() {
        try {
            val m = GlyphMatrixManager.getInstance(applicationContext)
            manager = m
            m.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    if (stopped) return
                    runCatching {
                        m.register(Glyph.DEVICE_25111p)
                        ready = true
                        renderSavedFrame()
                    }.onFailure { Log.w(TAG, "Glyph Toy registration failed", it) }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    ready = false
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Glyph Toy bind failed", e)
            ready = false
        }
    }

    private fun renderSavedFrame() {
        val m = manager ?: return
        if (!ready || stopped) return
        val pixels = if (GlyphToyPreferences(applicationContext).mode == GlyphToyMode.CLOCK) {
            val now = LocalDateTime.now()
            GlyphClock.draw(now.hour, now.minute, PhoneBattery.percentage(applicationContext))
        } else {
            val frames = PetRepository(applicationContext).loadAnimation().frames
            if (frames.isEmpty()) return
            GlyphMask.normalize(frames[frameIndex % frames.size].pixels)
        }
        val bitmap = Bitmap.createBitmap(13, 13, Bitmap.Config.ARGB_8888)
        try {
            pixels.forEachIndexed { index, on ->
                bitmap.setPixel(index % 13, index / 13, if (on) Color.WHITE else Color.BLACK)
            }
            val shape = GlyphMatrixObject.Builder()
                .setImageSource(bitmap)
                .setScale(100)
                .setOrientation(0)
                .setPosition(0, 0)
                .setBrightness(255)
                .build()
            val frame = GlyphMatrixFrame.Builder()
                .addTop(shape).build(applicationContext)
            m.setMatrixFrame(frame.render())
        } catch (e: Exception) {
            Log.w(TAG, "Glyph Toy frame failed", e)
        } finally {
            bitmap.recycle()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return false
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        if (stopped) return
        stopped = true
        ready = false
        runCatching { manager?.unInit() }
        manager = null
    }

    private companion object {
        const val TAG = "GlyphLifeAOD"
    }
}
