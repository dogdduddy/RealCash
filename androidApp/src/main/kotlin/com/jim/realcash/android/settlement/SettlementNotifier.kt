package com.jim.realcash.android.settlement

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jim.realcash.android.AppLog
import com.jim.realcash.android.MainActivity
import com.jim.realcash.android.R

/**
 * Posts our own notifications on the [CHANNEL_ID] ("정산") channel: the settlement result, or a confirmation request
 * with 맞아요 / 아니에요 actions handled by [PaymentActionReceiver]. Tapping a notification opens [MainActivity].
 */
class SettlementNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates the channel (idempotent). */
    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, SettlementNotificationText.CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = SettlementNotificationText.CHANNEL_DESCRIPTION }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Whether we may post notifications at all (permission on API 33+, and not blocked by the user). */
    fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    /** Posts or replaces the notification for [content.paymentId][SettlementNotificationContent.paymentId]. */
    fun show(content: SettlementNotificationContent) {
        if (!canPost()) return
        val id = notificationId(content.paymentId)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_realcash)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent())
            .setAutoCancel(!content.askConfirm)
            .setOnlyAlertOnce(true)
        if (content.askConfirm) {
            builder.addAction(0, SettlementNotificationText.ACTION_CONFIRM, actionIntent(content.paymentId, PaymentActionReceiver.ACTION_CONFIRM))
            builder.addAction(0, SettlementNotificationText.ACTION_REJECT, actionIntent(content.paymentId, PaymentActionReceiver.ACTION_REJECT))
        }
        try {
            manager.notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between canPost() and notify().
            AppLog.w("cannot post settlement notification", e)
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(paymentId: String, action: String): PendingIntent {
        val intent = Intent(context, PaymentActionReceiver::class.java)
            .setAction(action)
            .putExtra(PaymentActionReceiver.EXTRA_PAYMENT_ID, paymentId)
        // Distinct request code per (payment, action) so the extras of one notification never overwrite another's.
        val requestCode = 31 * notificationId(paymentId) + action.hashCode()
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Constants. */
    companion object {
        /** The "정산" channel. */
        const val CHANNEL_ID: String = "settlement"

        /** Stable notification id of a payment. */
        fun notificationId(paymentId: String): Int = paymentId.hashCode()
    }
}
