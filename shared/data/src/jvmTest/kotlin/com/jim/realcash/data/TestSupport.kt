package com.jim.realcash.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.util.IdGenerator
import com.jim.realcash.domain.util.SequentialIdGenerator
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Fixed reference instant for deterministic tests. */
val T0: Instant = Instant.parse("2026-11-04T09:00:00Z")

/** Manually advanced clock. */
class TestClock(var now: Instant = T0) : Clock {
    override fun now(): Instant = now

    /** Moves the clock forward by [d]. */
    fun advance(d: Duration) {
        now += d
    }
}

/** A fresh in-memory database. */
fun inMemoryDriver(): SqlDriver = DriverFactory().createDriver()

/** The data layer over [driver], running SQLite calls inline on the test thread. */
fun dataOf(
    driver: SqlDriver,
    clock: Clock = TestClock(),
    ids: IdGenerator = SequentialIdGenerator("id-"),
): RealCashData = RealCashData(driver, clock, ids, ioContext = EmptyCoroutineContext)

/** Number of rows in [table]. */
fun SqlDriver.rowCount(table: String): Long =
    executeQuery(null, "SELECT count(*) FROM $table", { c -> c.next(); QueryResult.Value(c.getLong(0)!!) }, 0).value

/** `type` column of every ledger row, in append order. */
fun SqlDriver.ledgerTypes(): List<String> = executeQuery(
    null,
    "SELECT type FROM ledger_event ORDER BY seq",
    { c ->
        val out = mutableListOf<String>()
        while (c.next().value) out += c.getString(0)!!
        QueryResult.Value(out)
    },
    0,
).value
