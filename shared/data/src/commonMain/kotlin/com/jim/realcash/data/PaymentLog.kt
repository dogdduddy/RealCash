package com.jim.realcash.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.jim.realcash.data.db.Detected_payment_log
import com.jim.realcash.data.db.RealCashDatabase
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Won
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What [PaymentIntake] did with a parsed payment notification. Stored by name; do not rename. */
enum class PaymentOutcome {
    /** Matched and settled automatically. */
    AUTO_SETTLED,

    /** Recorded as pending; the user has to confirm ("맞아요 / 아니에요"). */
    NEEDS_CONFIRM,

    /** Same payment as one already handled within the de-duplication window; nothing changed. */
    DUPLICATE,

    /** Parsed, but below [PaymentIntake]'s minimum confidence; nothing changed. */
    IGNORED_LOW_CONFIDENCE,

    /** A refund; units went back into the wallet. */
    REFUND,
}

/** One row of the detected-payment log, shown on the debug / sample screen. Never leaves the device. */
data class PaymentLogEntry(
    /** Id of the parsed payment. */
    val paymentId: PaymentId,
    /** Package name / app identifier of the notification. */
    val sourceApp: String,
    /** Parsed amount. */
    val amount: Won,
    /** Parsed merchant, if any. */
    val merchant: String?,
    /** When the notification was posted. */
    val detectedAt: Instant,
    /** Raw notification text. */
    val rawText: String,
    /** What the intake did with it, or `null` for a value written by a newer app version. */
    val outcome: PaymentOutcome?,
)

/** Access to the `detected_payment_log` table. Rows are written only by [PaymentIntake]. */
class PaymentLog internal constructor(
    private val database: RealCashDatabase,
    private val ioContext: CoroutineContext,
) {
    private val queries get() = database.realCashQueries

    /** The [limit] most recent entries, newest first. */
    suspend fun recent(limit: Int = DEFAULT_LIMIT): List<PaymentLogEntry> = withContext(ioContext) {
        queries.selectRecentPaymentLog(limit.toLong()).executeAsList()
    }.map { it.toEntry() }

    /** Observes the [limit] most recent entries, newest first; emits again after every write. */
    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<PaymentLogEntry>> =
        queries.selectRecentPaymentLog(limit.toLong()).asFlow().mapToList(ioContext).map { rows -> rows.map { it.toEntry() } }

    /** Number of logged notifications. */
    suspend fun count(): Long = withContext(ioContext) { queries.countPaymentLog().executeAsOne() }

    /** True if a row for [id] exists. */
    internal suspend fun exists(id: PaymentId): Boolean = withContext(ioContext) {
        queries.paymentLogExists(id.value).executeAsOne()
    }

    /** Records [payment] with [outcome]. A second write for the same payment id replaces the first. */
    internal suspend fun record(payment: DetectedPayment, outcome: PaymentOutcome) = withContext(ioContext) {
        queries.insertPaymentLog(
            payment_id = payment.id.value,
            source_app = payment.sourceApp,
            amount = payment.amount.value.toLong(),
            merchant = payment.merchant,
            detected_at_epoch_ms = payment.detectedAt.toEpochMilliseconds(),
            raw_text = payment.rawText,
            outcome = outcome.name,
        )
        Unit
    }

    /** Entries detected within `[from, to]` (both inclusive), oldest first. */
    internal suspend fun between(from: Instant, to: Instant): List<PaymentLogEntry> = withContext(ioContext) {
        queries.selectPaymentLogBetween(from.toEpochMilliseconds(), to.toEpochMilliseconds()).executeAsList()
    }.map { it.toEntry() }

    private fun Detected_payment_log.toEntry() = PaymentLogEntry(
        paymentId = PaymentId(payment_id),
        sourceApp = source_app,
        amount = Won(amount.toInt()),
        merchant = merchant,
        detectedAt = Instant.fromEpochMilliseconds(detected_at_epoch_ms),
        rawText = raw_text,
        outcome = PaymentOutcome.entries.firstOrNull { it.name == outcome },
    )

    /** Defaults. */
    companion object {
        /** Default number of entries returned by [recent] / [observeRecent]. */
        const val DEFAULT_LIMIT: Int = 100
    }
}
