package com.jim.realcash.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/**
 * JVM driver factory backed by `sqlite-jdbc`.
 *
 * @property url JDBC url, e.g. `jdbc:sqlite:/path/to/realcash.db`, or [JdbcSqliteDriver.IN_MEMORY] (the default)
 * for a throw-away database used by tests and desktop previews.
 */
actual class DriverFactory(private val url: String = JdbcSqliteDriver.IN_MEMORY) {

    /** Opens the driver; `JdbcSqliteDriver` creates or migrates the schema using SQLite's `user_version`. */
    actual fun createDriver(): SqlDriver = JdbcSqliteDriver(url, Properties(), RealCashDatabase.Schema)

    /** Factory helpers. */
    companion object {
        /** A factory for the SQLite database file at [path]. */
        fun forFile(path: String): DriverFactory = DriverFactory("jdbc:sqlite:$path")
    }
}
