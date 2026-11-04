package com.jim.realcash.data

import com.jim.realcash.domain.parser.RawNotification

/**
 * Deterministic identity of a captured notification, used as the parsed payment's id so that an exact
 * re-delivery (Android re-posting the same notification, a Shortcut re-run, a retry after a crash) maps to the
 * same `PaymentId` and is recognised as already handled.
 */
internal object NotificationKey {

    private const val SEPARATOR = '\u0000'
    private const val FNV_OFFSET_BASIS: ULong = 0xcbf29ce484222325uL
    private const val FNV_PRIME: ULong = 0x100000001b3uL

    /**
     * 16 lower-case hex digits: 64-bit FNV-1a over the UTF-8 bytes of
     * `sourceApp, postedAt (epoch ms), title, text, bigText` joined with `\u0000`.
     * Each nullable field is prefixed with `+` when present and encoded as `-` when `null`, so `null` and `""`
     * produce different keys.
     */
    fun of(n: RawNotification): String {
        val joined = listOf(
            n.sourceApp,
            n.postedAt.toEpochMilliseconds().toString(),
            optional(n.title),
            optional(n.text),
            optional(n.bigText),
        ).joinToString(SEPARATOR.toString())
        return fnv1a64(joined.encodeToByteArray()).toString(16).padStart(16, '0')
    }

    private fun optional(value: String?): String = if (value == null) "-" else "+$value"

    /** 64-bit FNV-1a. */
    fun fnv1a64(bytes: ByteArray): ULong {
        var hash = FNV_OFFSET_BASIS
        for (b in bytes) {
            hash = hash xor (b.toUByte().toULong())
            hash *= FNV_PRIME
        }
        return hash
    }
}
