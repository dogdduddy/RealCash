package com.jim.realcash.data

import com.jim.realcash.domain.calc.PaymentDeduplicator
import com.jim.realcash.domain.ledger.HandReturned
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.domain.parser.NotificationParser
import com.jim.realcash.domain.parser.ParserRule
import com.jim.realcash.domain.parser.RawNotification
import com.jim.realcash.domain.parser.RuleBasedParser
import com.jim.realcash.domain.util.IdGenerator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Result of [PaymentIntake.onNotification]; carries what the UI and the platform notification need. */
sealed class IntakeResult {
    /** The parsed payment, or `null` when the notification was not a payment. */
    abstract val payment: DetectedPayment?

    /** Parsed amount, if any. */
    val amount: Won? get() = payment?.amount

    /** Parsed merchant, if any. */
    val merchant: String? get() = payment?.merchant

    /** Nothing was recorded. */
    data class Ignored(
        /** Why the notification was ignored. */
        val reason: IgnoreReason,
        override val payment: DetectedPayment? = null,
    ) : IntakeResult()

    /** The same payment was already handled within the de-duplication window; nothing changed. */
    data class Duplicate(override val payment: DetectedPayment) : IntakeResult()

    /** The payment was recorded and settled automatically. */
    data class AutoSettled(
        override val payment: DetectedPayment,
        /** The applied settlement. */
        val settlement: Settlement,
        /** The expired hand that was put back into the wallet just before settling, if any. */
        val expiredHand: HandReturned? = null,
    ) : IntakeResult() {
        /** Total of the change that went into the wallet. */
        val changeTotal: Won get() = settlement.changeUnits.total()

        /** Settlement flags (NOT_HAND_PICKED, ADDED_FROM_WALLET, INSUFFICIENT_FUNDS). */
        val flags: Set<SettlementFlag> get() = settlement.flags
    }

    /** The payment is pending; ask the user to confirm ("맞아요 / 아니에요"). */
    data class NeedsConfirm(
        override val payment: DetectedPayment,
        /** The expired hand that was put back into the wallet first, if any. */
        val expiredHand: HandReturned? = null,
        /** Set when the payment qualified for auto-settlement but settling failed; the message says why. */
        val autoSettleError: String? = null,
    ) : IntakeResult()

    /** A refund; [units] went back into the wallet. */
    data class Refunded(
        override val payment: DetectedPayment,
        /** Units added to the wallet. */
        val units: List<CashUnit>,
        /** The expired hand that was put back into the wallet first, if any. */
        val expiredHand: HandReturned? = null,
    ) : IntakeResult() {
        /** Total value returned to the wallet. */
        val refundTotal: Won get() = units.total()
    }
}

/** Why [PaymentIntake] ignored a notification. */
enum class IgnoreReason {
    /** The source app is not in `AppSettings.enabledSourceApps`. Not logged. */
    SOURCE_DISABLED,

    /** No rule extracted a payment (or an ignore keyword matched). Not logged. */
    NOT_A_PAYMENT,

    /** Parsed, but the confidence is below the intake's minimum. Logged as IGNORED_LOW_CONFIDENCE. */
    LOW_CONFIDENCE,
}

/**
 * The payment pipeline called by the Android `NotificationListenerService` and the iOS App Intent.
 *
 * For each notification (serialised; one at a time):
 * 1. ignore it unless its app is enabled in the settings;
 * 2. parse it with the active rules; nothing parsed -> [IntakeResult.Ignored]. The payment id is replaced by a
 *    content key of the raw notification ([NotificationKey]); if the log or the ledger already has that id
 *    (exact re-delivery) -> [IntakeResult.Duplicate], backfilling a missing log row from the ledger;
 * 3. below [minConfidence] -> logged, [IntakeResult.Ignored];
 * 4. de-duplicate against handled log entries within `AppSettings.dedupeWindow` -> [IntakeResult.Duplicate];
 * 5. return an expired hand to the wallet (expiry judged at the notification's time);
 * 6. refund -> [WalletRepository.refund], [IntakeResult.Refunded];
 * 7. confidence >= `AppSettings.autoSettleThreshold` -> record + settle with `AppSettings.pullPolicy`,
 *    [IntakeResult.AutoSettled];
 * 8. otherwise record as pending -> [IntakeResult.NeedsConfirm].
 *
 * Every parsed outcome is written to the `detected_payment_log` (see [PaymentLog]).
 *
 * @param minConfidence parsed payments below this confidence are dropped. Default 0 (never drops).
 */
class PaymentIntake(
    private val wallet: WalletRepository,
    private val settings: SettingsRepository,
    private val rules: ParserRulesRepository,
    private val log: PaymentLog,
    private val ids: IdGenerator,
    private val minConfidence: Float = 0f,
) {
    private val mutex = Mutex()
    private var parserRules: List<ParserRule>? = null
    private var parser: NotificationParser? = null

    /** Runs the pipeline for [raw]; see the class documentation. */
    suspend fun onNotification(raw: RawNotification): IntakeResult = mutex.withLock {
        val config = settings.load()
        if (raw.sourceApp !in config.enabledSourceApps) return@withLock IntakeResult.Ignored(IgnoreReason.SOURCE_DISABLED)

        val parsed = activeParser().parse(raw)
            ?: return@withLock IntakeResult.Ignored(IgnoreReason.NOT_A_PAYMENT)
        // Deterministic id: an exact re-delivery of the same notification yields the same PaymentId.
        val payment = parsed.copy(id = PaymentId(NotificationKey.of(raw)))

        // Exact re-delivery, checked before anything is written. Not logged again: the log row for this id
        // already exists (or is backfilled here) and must keep its original outcome.
        if (isAlreadyHandled(payment)) return@withLock IntakeResult.Duplicate(payment)

        if (payment.confidence < minConfidence) {
            log.record(payment, PaymentOutcome.IGNORED_LOW_CONFIDENCE)
            return@withLock IntakeResult.Ignored(IgnoreReason.LOW_CONFIDENCE, payment)
        }

        if (isDuplicate(payment, config)) {
            log.record(payment, PaymentOutcome.DUPLICATE)
            return@withLock IntakeResult.Duplicate(payment)
        }

        // NOTE: expiry is judged at the notification's posting time, not the processing time, so a delayed
        // notification for a payment made while the hand was still valid settles against that hand.
        val expiredHand = wallet.returnHandIfExpired(payment.detectedAt)

        if (payment.isRefund) {
            val refunded = try {
                wallet.refund(payment)
            } catch (e: IllegalStateException) {
                return@withLock fallbackToConfirm(payment, expiredHand, e)
            } catch (e: IllegalArgumentException) {
                return@withLock fallbackToConfirm(payment, expiredHand, e)
            }
            log.record(payment, PaymentOutcome.REFUND)
            return@withLock IntakeResult.Refunded(payment, refunded.units, expiredHand)
        }

        if (payment.confidence >= config.autoSettleThreshold) {
            val settled = try {
                wallet.recordAndSettle(payment, config.pullPolicy)
            } catch (e: IllegalStateException) {
                return@withLock fallbackToConfirm(payment, expiredHand, e)
            } catch (e: IllegalArgumentException) {
                return@withLock fallbackToConfirm(payment, expiredHand, e)
            }
            log.record(payment, PaymentOutcome.AUTO_SETTLED)
            return@withLock IntakeResult.AutoSettled(payment, settled.settlement, expiredHand)
        }

        wallet.recordDetectedPayment(payment)
        log.record(payment, PaymentOutcome.NEEDS_CONFIRM)
        IntakeResult.NeedsConfirm(payment, expiredHand)
    }

    /**
     * True if [payment]'s id is already in the log or in the ledger. When only the ledger has it (the app died
     * between the ledger write and the log write), the missing log row is backfilled with the outcome implied by
     * the ledger.
     */
    private suspend fun isAlreadyHandled(payment: DetectedPayment): Boolean {
        if (log.exists(payment.id)) return true
        val status = wallet.paymentStatusOf(payment.id) ?: return false
        val outcome = when (status) {
            PaymentLedgerStatus.SETTLED -> PaymentOutcome.AUTO_SETTLED
            PaymentLedgerStatus.PENDING, PaymentLedgerStatus.REJECTED -> PaymentOutcome.NEEDS_CONFIRM
            PaymentLedgerStatus.REFUNDED -> PaymentOutcome.REFUND
        }
        log.record(payment, outcome)
        return true
    }

    /**
     * NOTE: when automatic handling fails validation (nothing was persisted), the payment is recorded as pending
     * instead, so the user can still settle or reject it. If even that is refused (the ledger already knows the
     * payment), the notification is reported as a duplicate instead of throwing out of the platform service.
     */
    private suspend fun fallbackToConfirm(payment: DetectedPayment, expiredHand: HandReturned?, error: Exception): IntakeResult {
        try {
            wallet.recordDetectedPayment(payment)
        } catch (e: IllegalStateException) {
            return IntakeResult.Duplicate(payment)
        }
        log.record(payment, PaymentOutcome.NEEDS_CONFIRM)
        return IntakeResult.NeedsConfirm(payment, expiredHand, autoSettleError = error.message ?: error::class.simpleName)
    }

    /**
     * Compares against log entries detected within ±window of [payment] whose outcome was acted on
     * (AUTO_SETTLED, NEEDS_CONFIRM, REFUND). Earlier DUPLICATE / ignored entries are not chained.
     */
    private suspend fun isDuplicate(payment: DetectedPayment, config: AppSettings): Boolean {
        val window = config.dedupeWindow
        val recent = log.between(payment.detectedAt - window, payment.detectedAt + window)
            .filter { it.outcome in HANDLED_OUTCOMES }
            .map { entry ->
                DetectedPayment(
                    id = entry.paymentId,
                    amount = entry.amount,
                    merchant = entry.merchant,
                    sourceApp = entry.sourceApp,
                    rawText = entry.rawText,
                    detectedAt = entry.detectedAt,
                    confidence = 1f,
                    isRefund = entry.outcome == PaymentOutcome.REFUND,
                )
            }
        return PaymentDeduplicator.isDuplicate(payment, recent, window)
    }

    private suspend fun activeParser(): NotificationParser {
        val current = rules.load()
        val cached = parser
        if (cached != null && parserRules == current) return cached
        // Rules are validated by ParserRulesRepository, so construction does not throw here.
        return RuleBasedParser(current, ids).also {
            parser = it
            parserRules = current
        }
    }

    private companion object {
        val HANDLED_OUTCOMES = setOf(PaymentOutcome.AUTO_SETTLED, PaymentOutcome.NEEDS_CONFIRM, PaymentOutcome.REFUND)
    }
}
