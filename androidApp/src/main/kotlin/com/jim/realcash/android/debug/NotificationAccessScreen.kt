package com.jim.realcash.android.debug

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jim.realcash.android.AppLog
import com.jim.realcash.android.listener.PaymentNotificationListenerService
import com.jim.realcash.android.realCash
import com.jim.realcash.ui.components.ScreenHeader
import com.jim.realcash.ui.format.sourceAppLabel

/**
 * 알림 접근: the Play-policy disclosure, whether our listener is enabled (and connected), a shortcut to the special
 * access settings, and the POST_NOTIFICATIONS permission (API 33+) for our settlement notifications.
 */
@Composable
fun NotificationAccessScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.realCash
    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }
    var canPost by remember { mutableStateOf(app.settlementNotifier.canPost()) }
    var deniedOnce by remember { mutableStateOf(false) }
    val connected by PaymentNotificationListenerService.connected.collectAsState()
    val settings by app.data.settings.settings.collectAsState()

    // The user comes back from the system settings: re-read both states.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        listenerEnabled = isListenerEnabled(context)
        canPost = app.settlementNotifier.canPost()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost = app.settlementNotifier.canPost()
        if (!granted) deniedOnce = true
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(DebugStrings.ACCESS_TITLE, onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard {
                Text(DebugStrings.ACCESS_DISCLOSURE, style = MaterialTheme.typography.bodyMedium)
            }

            InfoCard {
                StatusRow(
                    DebugStrings.ACCESS_LISTENER_LABEL,
                    if (listenerEnabled) DebugStrings.ACCESS_ON else DebugStrings.ACCESS_OFF,
                    positive = listenerEnabled,
                )
                if (listenerEnabled) {
                    Text(
                        if (connected) DebugStrings.ACCESS_CONNECTED else DebugStrings.ACCESS_NOT_CONNECTED,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openListenerSettings(context) }) { Text(DebugStrings.ACCESS_OPEN_SETTINGS) }
                    if (listenerEnabled && !connected) {
                        TextButton(onClick = { requestRebind(context) }) { Text(DebugStrings.ACCESS_REBIND) }
                    }
                }
            }

            InfoCard {
                StatusRow(
                    DebugStrings.ACCESS_POST_LABEL,
                    if (canPost) DebugStrings.ACCESS_POST_ALLOWED else DebugStrings.ACCESS_POST_BLOCKED,
                    positive = canPost,
                )
                if (!canPost) {
                    Text(DebugStrings.ACCESS_POST_BODY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    val askRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !deniedOnce &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    OutlinedButton(
                        onClick = {
                            if (askRuntime) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppNotificationSettings(context)
                            }
                        },
                    ) { Text(if (askRuntime) DebugStrings.ACCESS_POST_REQUEST else DebugStrings.ACCESS_POST_SETTINGS) }
                }
            }

            InfoCard {
                StatusRow(
                    DebugStrings.ACCESS_SOURCES_LABEL,
                    settings.enabledSourceApps.sorted().joinToString(", ") { sourceAppLabel(it) }.ifEmpty { DebugStrings.ACCESS_SOURCES_NONE },
                    positive = settings.enabledSourceApps.isNotEmpty(),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A tonal card with padding, shared by both debug screens. */
@Composable
internal fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun StatusRow(label: String, value: String, positive: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun listenerComponent(context: Context) = ComponentName(context, PaymentNotificationListenerService::class.java)

/** Whether the user granted our listener in 알림 접근 (special app access). */
internal fun isListenerEnabled(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

private fun openListenerSettings(context: Context) {
    // API 30+: straight to our own toggle; otherwise (or if the OEM lacks that screen) the list of all listeners.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent(context).flattenToString())
        if (tryStart(context, detail)) return
    }
    tryStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    tryStart(context, intent)
}

private fun requestRebind(context: Context) {
    try {
        NotificationListenerService.requestRebind(listenerComponent(context))
    } catch (e: RuntimeException) {
        AppLog.w("requestRebind failed", e)
    }
}

private fun tryStart(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    AppLog.w("no activity for ${intent.action}", e)
    false
}
