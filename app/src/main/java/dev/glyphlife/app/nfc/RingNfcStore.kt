package dev.glyphlife.app.nfc

import android.content.Context
import java.security.MessageDigest

/**
 * NFC UID fingerprint for convenient personal shortcuts only.
 * A tag UID is NOT a cryptographic identity; NFC tags can be cloned or use a
 * randomized UID. Never use this for authentication or private data access.
 */
object RingTagFingerprint {
    fun fromUid(uid: ByteArray): String {
        require(uid.isNotEmpty())
        val digest = MessageDigest.getInstance("SHA-256").digest(uid)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class RingNfcStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("glyph_life_nfc_v1", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }

    var enrolledFingerprint: String?
        get() = preferences.getString("uid_hash", null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("uid_hash")
                else putString("uid_hash", value)
            }.apply()
        }

    fun isEnrolled(): Boolean = enrolledFingerprint != null

    fun matches(uid: ByteArray): Boolean =
        uid.isNotEmpty() && enrolledFingerprint == RingTagFingerprint.fromUid(uid)
}
