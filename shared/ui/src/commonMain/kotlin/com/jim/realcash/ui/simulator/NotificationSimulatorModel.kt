package com.jim.realcash.ui.simulator

import com.jim.realcash.data.IntakeResult
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.parser.DefaultRules
import com.jim.realcash.domain.parser.RawNotification
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.format.groupThousands
import com.jim.realcash.ui.launchCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Source app choices of the simulator. */
enum class SimSource(
    /** Package name sent with the notification; `null` = typed by the user. */
    val packageName: String?,
) {
    /** Samsung Wallet. */
    SAMSUNG(DefaultRules.SAMSUNG_WALLET),

    /** Toss. */
    TOSS(DefaultRules.TOSS),

    /** Any package typed by the user. */
    CUSTOM(null),
}

/** Simulator form. */
data class SimulatorForm(
    /** Chosen source app. */
    val source: SimSource = SimSource.SAMSUNG,
    /** Package typed for [SimSource.CUSTOM]. */
    val customPackage: String = "",
    /** Notification title. */
    val title: String = "",
    /** Notification text. */
    val text: String = "",
) {
    /** Package the notification will carry. */
    val packageName: String get() = source.packageName ?: customPackage.trim()

    /** True when the form can be sent. */
    val canSend: Boolean get() = packageName.isNotEmpty() && (title.isNotBlank() || text.isNotBlank())
}

/** Last result shown under the form. */
data class SimulatorResult(
    /** The raw result. */
    val result: IntakeResult,
    /** The result in words. */
    val summary: IntakeResultSummary,
)

/** Preset texts matching the placeholder formats of [DefaultRules] (guessed formats, see the rule comments). */
object SimulatorPresets {
    /** Samsung Wallet: "스타벅스 강남점에서 6,500원 결제". */
    fun samsung(): SimulatorForm = SimulatorForm(SimSource.SAMSUNG, title = "결제 완료", text = "스타벅스 강남점에서 6,500원 결제")

    /** Toss: "12,000원 결제 | 김밥천국 역삼점". */
    fun toss(): SimulatorForm = SimulatorForm(SimSource.TOSS, title = "토스페이", text = "12,000원 결제 | 김밥천국 역삼점")

    /** Samsung Wallet cancellation of [amount] at [merchant]. */
    fun samsungCancel(amount: Won, merchant: String): SimulatorForm =
        SimulatorForm(SimSource.SAMSUNG, title = "결제 취소", text = "${merchant}에서 ${groupThousands(amount.value.toLong())}원 결제 취소")
}

/**
 * State holder of the notification simulator (screen 4): builds a [RawNotification] from the form and runs it through
 * `PaymentIntake.onNotification`, exactly like the platform notification services will.
 */
class NotificationSimulatorModel(
    private val scope: CoroutineScope,
    private val env: AppEnvironment,
) {
    private val _form = MutableStateFlow(SimulatorPresets.samsung())
    private val _result = MutableStateFlow<SimulatorResult?>(null)
    private val _sending = MutableStateFlow(false)

    /** Last non-refund payment sent, used by the cancellation preset. */
    private var lastPayment: Pair<Won, String> = Won(6_500) to "스타벅스 강남점"

    /** The form. */
    val form: StateFlow<SimulatorForm> = _form.asStateFlow()

    /** The last result, if any. */
    val result: StateFlow<SimulatorResult?> = _result.asStateFlow()

    /** True while a notification is being processed. */
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Replaces the form. */
    fun update(transform: (SimulatorForm) -> SimulatorForm) = _form.update(transform)

    /** Fills the Samsung Wallet preset. */
    fun presetSamsung() = _form.update { SimulatorPresets.samsung() }

    /** Fills the Toss preset. */
    fun presetToss() = _form.update { SimulatorPresets.toss() }

    /** Fills a Samsung Wallet cancellation of the last sent amount. */
    fun presetCancel() = _form.update { SimulatorPresets.samsungCancel(lastPayment.first, lastPayment.second) }

    /** Sends the form as a notification posted now. */
    fun send() {
        val f = _form.value
        if (!f.canSend || _sending.value) return
        _sending.value = true
        val raw = RawNotification(
            sourceApp = f.packageName,
            title = f.title.ifBlank { null },
            text = f.text.ifBlank { null },
            bigText = null,
            postedAt = env.clock.now(),
        )
        scope.launchCatching(env.messages) {
            try {
                val r = env.data.intake.onNotification(raw)
                val p = r.payment
                if (p != null && !p.isRefund) lastPayment = p.amount to (p.merchant ?: lastPayment.second)
                _result.value = SimulatorResult(r, IntakeResultSummary.of(r))
                if (r is IntakeResult.AutoSettled || r is IntakeResult.Refunded) env.haptics.success() else env.haptics.tap()
            } finally {
                _sending.value = false
            }
        }
    }
}
