package com.jim.realcash.data

import app.cash.sqldelight.db.SqlDriver
import com.jim.realcash.data.db.RealCashDatabase
import com.jim.realcash.data.db.createDatabase
import com.jim.realcash.domain.util.IdGenerator
import com.jim.realcash.domain.util.UuidIdGenerator
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Hand-wired object graph of the data layer (no DI framework). Create one per process and share it between
 * the UI and the platform notification service.
 *
 * @param driver an opened driver, normally from `DriverFactory.createDriver()`.
 * @param clock time source for event timestamps and hand expiry.
 * @param ids id source for events, units, hands and parsed payments.
 * @param ioContext where blocking SQLite calls run.
 */
class RealCashData(
    driver: SqlDriver,
    clock: Clock = Clock.System,
    /** Identifier source shared by the repositories; the UI uses it for manual entries. */
    val ids: IdGenerator = UuidIdGenerator,
    ioContext: CoroutineContext = Dispatchers.IO,
) {
    /** The generated database API. */
    val database: RealCashDatabase = createDatabase(driver)

    private val settingsStore = SettingsStore(database, ioContext)
    internal val ledgerStore = LedgerStore(database, ioContext)

    /** Wallet state and every wallet mutation. */
    val wallet: WalletRepository = WalletRepository(ledgerStore, clock, ids)

    /** Typed user settings. */
    val settings: SettingsRepository = SettingsRepository(settingsStore)

    /** Parser rules in effect (bundled or override). */
    val parserRules: ParserRulesRepository = ParserRulesRepository(settingsStore)

    /** Log of parsed payment notifications. */
    val paymentLog: PaymentLog = PaymentLog(database, ioContext)

    /** Notification pipeline for the platform services. */
    val intake: PaymentIntake = PaymentIntake(wallet, settings, parserRules, paymentLog, ids)
}
