package com.jim.realcash.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android driver factory backed by the framework SQLite (`SupportSQLiteOpenHelper`).
 *
 * The database file lives in the app's private database directory and never leaves the device.
 *
 * @property context any context; the application context is used so the factory never leaks an activity.
 * @property name database file name.
 */
actual class DriverFactory(context: Context, private val name: String = DEFAULT_NAME) {
    private val appContext: Context = context.applicationContext ?: context

    /** Opens the driver; the open helper creates or migrates the schema when the database is first used. */
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(RealCashDatabase.Schema, appContext, name)

    /** Defaults. */
    companion object {
        /** Default database file name. */
        const val DEFAULT_NAME: String = "realcash.db"
    }
}
