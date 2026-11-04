package com.jim.realcash.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UIViewController

/** UIKit feedback generators behind [Haptics]. */
object IosHaptics : Haptics {
    override fun tap() = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()

    override fun success() =
        UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)

    override fun pickUp() = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
}

/**
 * The process-wide data graph over the native SQLite driver (`realcash.db` in the app's database directory).
 *
 * NOTE: a top-level lazy so that the App Intent / notification extension code of the iOS app (T2 follow-up) can
 * share the same instance once it is wired; the view controller only reads it.
 */
val iosRealCashData: RealCashData by lazy { RealCashData(DriverFactory().createDriver()) }

/** Root view controller for the SwiftUI host (`ComposeView` → `UIViewControllerRepresentable`). */
fun MainViewController(): UIViewController = ComposeUIViewController {
    RealCashApp(iosRealCashData, IosHaptics)
}
