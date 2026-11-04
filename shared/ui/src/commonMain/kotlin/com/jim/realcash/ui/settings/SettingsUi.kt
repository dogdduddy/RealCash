package com.jim.realcash.ui.settings

import com.jim.realcash.data.AppSettings
import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.parser.DefaultRules
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * What the 설정 tab renders, derived from [AppSettings] by [from] (pure; unit-tested).
 *
 * @property ttlHours the selected hand-TTL option, or `null` when the stored TTL is not one of [SettingsRules.TTL_HOURS].
 * @property threshold the auto-settle threshold as shown on the slider (clamped to its range and snapped to its step).
 * @property otherPackages enabled source apps other than Samsung Wallet and Toss, sorted.
 */
data class SettingsUi(
    val cycle: BudgetPeriodPolicy.Cycle,
    val startDay: Int,
    val pullPolicy: PullPolicy,
    val ttlHours: Int?,
    val threshold: Float,
    val samsungEnabled: Boolean,
    val tossEnabled: Boolean,
    val otherPackages: List<String>,
) {
    /** Builders. */
    companion object {
        /** Maps stored settings to the screen state. */
        fun from(s: AppSettings): SettingsUi = SettingsUi(
            cycle = s.budgetPeriodPolicy.cycle,
            startDay = s.budgetPeriodPolicy.startDay,
            pullPolicy = s.pullPolicy,
            ttlHours = SettingsRules.ttlOption(s.handTtl),
            threshold = SettingsRules.snapThreshold(s.autoSettleThreshold),
            samsungEnabled = DefaultRules.SAMSUNG_WALLET in s.enabledSourceApps,
            tossEnabled = DefaultRules.TOSS in s.enabledSourceApps,
            otherPackages = (s.enabledSourceApps - SettingsRules.KNOWN_APPS).sorted(),
        )
    }
}

/** Outcome of validating the "패키지 추가" field. */
sealed interface PackageInput {
    /** Nothing typed. */
    data object Empty : PackageInput

    /** Not a package-name-like identifier. */
    data object Invalid : PackageInput

    /** Already enabled. */
    data object Duplicate : PackageInput

    /** Can be added as [packageName] (trimmed). */
    data class Valid(val packageName: String) : PackageInput
}

/** The option sets and value rules of the 설정 tab. */
object SettingsRules {
    /** Hand TTL options in hours. */
    val TTL_HOURS: List<Int> = listOf(1, 3, 6, 12, 24)

    /** Slider range and step of the auto-settle threshold. */
    const val THRESHOLD_MIN: Float = 0.5f
    const val THRESHOLD_MAX: Float = 1.0f
    const val THRESHOLD_STEP: Float = 0.05f

    /** Discrete stops between min and max for the Material slider (`(max - min) / step - 1`). */
    const val THRESHOLD_SLIDER_STEPS: Int = 9

    /** Source apps with a dedicated toggle. */
    val KNOWN_APPS: Set<String> = setOf(DefaultRules.SAMSUNG_WALLET, DefaultRules.TOSS)

    private val PACKAGE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    /** The TTL option matching [ttl] exactly, or `null`. */
    fun ttlOption(ttl: Duration): Int? = TTL_HOURS.firstOrNull { it.hours == ttl }

    /**
     * Clamps [value] to the slider range and snaps it to the 0.05 grid.
     *
     * NOTE: a stored threshold below 0.5 (only possible through older data) is shown as 0.5 and is overwritten only
     * when the user moves the slider.
     */
    fun snapThreshold(value: Float): Float {
        val clamped = value.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
        return (clamped * 20f).roundToInt() / 20f
    }

    /** Policy after switching to [cycle]; the start day resets to 1 (Monday / the 1st) unless the cycle is unchanged. */
    fun withCycle(current: BudgetPeriodPolicy, cycle: BudgetPeriodPolicy.Cycle): BudgetPeriodPolicy =
        if (current.cycle == cycle) current else BudgetPeriodPolicy(cycle, startDay = 1)

    /** Policy with [day] as start day, coerced into the cycle's valid range (1..7 weekly, 1..28 monthly). */
    fun withStartDay(current: BudgetPeriodPolicy, day: Int): BudgetPeriodPolicy {
        val range = if (current.cycle == BudgetPeriodPolicy.Cycle.WEEKLY) 1..7 else 1..28
        return BudgetPeriodPolicy(current.cycle, day.coerceIn(range))
    }

    /** [apps] with [packageName] switched on or off. */
    fun toggle(apps: Set<String>, packageName: String, enabled: Boolean): Set<String> =
        if (enabled) apps + packageName else apps - packageName

    /** Validates the "패키지 추가" field against the currently enabled [apps]. */
    fun validatePackage(text: String, apps: Set<String>): PackageInput {
        val trimmed = text.trim()
        return when {
            trimmed.isEmpty() -> PackageInput.Empty
            !PACKAGE_PATTERN.matches(trimmed) -> PackageInput.Invalid
            trimmed in apps -> PackageInput.Duplicate
            else -> PackageInput.Valid(trimmed)
        }
    }
}
