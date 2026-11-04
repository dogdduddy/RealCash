package com.jim.realcash.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import java.io.File

/** Phone-like logical size of the desktop window (iPhone 14/15 points). */
private val PhoneSize = DpSize(390.dp, 844.dp)

/**
 * Desktop entry point (`./gradlew :shared:ui:run`): a phone-sized window over a file-backed SQLite database at
 * `~/.realcash/realcash.db`. The wallet home shows the notification-simulator shortcut so the whole flow can be
 * exercised without any platform notification code.
 */
fun main() {
    val dir = File(System.getProperty("user.home"), ".realcash").apply { mkdirs() }
    val data = RealCashData(DriverFactory.forFile(File(dir, "realcash.db").absolutePath).createDriver())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = Strings.APP_NAME,
            state = rememberWindowState(size = PhoneSize, position = WindowPosition(Alignment.Center)),
        ) {
            RealCashApp(data, Haptics.None, showDebugShortcut = true)
        }
    }
}
