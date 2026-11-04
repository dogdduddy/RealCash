package com.jim.realcash.android.samples

import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.parser.RawNotification
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One captured notification (Phase 0 sample) as stored in `notification-samples.jsonl`, one JSON object per line.
 *
 * The parse fields are what the active parser rules extracted at capture time, or `null` when nothing parsed.
 */
@Serializable
data class NotificationSample(
    /** Posting app. */
    val packageName: String,
    /** `sbn.postTime` as ISO-8601 (UTC), e.g. `2026-11-04T03:22:00.123Z`. */
    val postTime: String,
    /** `EXTRA_TITLE`. */
    val title: String?,
    /** `EXTRA_TEXT`. */
    val text: String?,
    /** `EXTRA_BIG_TEXT` (or InboxStyle lines). */
    val bigText: String?,
    /** Parsed amount in won. */
    val parsedAmount: Int? = null,
    /** Parsed merchant. */
    val parsedMerchant: String? = null,
    /** Parser confidence `0..1`. */
    val parsedConfidence: Float? = null,
    /** Whether the parser saw a refund / cancellation keyword (`null` when nothing parsed). */
    val parsedRefund: Boolean? = null,
    /** `sbn.isOngoing` (such notifications are sampled but never go through the intake). */
    val isOngoing: Boolean = false,
    /** `Notification.FLAG_GROUP_SUMMARY` set (sampled, never go through the intake). */
    val isGroupSummary: Boolean = false,
    /** `Notification.channelId` of the posting app (API 26+), if any. */
    val channelId: String? = null,
) {
    /** Whether the active rules extracted a payment. */
    val parsed: Boolean get() = parsedAmount != null

    /** Factories. */
    companion object {
        /**
         * A sample of [raw] with the parse result [parsed] (or `null`) and how the app posted it
         * ([isOngoing], [isGroupSummary], [channelId]).
         */
        fun of(
            raw: RawNotification,
            parsed: DetectedPayment?,
            isOngoing: Boolean = false,
            isGroupSummary: Boolean = false,
            channelId: String? = null,
        ): NotificationSample = NotificationSample(
            packageName = raw.sourceApp,
            postTime = raw.postedAt.toString(),
            title = raw.title,
            text = raw.text,
            bigText = raw.bigText,
            parsedAmount = parsed?.amount?.value,
            parsedMerchant = parsed?.merchant,
            parsedConfidence = parsed?.confidence,
            parsedRefund = parsed?.isRefund,
            isOngoing = isOngoing,
            isGroupSummary = isGroupSummary,
            channelId = channelId,
        )
    }
}

/** JSON Lines encoding of [NotificationSample]s. */
object NotificationSampleCodec {
    private val json = Json {
        // Every field is always written (nulls included) so that each line has the same shape for tooling.
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    private val prettyJson = Json(json) { prettyPrint = true }

    /** One line (no newline inside: JSON escapes control characters in strings). */
    fun encodeLine(sample: NotificationSample): String = json.encodeToString(NotificationSample.serializer(), sample)

    /** Decodes one line; `null` for blank or malformed lines. */
    fun decodeLine(line: String): NotificationSample? {
        if (line.isBlank()) return null
        return try {
            json.decodeFromString(NotificationSample.serializer(), line)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            null
        }
    }

    /** Decodes a whole JSONL text, skipping malformed lines. */
    fun decodeAll(text: String): List<NotificationSample> = text.lineSequence().mapNotNull(::decodeLine).toList()

    /** A pretty-printed JSON array (for the clipboard). */
    fun encodeArray(samples: List<NotificationSample>): String =
        prettyJson.encodeToString(ListSerializer(NotificationSample.serializer()), samples)
}
