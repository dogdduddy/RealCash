package com.jim.realcash.android

import com.jim.realcash.android.samples.CollectorSettings
import com.jim.realcash.android.samples.NotificationSample
import com.jim.realcash.android.samples.NotificationSampleCodec
import com.jim.realcash.android.samples.NotificationSampleStore
import com.jim.realcash.android.samples.PackageNames
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.parser.RawNotification
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class NotificationSampleTest {
    private val raw = RawNotification(
        sourceApp = "com.samsung.android.spay",
        title = "결제 \"완료\"",
        text = "스타벅스 6,500원",
        bigText = "스타벅스 6,500원\n신한카드(1234)\t승인",
        postedAt = Instant.fromEpochMilliseconds(1_793_000_000_123L),
    )
    private val parsed = DetectedPayment(
        id = PaymentId("x"),
        amount = Won(6_500),
        merchant = "스타벅스",
        sourceApp = raw.sourceApp,
        rawText = "",
        detectedAt = raw.postedAt,
        confidence = 0.85f,
        isRefund = false,
    )

    @Test
    fun parsedSampleRoundTripsThroughOneLine() {
        val sample = NotificationSample.of(raw, parsed)
        val line = NotificationSampleCodec.encodeLine(sample)
        assertFalse('\n' in line, "a JSONL record must stay on one line")
        assertEquals(sample, NotificationSampleCodec.decodeLine(line))
        assertEquals("2026-10-26T07:33:20.123Z", sample.postTime)
        assertEquals(6_500, sample.parsedAmount)
        assertEquals("스타벅스", sample.parsedMerchant)
        assertEquals(0.85f, sample.parsedConfidence)
        assertEquals(false, sample.parsedRefund)
        assertTrue(sample.parsed)
    }

    @Test
    fun unparsedSampleWritesExplicitNulls() {
        val sample = NotificationSample.of(raw.copy(title = null), null)
        val line = NotificationSampleCodec.encodeLine(sample)
        val obj = Json.parseToJsonElement(line).jsonObject
        assertEquals(
            setOf(
                "packageName", "postTime", "title", "text", "bigText",
                "parsedAmount", "parsedMerchant", "parsedConfidence", "parsedRefund",
                "isOngoing", "isGroupSummary", "channelId",
            ),
            obj.keys,
        )
        assertEquals(JsonNull, obj["title"])
        assertEquals(JsonNull, obj["parsedAmount"])
        assertEquals(JsonPrimitive(false), obj["isOngoing"])
        assertEquals(JsonPrimitive(false), obj["isGroupSummary"])
        assertEquals(JsonNull, obj["channelId"])
        assertEquals(sample, NotificationSampleCodec.decodeLine(line))
        assertFalse(sample.parsed)
    }

    @Test
    fun postingFlagsAndChannelRoundTrip() {
        val sample = NotificationSample.of(raw, parsed, isOngoing = true, isGroupSummary = true, channelId = "payment_result")
        val line = NotificationSampleCodec.encodeLine(sample)
        val obj = Json.parseToJsonElement(line).jsonObject
        assertEquals(JsonPrimitive(true), obj["isOngoing"])
        assertEquals(JsonPrimitive(true), obj["isGroupSummary"])
        assertEquals(JsonPrimitive("payment_result"), obj["channelId"])
        val decoded = NotificationSampleCodec.decodeLine(line)
        assertEquals(sample, decoded)
        assertTrue(decoded!!.isOngoing && decoded.isGroupSummary)
    }

    @Test
    fun linesWithoutPostingFieldsDecodeWithDefaults() {
        val old = """{"packageName":"a.b","postTime":"2026-11-04T00:00:00Z","title":null,"text":"t","bigText":null,""" +
            """"parsedAmount":6500,"parsedMerchant":null,"parsedConfidence":0.6,"parsedRefund":false}"""
        val decoded = NotificationSampleCodec.decodeLine(old)!!
        assertFalse(decoded.isOngoing)
        assertFalse(decoded.isGroupSummary)
        assertNull(decoded.channelId)
        assertEquals(6_500, decoded.parsedAmount)
    }

    @Test
    fun decodeSkipsBlankMalformedLinesAndIgnoresUnknownKeys() {
        val good = NotificationSampleCodec.encodeLine(NotificationSample.of(raw, null))
        val withExtra = """{"packageName":"a.b","postTime":"2026-11-04T00:00:00Z","title":null,"text":"t","bigText":null,"future":1}"""
        val text = listOf(good, "", "not json", "{\"packageName\":1}", withExtra).joinToString("\n")
        val decoded = NotificationSampleCodec.decodeAll(text)
        assertEquals(2, decoded.size)
        assertEquals("a.b", decoded[1].packageName)
        assertNull(decoded[1].parsedAmount)
        assertNull(NotificationSampleCodec.decodeLine("   "))
    }

    @Test
    fun clipboardArrayIsValidJson() {
        val samples = listOf(NotificationSample.of(raw, parsed), NotificationSample.of(raw, null))
        val array = Json.parseToJsonElement(NotificationSampleCodec.encodeArray(samples))
        assertEquals(2, (array as JsonArray).size)
    }

    @Test
    fun storeAppendsReadsNewestFirstCopiesAndClears() {
        val dir = Files.createTempDirectory("samples").toFile()
        try {
            val store = NotificationSampleStore(File(dir, NotificationSampleStore.FILE_NAME))
            assertEquals(0, store.count())
            assertFalse(store.copyTo(File(dir, "copy.jsonl")))
            val v0 = store.version.value
            repeat(3) { i -> store.append(NotificationSample.of(raw.copy(text = "t$i"), null)) }
            assertEquals(v0 + 3, store.version.value)
            assertEquals(3, store.count())
            assertEquals(listOf("t2", "t1"), store.readLatest(2).map { it.text })
            assertEquals(3, store.readText().lines().count { it.isNotBlank() })
            val copy = File(dir, "export/copy.jsonl")
            assertTrue(store.copyTo(copy))
            assertEquals(store.readText(), copy.readText())
            store.clear()
            assertEquals(0, store.count())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun packageNameValidation() {
        assertTrue(PackageNames.isValid("com.samsung.android.spay"))
        assertTrue(PackageNames.isValid("viva.republica.toss"))
        assertTrue(PackageNames.isValid("kr.co.card_app2"))
        assertFalse(PackageNames.isValid("toss"))
        assertFalse(PackageNames.isValid("com..toss"))
        assertFalse(PackageNames.isValid("1com.toss"))
        assertFalse(PackageNames.isValid("com.toss "))
        assertFalse(PackageNames.isValid(""))
        assertTrue(CollectorSettings.DEFAULT_PACKAGES.all(PackageNames::isValid))
    }
}
