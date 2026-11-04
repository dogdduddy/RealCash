package com.jim.realcash.domain.parser

import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.IdGenerator
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** A notification as captured by the platform layer (Android listener or iOS Shortcuts intent). */
data class RawNotification(
    /** Android package name or iOS app identifier. */
    val sourceApp: String,
    /** Notification title. */
    val title: String?,
    /** Notification text. */
    val text: String?,
    /** Expanded text (Android `EXTRA_BIG_TEXT`), if any. */
    val bigText: String?,
    /** When the notification was posted. */
    val postedAt: Instant,
)

/**
 * One parsing rule, loadable from remote config.
 *
 * An empty [sourceApps] list makes the rule a wildcard that applies to any app (without the known-app bonus).
 */
@Serializable
data class ParserRule(
    /** Stable rule identifier. */
    val id: String,
    /** Package names / iOS identifiers this rule applies to. */
    val sourceApps: List<String>,
    /** Amount pattern; the first capture group that matched holds the digits (commas allowed). */
    val amountRegex: String,
    /** Merchant pattern; first capture group = merchant name. */
    val merchantRegex: String? = null,
    /** Keywords marking a cancellation / refund. */
    val refundKeywords: List<String> = listOf("취소", "승인취소", "환불"),
    /** Keywords marking a notification that is not a payment (top-ups, transfers, ...). */
    val ignoreKeywords: List<String> = emptyList(),
    /** Trust in this rule, `0..1`; multiplies the computed score. */
    val baseConfidence: Float,
)

/** Turns a raw notification into a detected payment, or `null` if it is not a payment. */
interface NotificationParser {
    /** Parses [n]; returns `null` when no rule applies, no amount is found, or an ignore keyword matches. */
    fun parse(n: RawNotification): DetectedPayment?
}

/**
 * [NotificationParser] driven by [ParserRule]s.
 *
 * Confidence = (0.6 for an extracted amount + 0.2 for an extracted merchant + 0.2 if the rule explicitly lists
 * the source app) × [ParserRule.baseConfidence], clamped to `0..1`. The auto-settle threshold is the caller's
 * choice (default 0.8). Rules listing the app are tried before wildcard rules; the first rule that extracts an
 * amount wins. Throws [IllegalArgumentException] on construction if a rule's regex does not compile.
 */
class RuleBasedParser(rules: List<ParserRule>, private val ids: IdGenerator) : NotificationParser {

    private class CompiledRule(val rule: ParserRule, val amount: Regex, val merchant: Regex?)

    private val compiled: List<CompiledRule> = rules.map { rule ->
        require(rule.baseConfidence in 0f..1f) { "rule ${rule.id}: baseConfidence must be in 0..1" }
        try {
            CompiledRule(rule, Regex(rule.amountRegex), rule.merchantRegex?.let(::Regex))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("rule ${rule.id}: invalid regex (${e.message})", e)
        }
    }

    override fun parse(n: RawNotification): DetectedPayment? {
        val text = listOfNotNull(n.title, n.text, n.bigText)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
        if (text.isEmpty()) return null

        val specific = compiled.filter { n.sourceApp in it.rule.sourceApps }
        val wildcard = compiled.filter { it.rule.sourceApps.isEmpty() }
        for (c in specific + wildcard) {
            if (c.rule.ignoreKeywords.any { it.isNotEmpty() && it in text }) return null
            val amount = extractAmount(c.amount, text) ?: continue
            val merchant = c.merchant?.find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.trim()
            val knownApp = c.rule.sourceApps.isNotEmpty()
            val score = AMOUNT_SCORE +
                (if (merchant != null) MERCHANT_SCORE else 0f) +
                (if (knownApp) KNOWN_APP_SCORE else 0f)
            return DetectedPayment(
                id = PaymentId(ids.next()),
                amount = amount,
                merchant = merchant,
                sourceApp = n.sourceApp,
                rawText = text,
                detectedAt = n.postedAt,
                confidence = (score * c.rule.baseConfidence).coerceIn(0f, 1f),
                isRefund = c.rule.refundKeywords.any { it.isNotEmpty() && it in text },
            )
        }
        return null
    }

    /**
     * NOTE: the spec says "first capture group = digits". To support alternations such as
     * `₩([0-9,]+)|([0-9,]+)원` with a single rule, the first capture group that actually matched is used.
     */
    private fun extractAmount(regex: Regex, text: String): Won? {
        val match = regex.find(text) ?: return null
        val digits = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return null
        val value = digits.filter { it.isDigit() }.toIntOrNull() ?: return null
        return if (value > 0) Won(value) else null
    }

    private companion object {
        const val AMOUNT_SCORE = 0.6f
        const val MERCHANT_SCORE = 0.2f
        const val KNOWN_APP_SCORE = 0.2f
    }
}
