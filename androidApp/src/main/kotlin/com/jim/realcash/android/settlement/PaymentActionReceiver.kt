package com.jim.realcash.android.settlement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jim.realcash.android.AppLog
import com.jim.realcash.android.realCash
import com.jim.realcash.data.RealCashData
import com.jim.realcash.domain.model.PaymentId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Handles the 맞아요 / 아니에요 actions of a confirmation notification: settles the pending payment with the current
 * pull policy, or rejects it, then replaces the notification with the outcome. Not exported.
 */
class PaymentActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val paymentId = intent.getStringExtra(EXTRA_PAYMENT_ID) ?: return
        val action = intent.action ?: return
        if (action != ACTION_CONFIRM && action != ACTION_REJECT) return
        val app = context.realCash
        val pending = goAsync()
        app.appScope.launch {
            try {
                val content = handle(app.data, paymentId, action)
                app.settlementNotifier.show(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("notification action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(data: RealCashData, paymentId: String, action: String): SettlementNotificationContent {
        val id = PaymentId(paymentId)
        val payment = data.wallet.load().pendingPayments[id]
            ?: return SettlementNotificationText.failed(paymentId, "이미 처리된 결제예요")
        return try {
            if (action == ACTION_CONFIRM) {
                val settled = data.wallet.settle(id, data.settings.load().pullPolicy)
                SettlementNotificationText.settled(payment, settled.settlement)
            } else {
                data.wallet.rejectPayment(id)
                SettlementNotificationText.rejected(paymentId, payment.amount, payment.merchant)
            }
        } catch (e: IllegalStateException) {
            // The data layer's messages are developer English; the notification keeps the generic Korean text.
            AppLog.w("notification action refused", e)
            SettlementNotificationText.failed(paymentId, null)
        } catch (e: IllegalArgumentException) {
            AppLog.w("notification action refused", e)
            SettlementNotificationText.failed(paymentId, null)
        }
    }

    /** Intent actions and extras. */
    companion object {
        /** 맞아요. */
        const val ACTION_CONFIRM: String = "com.jim.realcash.action.CONFIRM_PAYMENT"

        /** 아니에요. */
        const val ACTION_REJECT: String = "com.jim.realcash.action.REJECT_PAYMENT"

        /** The pending payment's id. */
        const val EXTRA_PAYMENT_ID: String = "paymentId"
    }
}
