package com.jim.realcash.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.parser.DefaultRules
import com.jim.realcash.ui.DebugEntry
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.ScreenHeader
import com.jim.realcash.ui.components.SegmentedChoice
import com.jim.realcash.ui.components.SectionLabel
import com.jim.realcash.ui.format.koreanShort
import com.jim.realcash.ui.format.sourceAppLabel
import kotlin.math.roundToInt
import kotlinx.datetime.DayOfWeek

/**
 * 설정 tab (screen 7): budget period, automatic deduction policy, hand TTL, auto-settle threshold and the detected
 * apps, followed by the "디버그" section (notification simulator + platform entries).
 *
 * @param onOpenSimulator opens the notification simulator.
 * @param platformEntries platform-only debug screens, listed after the simulator (see [DebugEntry]).
 * @param onOpenPlatformEntry opens `platformEntries[index]`.
 */
@Composable
fun SettingsScreen(
    model: SettingsModel,
    onOpenSimulator: () -> Unit,
    modifier: Modifier = Modifier,
    platformEntries: List<DebugEntry> = emptyList(),
    onOpenPlatformEntry: (Int) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val ui by model.ui.collectAsState()
    val packageText by model.packageText.collectAsState()
    val packageError by model.packageError.collectAsState()
    Column(modifier.fillMaxSize()) {
        ScreenHeader(Strings.SETTINGS_TITLE, onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeriodCard(ui, model::setCycle, model::setStartDay)
            PullPolicyCard(ui.pullPolicy, model::setPullPolicy)
            TtlCard(ui.ttlHours, model::setTtlHours)
            ThresholdCard(ui.threshold, model::setThreshold)
            SourcesCard(ui, packageText, packageError, model::setSourceApp, model::onPackageTextChange, model::addPackage)
            Spacer(Modifier.height(4.dp))
            DebugSection(onOpenSimulator, platformEntries, onOpenPlatformEntry)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingCard(title: String, note: String? = null, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            content()
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PeriodCard(ui: SettingsUi, onCycle: (BudgetPeriodPolicy.Cycle) -> Unit, onStartDay: (Int) -> Unit) {
    SettingCard(Strings.SET_PERIOD) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ui.cycle == BudgetPeriodPolicy.Cycle.WEEKLY,
                onClick = { onCycle(BudgetPeriodPolicy.Cycle.WEEKLY) },
                label = { Text(Strings.SET_WEEKLY) },
            )
            FilterChip(
                selected = ui.cycle == BudgetPeriodPolicy.Cycle.MONTHLY,
                onClick = { onCycle(BudgetPeriodPolicy.Cycle.MONTHLY) },
                label = { Text(Strings.SET_MONTHLY) },
            )
        }
        if (ui.cycle == BudgetPeriodPolicy.Cycle.WEEKLY) {
            Text(Strings.SET_START_WEEKDAY, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentedChoice(DayOfWeek.entries.map { it.koreanShort }, selectedIndex = ui.startDay - 1, onSelect = { onStartDay(it + 1) })
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Strings.SET_START_MONTHDAY,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { onStartDay(ui.startDay - 1) },
                    enabled = ui.startDay > 1,
                    modifier = Modifier.size(36.dp).semantics { contentDescription = "시작일 하루 앞으로" },
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                Text(
                    Strings.monthDayLabel(ui.startDay),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 88.dp),
                )
                FilledTonalIconButton(
                    onClick = { onStartDay(ui.startDay + 1) },
                    enabled = ui.startDay < 28,
                    modifier = Modifier.size(36.dp).semantics { contentDescription = "시작일 하루 뒤로" },
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun PullPolicyCard(selected: PullPolicy, onSelect: (PullPolicy) -> Unit) {
    SettingCard(Strings.SET_PULL, note = Strings.SET_PULL_NOTE) {
        Column {
            listOf(
                PullPolicy.LARGEST_FIRST to Strings.SET_PULL_LARGEST,
                PullPolicy.FEWEST_UNITS to Strings.SET_PULL_FEWEST,
                PullPolicy.EXACT_IF_POSSIBLE to Strings.SET_PULL_EXACT,
            ).forEach { (policy, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected == policy, onClick = { onSelect(policy) }, role = Role.RadioButton)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == policy, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TtlCard(selected: Int?, onSelect: (Int) -> Unit) {
    SettingCard(Strings.SET_TTL, note = Strings.SET_TTL_NOTE) {
        SegmentedChoice(
            SettingsRules.TTL_HOURS.map(Strings::ttlLabel),
            selectedIndex = SettingsRules.TTL_HOURS.indexOf(selected),
            onSelect = { onSelect(SettingsRules.TTL_HOURS[it]) },
        )
    }
}

@Composable
private fun ThresholdCard(threshold: Float, onCommit: (Float) -> Unit) {
    // Local value while dragging; written once when the thumb is released.
    var local by remember(threshold) { mutableFloatStateOf(threshold) }
    SettingCard(Strings.SET_THRESHOLD, note = Strings.SET_THRESHOLD_NOTE) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = local,
                onValueChange = { local = SettingsRules.snapThreshold(it) },
                onValueChangeFinished = { onCommit(local) },
                valueRange = SettingsRules.THRESHOLD_MIN..SettingsRules.THRESHOLD_MAX,
                steps = SettingsRules.THRESHOLD_SLIDER_STEPS,
                modifier = Modifier.weight(1f),
            )
            Text(
                Strings.thresholdLabel((local * 100).roundToInt()),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun SourcesCard(
    ui: SettingsUi,
    packageText: String,
    packageError: String?,
    onToggle: (String, Boolean) -> Unit,
    onPackageTextChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    SettingCard(Strings.SET_SOURCES, note = Strings.SET_SOURCES_NOTE) {
        SourceRow(DefaultRules.SAMSUNG_WALLET, ui.samsungEnabled, onToggle)
        SourceRow(DefaultRules.TOSS, ui.tossEnabled, onToggle)
        ui.otherPackages.forEach { pkg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pkg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onToggle(pkg, false) }) { Text(Strings.SET_REMOVE) }
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = packageText,
                onValueChange = onPackageTextChange,
                label = { Text(Strings.SET_ADD_PACKAGE) },
                placeholder = { Text(Strings.SET_ADD_PACKAGE_HINT) },
                singleLine = true,
                isError = packageError != null,
                supportingText = packageError?.let { { Text(it) } },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onAdd, enabled = packageText.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
                Text(Strings.SET_ADD)
            }
        }
    }
}

@Composable
private fun SourceRow(packageName: String, enabled: Boolean, onToggle: (String, Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(sourceAppLabel(packageName), style = MaterialTheme.typography.bodyLarge)
            Text(packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = { onToggle(packageName, it) })
    }
}

/** The "디버그" section: the notification simulator card and one card per platform entry. */
@Composable
private fun DebugSection(onOpenSimulator: () -> Unit, platformEntries: List<DebugEntry>, onOpenPlatformEntry: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(Strings.DEBUG_SECTION)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(Strings.SIMULATOR_TITLE, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    Strings.DEBUG_SIMULATOR_BODY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenSimulator) { Text(Strings.SIMULATOR_SHORTCUT) }
            }
        }
        // NOTE(T3): platform entries (Android: 알림 접근, 알림 샘플 수집), one card each, same look as the simulator card.
        platformEntries.forEachIndexed { index, entry ->
            Card(
                onClick = { onOpenPlatformEntry(index) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    if (entry.description != null) {
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
