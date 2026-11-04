package com.jim.realcash.android.samples

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Append-only JSONL file of captured notifications (`filesDir/notification-samples.jsonl` in the app).
 *
 * Blocking file I/O: call from a background dispatcher. All operations are serialised on one lock, so the listener
 * service and the debug screen can use the same instance concurrently.
 *
 * @param file the JSONL file; created on the first append.
 */
class NotificationSampleStore(val file: File) {
    private val lock = Any()
    private val _version = MutableStateFlow(0L)

    /** Incremented after every change (append / clear); screens reload when it changes. */
    val version: StateFlow<Long> = _version.asStateFlow()

    /** Appends [sample] as one line. */
    fun append(sample: NotificationSample) {
        val line = NotificationSampleCodec.encodeLine(sample)
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.appendText(line + "\n", Charsets.UTF_8)
        }
        _version.value += 1
    }

    /** Every stored sample, oldest first; malformed lines are skipped. */
    fun readAll(): List<NotificationSample> = NotificationSampleCodec.decodeAll(readText())

    /** The newest [limit] samples, newest first. */
    fun readLatest(limit: Int): List<NotificationSample> = readAll().takeLast(limit).asReversed()

    /** Number of stored samples. */
    fun count(): Int = readAll().size

    /** The raw file content ("" when there is no file). */
    fun readText(): String = synchronized(lock) {
        if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }

    /** Copies the file to [target] (for sharing a stable snapshot); returns false when there is nothing to copy. */
    fun copyTo(target: File): Boolean = synchronized(lock) {
        if (!file.exists() || file.length() == 0L) return@synchronized false
        target.parentFile?.mkdirs()
        file.copyTo(target, overwrite = true)
        true
    }

    /** Deletes every sample. */
    fun clear() {
        synchronized(lock) {
            if (file.exists()) file.delete()
        }
        _version.value += 1
    }

    /** Defaults. */
    companion object {
        /** File name inside `filesDir`. */
        const val FILE_NAME: String = "notification-samples.jsonl"
    }
}
