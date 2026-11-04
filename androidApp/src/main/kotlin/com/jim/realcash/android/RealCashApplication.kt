package com.jim.realcash.android

import android.app.Application
import android.content.Context
import com.jim.realcash.android.samples.CollectorSettings
import com.jim.realcash.android.samples.NotificationSampleStore
import com.jim.realcash.android.settlement.SettlementNotifier
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide object graph: the single [RealCashData] (shared by the activity, the notification listener and the
 * notification action receiver), the app [CoroutineScope], and the sample collector.
 */
class RealCashApplication : Application() {

    /** Lives as long as the process; failures of one job never cancel the others and are only logged. */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> AppLog.w("uncaught in appScope", e) },
    )

    /** The data layer over the on-device SQLite database `realcash.db`. Opened lazily on first query. */
    val data: RealCashData by lazy { RealCashData(DriverFactory(this).createDriver()) }

    /** Captured notification samples (`filesDir/notification-samples.jsonl`). */
    val sampleStore: NotificationSampleStore by lazy {
        NotificationSampleStore(File(filesDir, NotificationSampleStore.FILE_NAME))
    }

    /** Sample collector on/off and package list. */
    val collectorSettings: CollectorSettings by lazy {
        CollectorSettings(getSharedPreferences(CollectorSettings.PREFS_NAME, MODE_PRIVATE))
    }

    /** Posts and updates the settlement notifications. */
    val settlementNotifier: SettlementNotifier by lazy { SettlementNotifier(this) }

    override fun onCreate() {
        super.onCreate()
        settlementNotifier.ensureChannel()
    }
}

/** The [RealCashApplication] behind any context of this app. */
val Context.realCash: RealCashApplication get() = applicationContext as RealCashApplication
