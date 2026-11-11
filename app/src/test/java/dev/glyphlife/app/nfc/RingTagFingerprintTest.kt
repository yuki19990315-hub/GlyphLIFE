package dev.glyphlife.app.nfc

import org.junit.Assert.*
import org.junit.Test

class RingTagFingerprintTest {
    @Test fun stableFingerprintLength() {
        val value = RingTagFingerprint.fromUid(byteArrayOf(1, 2, 3, 4))
        assertEquals(64, value.length)
        assertEquals(value, RingTagFingerprint.fromUid(byteArrayOf(1, 2, 3, 4)))
    }

    @Test fun separateUidsGetDifferentFingerprints() {
        val a = RingTagFingerprint.fromUid(byteArrayOf(1, 2, 3))
        val b = RingTagFingerprint.fromUid(byteArrayOf(1, 2, 4))
        assertNotEquals(a, b)
        assertTrue(a.all { it in "0123456789abcdef" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyUid() {
        RingTagFingerprint.fromUid(byteArrayOf())
    }
}
