package com.jim.realcash.android.listener

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jim.realcash.android.AppLog
import com.jim.realcash.android.RealCashApplication
import com.jim.realcash.android.samples.NotificationSample
import com.jim.realcash.android.settlement.SettlementNotificationText
import com.jim.realcash.domain.parser.ParserRule
import com.jim.realcash.domain.parser.RawNotification
import com.jim.realcash.domain.parser.RuleBasedParser
import com.jim.realcash.domain.util.UuidIdGenerator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads payment notifications of other apps (special app access "알림 접근"). Everything stays on the device.
 *
 * For each posted notification of another app (our own are skipped):
 * 1. if the sample collector is on and the package is in its list, a [NotificationSample] is appended, whether or
 *    not it parses, including ongoing and group-summary notifications (with those flags and the channel id);
 * 2. ongoing and group-summary notifications stop here; otherwise, if the package is in
 *    `AppSettings.enabledSourceApps`, the notification goes through `PaymentIntake` and the result is shown as our
 *    own settlement notification.
 *
 * Nothing ever throws out of the service: failures are logged and the next notification is handled normally.
 */
class PaymentNotificationListenerService : NotificationListenerService() {

    private val app: RealCashApplication get() = application as RealCashApplication

    override fun onListenerConnected() {
        _connected.value = true
    }

    override fun onListenerDisconnected() {
        _connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null || sbn.packageName == packageName) return
            val notification = sbn.notification ?: return
            val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
            val collector = app.collectorSettings.state.value
            val route = NotificationMapper.route(
                collectorEnabled = collector.enabled,
                inCollectorList = sbn.packageName in collector.packages,
                isOngoing = sbn.isOngoing,
                isGroupSummary = isGroupSummary,
            )
            if (!route.sample && !route.intake) return
            val raw = NotificationMapper.toRaw(sbn.packageName, sbn.postTime, extrasOf(notification.extras))
            val meta = PostedAs(sbn.isOngoing, isGroupSummary, notification.channelId)
            handle(raw, route, meta)
        } catch (e: Exception) {
            AppLog.w("onNotificationPosted failed", e)
        }
    }

    /** How the app posted the notification (recorded in samples). */
    private class PostedAs(val isOngoing: Boolean, val isGroupSummary: Boolean, val channelId: String?)

    private fun handle(raw: RawNotification, route: NotificationMapper.Route, meta: PostedAs) {
        val app = app
        app.appScope.launch {
            if (route.sample) {
                guarded("sample capture") { captureSample(app, raw, meta) }
            }
            if (route.intake) {
                guarded("payment intake") { runIntake(app, raw) }
            }
        }
    }

    private suspend fun captureSample(app: RealCashApplication, raw: RawNotification, meta: PostedAs) {
        val parsed = try {
            parserFor(app.data.parserRules.load()).parse(raw)
        } catch (e: Exception) {
            AppLog.w("sample parse failed", e)
            null
        }
        val sample = NotificationSample.of(raw, parsed, meta.isOngoing, meta.isGroupSummary, meta.channelId)
        withContext(Dispatchers.IO) { app.sampleStore.append(sample) }
    }

    private suspend fun runIntake(app: RealCashApplication, raw: RawNotification) {
        val settings = app.data.settings.load()
        if (raw.sourceApp !in settings.enabledSourceApps) return
        val result = app.data.intake.onNotification(raw)
        SettlementNotificationText.of(result)?.let(app.settlementNotifier::show)
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("$what failed", e)
        }
    }

    /** Shares one parser per rule list between captures (the intake keeps its own). */
    private fun parserFor(rules: List<ParserRule>): RuleBasedParser = synchronized(parserLock) {
        val cached = cachedParser
        if (cached != null && cachedRules == rules) return cached
        RuleBasedParser(rules, UuidIdGenerator).also {
            cachedParser = it
            cachedRules = rules
        }
    }

    /** Listener state visible to the 알림 접근 screen. */
    companion object {
        private val _connected = MutableStateFlow(false)

        /** Whether the system has bound and connected the listener in this process. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val parserLock = Any()
        private var cachedParser: RuleBasedParser? = null
        private var cachedRules: List<ParserRule>? = null

        /** Copies [NotificationMapper.KEYS] out of [extras]; unreadable values are left out. */
        internal fun extrasOf(extras: Bundle?): Map<String, Any?> {
            if (extras == null) return emptyMap()
            return buildMap {
                for (key in NotificationMapper.KEYS) {
                    val value = try {
                        if (key == Notification.EXTRA_TEXT_LINES) extras.getCharSequenceArray(key) else extras.getCharSequence(key)
                    } catch (e: RuntimeException) {
                        // A bundle with an unknown Parcelable or a value of another type; skip that key.
                        null
                    }
                    put(key, value)
                }
            }
        }
    }
}
