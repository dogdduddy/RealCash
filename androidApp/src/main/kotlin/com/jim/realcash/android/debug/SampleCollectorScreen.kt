package com.jim.realcash.android.debug

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jim.realcash.android.AppLog
import com.jim.realcash.android.realCash
import com.jim.realcash.android.samples.NotificationSample
import com.jim.realcash.android.samples.SampleExporter
import com.jim.realcash.domain.model.Won
import com.jim.realcash.ui.components.ScreenHeader
import com.jim.realcash.ui.components.SectionLabel
import com.jim.realcash.ui.format.formatConfidence
import com.jim.realcash.ui.format.formatTime
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.format.sourceAppLabel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Samples shown on the screen. */
private const val SHOWN_SAMPLES = 50

/**
 * 알림 샘플 수집 (Phase 0): on/off, the package list, the newest samples with their parse result, export (share
 * sheet / clipboard) and clear.
 */
@Composable
fun SampleCollectorScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.realCash
    val store = app.sampleStore
    val collector = app.collectorSettings
    val state by collector.state.collectAsState()
    val version by store.version.collectAsState()
    val scope = rememberCoroutineScope()

    var samples by remember { mutableStateOf<List<NotificationSample>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) {
        val all = withContext(Dispatchers.IO) { store.readAll() }
        total = all.size
        samples = all.takeLast(SHOWN_SAMPLES).asReversed()
    }

    var newPackage by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val timeZone = remember { TimeZone.currentSystemDefault() }
    val today = remember(version) { Clock.System.now().toLocalDateTime(timeZone).date }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun addPackage() {
        if (collector.addPackage(newPackage)) {
            newPackage = ""
            invalid = false
        } else {
            invalid = true
        }
    }

    LazyColumn(
        modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { ScreenHeader(DebugStrings.SAMPLES_TITLE, onBack = onBack) }
        item {
            Section {
                InfoCard {
                    Text(DebugStrings.SAMPLES_PRIVACY, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Section {
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(DebugStrings.SAMPLES_TOGGLE, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                DebugStrings.SAMPLES_TOGGLE_BODY,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.enabled, onCheckedChange = collector::setEnabled)
                    }
                    if (state.enabled && !isListenerEnabled(context)) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${DebugStrings.ACCESS_LISTENER_LABEL}: ${DebugStrings.ACCESS_OFF}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        item { Section { SectionLabel(DebugStrings.SAMPLES_PACKAGES) } }
        items(state.packages.sorted(), key = { "pkg:$it" }) { pkg ->
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(sourceAppLabel(pkg), style = MaterialTheme.typography.bodyMedium)
                    if (sourceAppLabel(pkg) != pkg) {
                        Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = { collector.removePackage(pkg) }) { Text(DebugStrings.SAMPLES_REMOVE) }
            }
        }
        item {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPackage,
                        onValueChange = {
                            newPackage = it.trim()
                            invalid = false
                        },
                        placeholder = { Text(DebugStrings.SAMPLES_PACKAGE_HINT) },
                        singleLine = true,
                        isError = invalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onDone = { addPackage() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = ::addPackage, enabled = newPackage.isNotBlank()) { Text(DebugStrings.SAMPLES_ADD) }
                }
                if (invalid) {
                    Text(DebugStrings.SAMPLES_INVALID, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = collector::resetPackages) { Text(DebugStrings.SAMPLES_RESET) }
            }
        }
        item {
            Section {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = total > 0,
                        onClick = {
                            scope.launch {
                                try {
                                    val uri = withContext(Dispatchers.IO) { SampleExporter.snapshot(context, store) }
                                    if (uri == null) toast(DebugStrings.SAMPLES_NOTHING) else SampleExporter.share(context, uri)
                                } catch (e: Exception) {
                                    AppLog.w("sample export failed", e)
                                }
                            }
                        },
                    ) { Text(DebugStrings.SAMPLES_EXPORT) }
                    OutlinedButton(
                        enabled = total > 0,
                        onClick = {
                            scope.launch {
                                val all = withContext(Dispatchers.IO) { store.readAll() }
                                toast(DebugStrings.copied(SampleExporter.copyToClipboard(context, all)))
                            }
                        },
                    ) { Text(DebugStrings.SAMPLES_COPY) }
                    TextButton(enabled = total > 0, onClick = { confirmClear = true }) {
                        Text(DebugStrings.SAMPLES_CLEAR, color = if (total > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Section {
                SectionLabel(if (total == 0) DebugStrings.SAMPLES_NOTHING else DebugStrings.samplesRecent(samples.size, total))
            }
        }
        items(samples) { sample ->
            Section { SampleCard(sample, timeZone, today) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(DebugStrings.SAMPLES_CLEAR_CONFIRM_TITLE) },
            text = { Text(DebugStrings.SAMPLES_CLEAR_CONFIRM_BODY) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch {
                            withContext(Dispatchers.IO) { store.clear() }
                            toast(DebugStrings.SAMPLES_CLEARED)
                        }
                    },
                ) { Text(DebugStrings.SAMPLES_CLEAR, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(DebugStrings.SAMPLES_CANCEL) } },
        )
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) { content() }
}

@Composable
private fun SampleCard(sample: NotificationSample, timeZone: TimeZone, today: kotlinx.datetime.LocalDate) {
    val time = try {
        formatTime(Instant.parse(sample.postTime), timeZone, today)
    } catch (e: IllegalArgumentException) {
        sample.postTime
    }
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                sourceAppLabel(sample.packageName),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        sample.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)) }
        sample.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (sample.bigText != null && sample.bigText != sample.text) {
            Text(sample.bigText, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
        postedAsLine(sample)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            parseLine(sample),
            style = MaterialTheme.typography.labelMedium,
            color = if (sample.parsed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `채널 payment · 진행 중 · 그룹 요약`, or `null` when there is nothing to say. */
private fun postedAsLine(sample: NotificationSample): String? = listOfNotNull(
    sample.channelId?.let { "채널 $it" },
    "진행 중".takeIf { sample.isOngoing },
    "그룹 요약".takeIf { sample.isGroupSummary },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** `₩6,500 · 스타벅스 · 85%` (+ 취소/환불), or 파싱 안 됨. */
private fun parseLine(sample: NotificationSample): String {
    val amount = sample.parsedAmount ?: return DebugStrings.SAMPLES_NOT_PARSED
    return listOfNotNull(
        Won(amount).formatted(),
        sample.parsedMerchant,
        sample.parsedConfidence?.let(::formatConfidence),
        DebugStrings.SAMPLES_REFUND.takeIf { sample.parsedRefund == true },
    ).joinToString(" · ")
}
