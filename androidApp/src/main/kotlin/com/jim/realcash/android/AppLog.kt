package com.jim.realcash.android

import android.util.Log

/** Logging for the platform layer. Never logs notification content (it may contain card details). */
object AppLog {
    /** Log tag. */
    const val TAG: String = "RealCash"

    /** Warning with an optional cause. */
    fun w(message: String, e: Throwable? = null) {
        Log.w(TAG, message, e)
    }
}
