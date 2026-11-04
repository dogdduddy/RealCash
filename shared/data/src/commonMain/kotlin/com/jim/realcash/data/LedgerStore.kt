package com.jim.realcash.data

import com.jim.realcash.data.db.RealCashDatabase
import com.jim.realcash.domain.ledger.LedgerEvent
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Append-only persistence of [LedgerEvent]s in the `ledger_event` table.
 *
 * The store does not validate events; [WalletRepository] runs every event through the reducer before it gets here.
 */
internal class LedgerStore(
    private val database: RealCashDatabase,
    private val ioContext: CoroutineContext,
    private val json: Json = DataJson,
) {
    private val queries get() = database.realCashQueries

    /** Appends one event. */
    suspend fun append(event: LedgerEvent) = append(listOf(event))

    /** Appends [events] in order, atomically (all or nothing). */
    suspend fun append(events: List<LedgerEvent>) {
        if (events.isEmpty()) return
        val rows = events.map(::encode)
        withContext(ioContext) {
            queries.transaction {
                rows.forEach { r -> queries.insertEvent(r.eventId, r.type, r.atEpochMs, r.payload) }
            }
        }
    }

    /** Every stored event, in append order. */
    suspend fun loadAll(): List<LedgerEvent> = withContext(ioContext) {
        queries.selectAllPayloads().executeAsList()
    }.map { json.decodeFromString(LedgerEvent.serializer(), it) }

    /** Number of stored events. */
    suspend fun count(): Long = withContext(ioContext) { queries.countEvents().executeAsOne() }

    private class Row(val eventId: String, val type: String, val atEpochMs: Long, val payload: String)

    private fun encode(event: LedgerEvent): Row {
        val element = json.encodeToJsonElement(LedgerEvent.serializer(), event)
        // The discriminator value is the event's @SerialName; reading it back keeps `type` and payload in sync.
        val type = element.jsonObject.getValue(json.configuration.classDiscriminator).jsonPrimitive.content
        return Row(
            eventId = event.id,
            type = type,
            atEpochMs = event.at.toEpochMilliseconds(),
            payload = json.encodeToString(JsonElement.serializer(), element),
        )
    }
}
