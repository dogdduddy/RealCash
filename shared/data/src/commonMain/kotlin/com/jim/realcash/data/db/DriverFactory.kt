package com.jim.realcash.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Creates the platform [SqlDriver] for [RealCashDatabase].
 *
 * Every `actual` creates (on first open) or migrates (on version change) the schema through
 * `RealCashDatabase.Schema`, so callers can hand the driver straight to [createDatabase].
 * Constructors are platform specific (file path / database name).
 */
expect class DriverFactory {
    /** Opens a driver whose schema is created or migrated to the current `RealCashDatabase.Schema` version. */
    fun createDriver(): SqlDriver
}

/** Wraps an already opened (and schema-initialised) [driver] in the generated database API. */
fun createDatabase(driver: SqlDriver): RealCashDatabase = RealCashDatabase(driver)
