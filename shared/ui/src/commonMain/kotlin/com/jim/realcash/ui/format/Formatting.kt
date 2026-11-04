package com.jim.realcash.ui.format

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.parser.DefaultRules
import com.jim.realcash.ui.Strings
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** Groups the digits of [value] by thousands: `12000` -> `12,000`, `-1500` -> `-1,500`. */
fun groupThousands(value: Long): String {
    val digits = if (value < 0) (-value).toString() else value.toString()
    val sb = StringBuilder(digits.length + digits.length / 3)
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return if (value < 0) "-$sb" else sb.toString()
}

/** UI form of an amount: `₩12,000`; negative amounts as `-₩1,500`. */
fun Won.formatted(): String = if (value < 0) "-₩${groupThousands(-value.toLong())}" else "₩${groupThousands(value.toLong())}"

/** Face value label of a denomination without currency sign: `50,000`, `500`. */
val Denomination.faceLabel: String get() = groupThousands(won.toLong())

/** Helpers for the digits-only amount field. */
object AmountInput {
    /** Maximum number of digits accepted (99,999,999원), well inside [Int] range. */
    const val MAX_DIGITS: Int = 8

    /** Keeps only ASCII digits, drops leading zeros and truncates to [MAX_DIGITS]. */
    fun sanitize(text: String): String = text.filter { it in '0'..'9' }.trimStart('0').take(MAX_DIGITS)

    /** The amount typed as [digits] (output of [sanitize]); `null` when empty. */
    fun parse(digits: String): Won? = digits.toIntOrNull()?.let(::Won)
}

/**
 * Shows a digits-only field value with thousands separators (`100000` -> `100,000`) while the underlying text
 * stays digits only. The cursor mapping skips the inserted commas.
 */
object ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val n = digits.length
        val grouped = if (n == 0) "" else groupThousands(digits.toLong())
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + commasBefore(n, offset)

            override fun transformedToOriginal(offset: Int): Int =
                offset - grouped.take(offset.coerceIn(0, grouped.length)).count { it == ',' }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }

    /**
     * Number of commas at or before original cursor position [offset] in an [n]-digit string: a comma sits in front
     * of digit `i` (0 < i < n) when `(n - i) % 3 == 0`, and a cursor at `i` is placed after that comma.
     */
    internal fun commasBefore(n: Int, offset: Int): Int = (1..minOf(offset, n - 1)).count { (n - it) % 3 == 0 }
}

/** Remaining time for a hand: `5시간 12분 남음`, `12분 남음`, `1분 미만 남음`, or `만료됨`. */
fun formatRemaining(remaining: Duration): String {
    if (!remaining.isPositive()) return "만료됨"
    val totalMinutes = remaining.inWholeMinutes
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes < 1 -> "1분 미만 남음"
        hours == 0L -> "${minutes}분 남음"
        minutes == 0L -> "${hours}시간 남음"
        else -> "${hours}시간 ${minutes}분 남음"
    }
}

/** Korean one-letter weekday: 월 화 수 목 금 토 일. */
val DayOfWeek.koreanShort: String get() = "월화수목금토일"[isoDayNumber - 1].toString()

/** `11.2 (월)`. */
fun LocalDate.shortLabel(): String = "${month.number}.$day (${dayOfWeek.koreanShort})"

/** `14:05` when [instant] is on [today], otherwise `11.3 14:05`. */
fun formatTime(instant: Instant, timeZone: TimeZone, today: LocalDate): String {
    val local = instant.toLocalDateTime(timeZone)
    val hm = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return if (local.date == today) hm else "${local.date.month.number}.${local.date.day} $hm"
}

/** Display name of a notification source app: Samsung Wallet / Toss by name, anything else by its package. */
fun sourceAppLabel(packageName: String): String = when (packageName) {
    DefaultRules.SAMSUNG_WALLET -> "삼성월렛"
    DefaultRules.TOSS -> "토스"
    "manual" -> Strings.MANUAL_TITLE
    else -> packageName
}

/** Parser confidence as a percentage: `0.85` -> `85%`. */
fun formatConfidence(confidence: Float): String = "${(confidence * 100).toInt().coerceIn(0, 100)}%"

/** Counts as `50,000×1 + 10,000×2` (largest first, zero counts skipped); empty string for no counts. */
fun splitSummary(counts: Map<Denomination, Int>): String =
    Denomination.descending.filter { (counts[it] ?: 0) > 0 }.joinToString(" + ") { "${it.faceLabel}×${counts.getValue(it)}" }

/** `50,000×1 → 10,000×5`. */
fun exchangeSummary(from: Map<Denomination, Int>, to: Map<Denomination, Int>): String = "${splitSummary(from)} → ${splitSummary(to)}"
