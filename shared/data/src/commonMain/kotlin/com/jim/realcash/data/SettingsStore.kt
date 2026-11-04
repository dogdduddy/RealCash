package com.jim.realcash.data

import com.jim.realcash.data.db.RealCashDatabase
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/** Raw key/value access to the `app_setting` table, shared by [SettingsRepository] and [ParserRulesRepository]. */
internal class SettingsStore(
    private val database: RealCashDatabase,
    private val ioContext: CoroutineContext,
) {
    private val queries get() = database.realCashQueries

    /** Value stored under [key], or `null`. */
    suspend fun get(key: String): String? = withContext(ioContext) {
        queries.selectSetting(key).executeAsOneOrNull()
    }

    /** Every stored key/value pair. */
    suspend fun getAll(): Map<String, String> = withContext(ioContext) {
        queries.selectAllSettings().executeAsList().associate { it.key to it.value_ }
    }

    /** Writes all [values] (`null` deletes the key) in one transaction. */
    suspend fun putAll(values: Map<String, String?>) {
        if (values.isEmpty()) return
        withContext(ioContext) {
            queries.transaction {
                values.forEach { (k, v) -> if (v == null) queries.deleteSetting(k) else queries.upsertSetting(k, v) }
            }
        }
    }

    /** Writes or deletes a single key. */
    suspend fun put(key: String, value: String?) = putAll(mapOf(key to value))
}
