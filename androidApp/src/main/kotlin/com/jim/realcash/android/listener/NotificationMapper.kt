package com.jim.realcash.android.listener

import android.app.Notification
import com.jim.realcash.domain.parser.RawNotification
import kotlin.time.Instant

/**
 * Pure mapping from what a `StatusBarNotification` carries to the domain's [RawNotification].
 *
 * The service copies the few extras it needs into a plain map ([KEYS]) so that this mapping is testable on the JVM
 * without Android classes: values may be any [CharSequence] (spans are dropped) or, for [Notification.EXTRA_TEXT_LINES],
 * an array of them.
 */
object NotificationMapper {

    /** Extras the service copies out of the notification's bundle. */
    val KEYS: List<String> = listOf(
        Notification.EXTRA_TITLE,
        Notification.EXTRA_TEXT,
        Notification.EXTRA_BIG_TEXT,
        Notification.EXTRA_TEXT_LINES,
    )

    /**
     * Builds the raw notification.
     *
     * @param packageName the posting app (`sbn.packageName`), used as `sourceApp`.
     * @param postTime `sbn.postTime` in epoch milliseconds.
     * @param extras the values of [KEYS] (missing keys / null values are fine).
     */
    fun toRaw(packageName: String, postTime: Long, extras: Map<String, Any?>): RawNotification {
        // NOTE: InboxStyle notifications carry their expanded content in EXTRA_TEXT_LINES instead of EXTRA_BIG_TEXT.
        // Those lines are used as bigText when there is no big text, so no payment line is lost for such sources.
        val bigText = text(extras[Notification.EXTRA_BIG_TEXT]) ?: lines(extras[Notification.EXTRA_TEXT_LINES])
        return RawNotification(
            sourceApp = packageName,
            title = text(extras[Notification.EXTRA_TITLE]),
            text = text(extras[Notification.EXTRA_TEXT]),
            bigText = bigText,
            postedAt = Instant.fromEpochMilliseconds(postTime),
        )
    }

    /** A char sequence as a plain string; blank or non-text values become `null`. */
    private fun text(value: Any?): String? = (value as? CharSequence)?.toString()?.takeIf { it.isNotBlank() }

    /** Text lines joined by newlines; `null` when there is no non-blank line. */
    private fun lines(value: Any?): String? {
        val items: List<Any?> = when (value) {
            is Array<*> -> value.toList()
            is List<*> -> value
            else -> return null
        }
        return items.mapNotNull(::text).takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /**
     * What the listener does with a notification of another app.
     *
     * The sample collector sees everything posted by a listed package (ongoing and group summaries included, so we
     * learn how the payment apps post); the payment intake skips ongoing and group-summary notifications.
     * Whether the package is an enabled payment source is checked later, against the stored settings.
     */
    fun route(collectorEnabled: Boolean, inCollectorList: Boolean, isOngoing: Boolean, isGroupSummary: Boolean): Route =
        Route(sample = collectorEnabled && inCollectorList, intake = !isOngoing && !isGroupSummary)

    /** Result of [route]. */
    data class Route(
        /** Append a sample. */
        val sample: Boolean,
        /** Hand the notification to the payment intake (if its app is enabled). */
        val intake: Boolean,
    )
}
