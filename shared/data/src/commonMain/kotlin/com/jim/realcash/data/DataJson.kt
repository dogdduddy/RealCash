package com.jim.realcash.data

import kotlinx.serialization.json.Json

/**
 * The single [Json] configuration used for everything this module persists (ledger payloads, settings,
 * parser-rule overrides).
 *
 * The class discriminator is `"type"`, the kotlinx-serialization default, which is the format pinned by the
 * domain module's `LedgerSerializationTest` (`"type":"AllowanceReceived"`, ISO-8601 instants). Changing it
 * would make existing databases unreadable.
 */
internal val DataJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
