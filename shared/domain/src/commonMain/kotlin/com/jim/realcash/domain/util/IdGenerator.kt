package com.jim.realcash.domain.util

import kotlin.uuid.Uuid

/** Source of unique string identifiers, injected so that domain logic stays deterministic in tests. */
fun interface IdGenerator {
    /** Returns a new identifier that has never been returned before by this generator. */
    fun next(): String
}

/** Production [IdGenerator] backed by random (version 4) UUIDs. */
object UuidIdGenerator : IdGenerator {
    /** Returns a random UUID string. */
    override fun next(): String = Uuid.random().toString()
}

/** Deterministic [IdGenerator] that yields `prefix1`, `prefix2`, ... Intended for tests and previews. */
class SequentialIdGenerator(private val prefix: String = "id-", start: Long = 1) : IdGenerator {
    private var counter = start

    /** Returns the next identifier in the sequence. */
    override fun next(): String = "$prefix${counter++}"
}
