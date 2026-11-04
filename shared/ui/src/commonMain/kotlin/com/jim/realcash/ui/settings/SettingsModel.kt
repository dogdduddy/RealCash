package com.jim.realcash.ui.settings

import com.jim.realcash.data.SettingsRepository
import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Strings
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State holder of the 설정 tab. Every change is written immediately; a failure shows a snackbar. */
class SettingsModel(
    private val scope: CoroutineScope,
    private val env: AppEnvironment,
) {
    private val repo: SettingsRepository = env.data.settings
    private val _packageText = MutableStateFlow("")
    private val _packageError = MutableStateFlow<String?>(null)

    /** The screen state. */
    val ui: StateFlow<SettingsUi> = repo.settings.map(SettingsUi::from)
        .stateIn(scope, SharingStarted.Eagerly, SettingsUi.from(repo.settings.value))

    /** Text of the "패키지 추가" field. */
    val packageText: StateFlow<String> = _packageText.asStateFlow()

    /** Validation message under the field, if any. */
    val packageError: StateFlow<String?> = _packageError.asStateFlow()

    /** 예산 주기 (주간 / 월간). */
    fun setCycle(cycle: BudgetPeriodPolicy.Cycle) = persist {
        setBudgetPeriodPolicy(SettingsRules.withCycle(settings.value.budgetPeriodPolicy, cycle))
    }

    /** 시작 요일 (weekly, ISO 1..7) / 시작일 (monthly, 1..28). */
    fun setStartDay(day: Int) = persist {
        setBudgetPeriodPolicy(SettingsRules.withStartDay(settings.value.budgetPeriodPolicy, day))
    }

    /** 자동 차감 방식. */
    fun setPullPolicy(policy: PullPolicy) = persist { setPullPolicy(policy) }

    /** 손에 든 돈 유지 시간. */
    fun setTtlHours(hours: Int) = persist { setHandTtl(hours.hours) }

    /** 자동 정산 신뢰도 (snapped to the slider grid). */
    fun setThreshold(value: Float) = persist { setAutoSettleThreshold(SettingsRules.snapThreshold(value)) }

    /** A source app toggle. */
    fun setSourceApp(packageName: String, enabled: Boolean) = persist {
        setEnabledSourceApps(SettingsRules.toggle(settings.value.enabledSourceApps, packageName, enabled))
    }

    /** The "패키지 추가" field changed. */
    fun onPackageTextChange(text: String) {
        _packageText.value = text
        _packageError.value = null
    }

    /** "추가": validates the field and enables the package. */
    fun addPackage() {
        when (val input = SettingsRules.validatePackage(_packageText.value, repo.settings.value.enabledSourceApps)) {
            PackageInput.Empty -> Unit
            PackageInput.Invalid -> _packageError.value = Strings.SET_PACKAGE_INVALID
            PackageInput.Duplicate -> _packageError.value = Strings.SET_PACKAGE_DUPLICATE
            is PackageInput.Valid -> {
                _packageText.value = ""
                setSourceApp(input.packageName, enabled = true)
            }
        }
    }

    private fun persist(block: suspend SettingsRepository.() -> Unit) {
        env.haptics.tap()
        scope.launch {
            try {
                repo.block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Any storage or validation failure: keep the old value (the flow still holds it) and say so.
                env.messages.show(Strings.failed(e.message))
            }
        }
    }
}
