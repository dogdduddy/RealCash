package com.jim.realcash.ui.format

import androidx.compose.ui.text.AnnotatedString
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.parser.DefaultRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class FormattingTest {
    @Test
    fun wonFormatted() {
        assertEquals("₩0", Won.ZERO.formatted())
        assertEquals("₩500", Won(500).formatted())
        assertEquals("₩1,000", Won(1_000).formatted())
        assertEquals("₩12,000", Won(12_000).formatted())
        assertEquals("₩1,234,567", Won(1_234_567).formatted())
        assertEquals("₩100,000,000", Won(100_000_000).formatted())
        assertEquals("-₩1,500", Won(-1_500).formatted())
    }

    @Test
    fun groupThousandsHandlesBoundaries() {
        assertEquals("0", groupThousands(0))
        assertEquals("999", groupThousands(999))
        assertEquals("1,000", groupThousands(1_000))
        assertEquals("-12,345", groupThousands(-12_345))
        assertEquals("2,147,483,647", groupThousands(Int.MAX_VALUE.toLong()))
    }

    @Test
    fun faceLabel() {
        assertEquals("50,000", Denomination.W50000.faceLabel)
        assertEquals("10", Denomination.C10.faceLabel)
    }

    @Test
    fun amountInputSanitizesDigits() {
        assertEquals("", AmountInput.sanitize(""))
        assertEquals("", AmountInput.sanitize("000"))
        assertEquals("100000", AmountInput.sanitize("₩100,000원"))
        assertEquals("12345678", AmountInput.sanitize("123456789"))
        assertEquals(Won(6_500), AmountInput.parse("6500"))
        assertNull(AmountInput.parse(""))
    }

    @Test
    fun thousandsSeparatorTransformationMapsOffsets() {
        val t = ThousandsSeparatorTransformation.filter(AnnotatedString("1000000"))
        assertEquals("1,000,000", t.text.text)
        val m = t.offsetMapping
        // original -> transformed
        assertEquals(0, m.originalToTransformed(0))
        assertEquals(2, m.originalToTransformed(1))
        assertEquals(3, m.originalToTransformed(2))
        assertEquals(6, m.originalToTransformed(4))
        assertEquals(9, m.originalToTransformed(7))
        // transformed -> original, round trips
        for (o in 0..7) assertEquals(o, m.transformedToOriginal(m.originalToTransformed(o)), "round trip of $o")
        assertEquals(1, m.transformedToOriginal(1)) // cursor right after "1", before the comma
        assertEquals(7, m.transformedToOriginal(9))

        val short = ThousandsSeparatorTransformation.filter(AnnotatedString("999"))
        assertEquals("999", short.text.text)
        assertEquals(3, short.offsetMapping.originalToTransformed(3))

        val empty = ThousandsSeparatorTransformation.filter(AnnotatedString(""))
        assertEquals("", empty.text.text)
        assertEquals(0, empty.offsetMapping.originalToTransformed(0))
    }

    @Test
    fun remainingTime() {
        assertEquals("만료됨", formatRemaining(Duration.ZERO))
        assertEquals("만료됨", formatRemaining((-5).minutes))
        assertEquals("1분 미만 남음", formatRemaining(40.seconds))
        assertEquals("12분 남음", formatRemaining(12.minutes + 30.seconds))
        assertEquals("2시간 남음", formatRemaining(2.hours))
        assertEquals("5시간 12분 남음", formatRemaining(5.hours + 12.minutes))
    }

    @Test
    fun dateAndTimeLabels() {
        val seoul = TimeZone.of("Asia/Seoul")
        val today = LocalDate(2026, 11, 4)
        assertEquals("11.2 (월)", LocalDate(2026, 11, 2).shortLabel())
        assertEquals("11.8 (일)", LocalDate(2026, 11, 8).shortLabel())
        // 09:00Z = 18:00 KST on the same day.
        assertEquals("18:00", formatTime(Instant.parse("2026-11-04T09:00:00Z"), seoul, today))
        assertEquals("11.3 23:05", formatTime(Instant.parse("2026-11-03T14:05:00Z"), seoul, today))
    }

    @Test
    fun sourceAndConfidenceLabels() {
        assertEquals("삼성월렛", sourceAppLabel(DefaultRules.SAMSUNG_WALLET))
        assertEquals("토스", sourceAppLabel(DefaultRules.TOSS))
        assertEquals("com.example.bank", sourceAppLabel("com.example.bank"))
        assertEquals("85%", formatConfidence(0.85f))
        assertEquals("100%", formatConfidence(1f))
        assertEquals("0%", formatConfidence(-0.1f))
    }

    @Test
    fun splitSummaries() {
        val from = mapOf(Denomination.W50000 to 1)
        val to = mapOf(Denomination.W5000 to 2, Denomination.W10000 to 4, Denomination.C100 to 0)
        assertEquals("50,000×1", splitSummary(from))
        assertEquals("10,000×4 + 5,000×2", splitSummary(to), "largest first, zero counts skipped")
        assertEquals("", splitSummary(emptyMap()))
        assertEquals("50,000×1 → 10,000×4 + 5,000×2", exchangeSummary(from, to))
    }
}
