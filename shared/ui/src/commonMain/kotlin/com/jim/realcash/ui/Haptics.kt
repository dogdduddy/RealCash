package com.jim.realcash.ui

/**
 * Tactile feedback hooks, implemented per platform (iOS: UIKit feedback generators; desktop: no-op).
 *
 * Kept to three intents so that screens express *why* they vibrate, not *how*.
 */
interface Haptics {
    /** Light tap: a stepper step, a chip selection. */
    fun tap()

    /** Success: money went into the wallet, a payment was settled. */
    fun success()

    /** Picking up a bill or coin (used by drag & drop in T5b). */
    fun pickUp()

    /** Haptics that do nothing (desktop, previews, tests). */
    object None : Haptics {
        override fun tap() = Unit
        override fun success() = Unit
        override fun pickUp() = Unit
    }
}
