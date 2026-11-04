package com.jim.realcash.data

import com.jim.realcash.domain.parser.RawNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.milliseconds

class NotificationKeyTest {
    private val base = RawNotification("com.samsung.android.spay", "결제 완료", "스타벅스 강남점에서 6,500원 결제", null, T0)

    @Test
    fun fnv1aMatchesReferenceVectors() {
        assertEquals(0xcbf29ce484222325uL, NotificationKey.fnv1a64(ByteArray(0)))
        assertEquals(0xaf63dc4c8601ec8cuL, NotificationKey.fnv1a64("a".encodeToByteArray()))
        assertEquals(0x85944171f73967e8uL, NotificationKey.fnv1a64("foobar".encodeToByteArray()))
    }

    @Test
    fun stableForAFixedInput() {
        val key = NotificationKey.of(base)
        assertEquals(16, key.length)
        assertEquals(key, NotificationKey.of(base.copy()))
        // Pinned value: changing the key derivation would make re-deliveries of old notifications look new.
        assertEquals(PINNED, key)
    }

    @Test
    fun anyChangeGivesADifferentKey() {
        val key = NotificationKey.of(base)
        val variants = listOf(
            base.copy(text = "스타벅스 강남점에서 6,501원 결제"),
            base.copy(title = "결제 완료!"),
            base.copy(sourceApp = "viva.republica.toss"),
            base.copy(postedAt = T0 + 1.milliseconds),
            base.copy(bigText = ""),
            base.copy(title = null),
            // Moving text between fields must not collide.
            base.copy(title = "결제 완료스타벅스 강남점에서 6,500원 결제", text = null),
        )
        variants.forEach { assertNotEquals(key, NotificationKey.of(it), it.toString()) }
        assertEquals(variants.size, variants.map(NotificationKey::of).toSet().size)
    }

    private companion object {
        const val PINNED = "98e693e126f96eb4"
    }
}
