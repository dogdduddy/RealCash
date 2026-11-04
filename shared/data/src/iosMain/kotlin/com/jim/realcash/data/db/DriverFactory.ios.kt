package com.jim.realcash.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS driver factory backed by SQLDelight's native driver (SQLiter).
 *
 * @property name database file name inside the app's default database directory.
 */
actual class DriverFactory(private val name: String = DEFAULT_NAME) {

    /** Opens the driver; `NativeSqliteDriver` creates or migrates the schema on open. */
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(RealCashDatabase.Schema, name)

    /** Defaults. */
    companion object {
        /** Default database file name. */
        const val DEFAULT_NAME: String = "realcash.db"
    }
}
