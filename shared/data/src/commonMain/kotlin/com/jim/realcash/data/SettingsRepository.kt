package com.jim.realcash.data

import com.jim.realcash.domain.calc.PaymentDeduplicator
import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.Hand
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.parser.DefaultRules
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** User settings. Every field has a default; see [DEFAULT]. */
data class AppSettings(
    /** How allowance periods are cut. Default: weekly, starting Monday. */
    val budgetPeriodPolicy: BudgetPeriodPolicy = BudgetPeriodPolicy(BudgetPeriodPolicy.Cycle.MONTHLY, startDay = 1),
    /** How units are pulled from the wallet when no hand covers a payment. Default: LARGEST_FIRST. */
    val pullPolicy: PullPolicy = PullPolicy.LARGEST_FIRST,
    /** Time-to-live of a new hand. Default: 6 hours. Must be positive. */
    val handTtl: Duration = Hand.DEFAULT_TTL,
    /** Parser confidence at or above which a payment is settled without asking. Default 0.8; must be in `0..1`. */
    val autoSettleThreshold: Float = 0.8f,
    /** Apps whose notifications are processed at all. Default: Samsung Wallet and Toss. */
    val enabledSourceApps: Set<String> = setOf(DefaultRules.SAMSUNG_WALLET, DefaultRules.TOSS),
    /** Window within which two notifications of the same amount count as one payment. Default 120s; not negative. */
    val dedupeWindow: Duration = PaymentDeduplicator.DEFAULT_WINDOW,
) {
    init {
        require(handTtl.isPositive()) { "handTtl must be positive, was $handTtl" }
        require(autoSettleThreshold in 0f..1f) { "autoSettleThreshold must be in 0..1, was $autoSettleThreshold" }
        require(!dedupeWindow.isNegative()) { "dedupeWindow must not be negative, was $dedupeWindow" }
    }

    /** Defaults. */
    companion object {
        /** Settings of a fresh install. */
        val DEFAULT: AppSettings = AppSettings()
    }
}

/**
 * Typed settings stored in the `app_setting` table (one key per field, string-encoded).
 *
 * A missing or unreadable value falls back to the field's default, so a corrupted row never blocks the app.
 * [settings] holds [AppSettings.DEFAULT] until [load] (or any write) has run.
 */
class SettingsRepository internal constructor(
    private val store: SettingsStore,
    private val json: Json = DataJson,
) {
    private val mutex = Mutex()
    private val _settings = MutableStateFlow(AppSettings.DEFAULT)
    private var loaded = false

    /** Current settings. */
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Loads the stored settings if that has not happened yet; returns them. Idempotent. */
    suspend fun load(): AppSettings = mutex.withLock { ensureLoaded() }

    /** Applies [transform] to the current settings and persists every field that changed. Returns the result. */
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings = mutex.withLock {
        val current = ensureLoaded()
        val next = transform(current)
        val currentEncoded = encode(current)
        val changed = encode(next).filter { (k, v) -> currentEncoded[k] != v }
        store.putAll(changed)
        _settings.value = next
        next
    }

    /** Sets [AppSettings.budgetPeriodPolicy]. */
    suspend fun setBudgetPeriodPolicy(value: BudgetPeriodPolicy) = update { it.copy(budgetPeriodPolicy = value) }

    /** Sets [AppSettings.pullPolicy]. */
    suspend fun setPullPolicy(value: PullPolicy) = update { it.copy(pullPolicy = value) }

    /** Sets [AppSettings.handTtl]. */
    suspend fun setHandTtl(value: Duration) = update { it.copy(handTtl = value) }

    /** Sets [AppSettings.autoSettleThreshold]. */
    suspend fun setAutoSettleThreshold(value: Float) = update { it.copy(autoSettleThreshold = value) }

    /** Sets [AppSettings.enabledSourceApps]. */
    suspend fun setEnabledSourceApps(value: Set<String>) = update { it.copy(enabledSourceApps = value) }

    /** Sets [AppSettings.dedupeWindow]. */
    suspend fun setDedupeWindow(value: Duration) = update { it.copy(dedupeWindow = value) }

    /** Must be called with [mutex] held. */
    private suspend fun ensureLoaded(): AppSettings {
        if (!loaded) {
            _settings.value = decode(store.getAll())
            loaded = true
        }
        return _settings.value
    }

    private val appsSerializer = ListSerializer(String.serializer())

    private fun encode(s: AppSettings): Map<String, String> = mapOf(
        KEY_BUDGET_PERIOD to json.encodeToString(BudgetPeriodPolicy.serializer(), s.budgetPeriodPolicy),
        KEY_PULL_POLICY to s.pullPolicy.name,
        KEY_HAND_TTL to s.handTtl.toIsoString(),
        KEY_AUTO_SETTLE_THRESHOLD to s.autoSettleThreshold.toString(),
        KEY_ENABLED_SOURCE_APPS to json.encodeToString(appsSerializer, s.enabledSourceApps.sorted()),
        KEY_DEDUPE_WINDOW to s.dedupeWindow.toIsoString(),
    )

    private fun decode(raw: Map<String, String>): AppSettings {
        val d = AppSettings.DEFAULT
        fun <T> read(key: String, default: T, parse: (String) -> T?): T {
            val value = raw[key] ?: return default
            return try {
                parse(value) ?: default
            } catch (e: Exception) {
                // NOTE: unreadable values (corruption, older/newer format) silently fall back to the default.
                default
            }
        }
        return AppSettings(
            budgetPeriodPolicy = read(KEY_BUDGET_PERIOD, d.budgetPeriodPolicy) {
                json.decodeFromString(BudgetPeriodPolicy.serializer(), it)
            },
            pullPolicy = read(KEY_PULL_POLICY, d.pullPolicy) { v -> PullPolicy.entries.firstOrNull { it.name == v } },
            handTtl = read(KEY_HAND_TTL, d.handTtl) { Duration.parseIsoString(it).takeIf { t -> t.isPositive() } },
            autoSettleThreshold = read(KEY_AUTO_SETTLE_THRESHOLD, d.autoSettleThreshold) {
                it.toFloatOrNull()?.takeIf { t -> t in 0f..1f }
            },
            enabledSourceApps = read(KEY_ENABLED_SOURCE_APPS, d.enabledSourceApps) {
                json.decodeFromString(appsSerializer, it).toSet()
            },
            dedupeWindow = read(KEY_DEDUPE_WINDOW, d.dedupeWindow) {
                Duration.parseIsoString(it).takeIf { w -> !w.isNegative() }
            },
        )
    }

    internal companion object {
        const val KEY_BUDGET_PERIOD = "settings.budget_period_policy"
        const val KEY_PULL_POLICY = "settings.pull_policy"
        const val KEY_HAND_TTL = "settings.hand_ttl"
        const val KEY_AUTO_SETTLE_THRESHOLD = "settings.auto_settle_threshold"
        const val KEY_ENABLED_SOURCE_APPS = "settings.enabled_source_apps"
        const val KEY_DEDUPE_WINDOW = "settings.dedupe_window"
    }
}
