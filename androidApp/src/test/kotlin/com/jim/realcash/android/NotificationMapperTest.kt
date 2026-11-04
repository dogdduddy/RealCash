package com.jim.realcash.android

import android.app.Notification
import com.jim.realcash.android.listener.NotificationMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class NotificationMapperTest {
    private val postTime = 1_793_000_000_123L

    @Test
    fun mapsTitleTextBigTextAndTime() {
        val raw = NotificationMapper.toRaw(
            "com.samsung.android.spay",
            postTime,
            mapOf(
                Notification.EXTRA_TITLE to "삼성월렛",
                Notification.EXTRA_TEXT to "스타벅스 6,500원 결제",
                Notification.EXTRA_BIG_TEXT to StringBuilder("스타벅스 6,500원 결제\n신한카드(1234)"),
            ),
        )
        assertEquals("com.samsung.android.spay", raw.sourceApp)
        assertEquals("삼성월렛", raw.title)
        assertEquals("스타벅스 6,500원 결제", raw.text)
        assertEquals("스타벅스 6,500원 결제\n신한카드(1234)", raw.bigText)
        assertEquals(Instant.fromEpochMilliseconds(postTime), raw.postedAt)
    }

    @Test
    fun missingBlankAndNonTextValuesBecomeNull() {
        val raw = NotificationMapper.toRaw(
            "viva.republica.toss",
            postTime,
            mapOf(
                Notification.EXTRA_TITLE to "   ",
                Notification.EXTRA_TEXT to 42,
                Notification.EXTRA_BIG_TEXT to null,
            ),
        )
        assertNull(raw.title)
        assertNull(raw.text)
        assertNull(raw.bigText)
        assertEquals("viva.republica.toss", raw.sourceApp)
    }

    @Test
    fun emptyExtras() {
        val raw = NotificationMapper.toRaw("a.b", 0L, emptyMap())
        assertNull(raw.title)
        assertNull(raw.text)
        assertNull(raw.bigText)
        assertEquals(Instant.fromEpochMilliseconds(0), raw.postedAt)
    }

    @Test
    fun inboxLinesAreUsedWhenThereIsNoBigText() {
        val lines: Array<CharSequence?> = arrayOf("6,500원 결제", " ", null, "스타벅스")
        val raw = NotificationMapper.toRaw("a.b", postTime, mapOf(Notification.EXTRA_TEXT_LINES to lines))
        assertEquals("6,500원 결제\n스타벅스", raw.bigText)
    }

    @Test
    fun bigTextWinsOverInboxLines() {
        val raw = NotificationMapper.toRaw(
            "a.b",
            postTime,
            mapOf(
                Notification.EXTRA_BIG_TEXT to "big",
                Notification.EXTRA_TEXT_LINES to arrayOf<CharSequence>("line"),
            ),
        )
        assertEquals("big", raw.bigText)
    }

    @Test
    fun keysAreTheFrameworkExtraNames() {
        assertEquals(listOf("android.title", "android.text", "android.bigText", "android.textLines"), NotificationMapper.KEYS)
    }

    @Test
    fun collectorSamplesOngoingAndGroupSummariesButIntakeSkipsThem() {
        val ongoing = NotificationMapper.route(collectorEnabled = true, inCollectorList = true, isOngoing = true, isGroupSummary = false)
        assertEquals(NotificationMapper.Route(sample = true, intake = false), ongoing)
        val summary = NotificationMapper.route(collectorEnabled = true, inCollectorList = true, isOngoing = false, isGroupSummary = true)
        assertEquals(NotificationMapper.Route(sample = true, intake = false), summary)
        val plain = NotificationMapper.route(collectorEnabled = true, inCollectorList = true, isOngoing = false, isGroupSummary = false)
        assertEquals(NotificationMapper.Route(sample = true, intake = true), plain)
    }

    @Test
    fun collectorOffOrPackageNotListedMeansNoSample() {
        assertEquals(
            NotificationMapper.Route(sample = false, intake = true),
            NotificationMapper.route(collectorEnabled = false, inCollectorList = true, isOngoing = false, isGroupSummary = false),
        )
        assertEquals(
            NotificationMapper.Route(sample = false, intake = false),
            NotificationMapper.route(collectorEnabled = true, inCollectorList = false, isOngoing = true, isGroupSummary = false),
        )
    }
}
