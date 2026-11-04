package com.jim.realcash.android.samples

import android.content.SharedPreferences
import androidx.core.content.edit
import com.jim.realcash.domain.parser.DefaultRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** State of the notification sample collector. */
data class CollectorState(
    /** Whether captured notifications are written to the sample file. */
    val enabled: Boolean,
    /** Packages whose notifications are captured. */
    val packages: Set<String>,
)

/**
 * Sample collector settings persisted in [SharedPreferences]: an enabled flag and the package list.
 *
 * Collection is off on a fresh install (it writes raw notification text to disk; the user opts in on the
 * 알림 샘플 수집 screen).
 */
class CollectorSettings(private val prefs: SharedPreferences) {
    private val _state = MutableStateFlow(read())

    /** Current settings. */
    val state: StateFlow<CollectorState> = _state.asStateFlow()

    /** Turns collection on or off. */
    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _state.value = _state.value.copy(enabled = enabled)
    }

    /** Adds [packageName] (trimmed) if it is a valid package name; returns whether the list changed. */
    fun addPackage(packageName: String): Boolean {
        val name = packageName.trim()
        if (!PackageNames.isValid(name) || name in _state.value.packages) return false
        writePackages(_state.value.packages + name)
        return true
    }

    /** Removes [packageName]; returns whether the list changed. */
    fun removePackage(packageName: String): Boolean {
        if (packageName !in _state.value.packages) return false
        writePackages(_state.value.packages - packageName)
        return true
    }

    /** Puts the default package list back. */
    fun resetPackages() = writePackages(DEFAULT_PACKAGES)

    private fun writePackages(packages: Set<String>) {
        // NOTE: a fresh set instance every time; SharedPreferences must not be handed back the set it returned.
        prefs.edit { putStringSet(KEY_PACKAGES, HashSet(packages)) }
        _state.value = _state.value.copy(packages = packages.toSet())
    }

    private fun read(): CollectorState = CollectorState(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        packages = prefs.getStringSet(KEY_PACKAGES, null)?.toSet() ?: DEFAULT_PACKAGES,
    )

    /** Keys and defaults. */
    companion object {
        /** SharedPreferences file name. */
        const val PREFS_NAME: String = "notification_sample_collector"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PACKAGES = "packages"

        /**
         * Samsung Wallet, its Galaxy Watch payment plugin (watch payments notify on the phone) and Toss.
         * Card-company apps are added by the user on the screen.
         */
        val DEFAULT_PACKAGES: Set<String> = setOf(
            DefaultRules.SAMSUNG_WALLET,
            SAMSUNG_PAY_WATCH_PLUGIN,
            DefaultRules.TOSS,
        )

        /** Samsung Pay (Watch Plugin). Verify on a real device that it posts payment notifications. */
        const val SAMSUNG_PAY_WATCH_PLUGIN: String = "com.samsung.android.samsungpay.gear"
    }
}

/** Android package-name syntax check (two or more dot-separated segments, each starting with a letter). */
object PackageNames {
    private val PATTERN = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")

    /** Whether [name] looks like a package name. */
    fun isValid(name: String): Boolean = name.length <= 255 && PATTERN.matches(name)
}
