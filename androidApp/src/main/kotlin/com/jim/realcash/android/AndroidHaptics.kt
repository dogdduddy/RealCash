package com.jim.realcash.android

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.jim.realcash.ui.Haptics

/** [Haptics] through `View.performHapticFeedback`, so the system's touch-feedback setting is respected. */
class AndroidHaptics(private val view: View) : Haptics {
    override fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun success() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        view.performHapticFeedback(constant)
    }

    override fun pickUp() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.DRAG_START
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}
