package com.jim.realcash.ui.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.FlagBadges
import com.jim.realcash.ui.components.ScreenHeader
import com.jim.realcash.ui.components.SectionLabel
import com.jim.realcash.ui.theme.LocalRealCashColors

/** Label of a [SimSource] in the dropdown. */
val SimSource.label: String
    get() = when (this) {
        SimSource.SAMSUNG -> Strings.SIM_SOURCE_SAMSUNG
        SimSource.TOSS -> Strings.SIM_SOURCE_TOSS
        SimSource.CUSTOM -> Strings.SIM_SOURCE_CUSTOM
    }

/**
 * Notification simulator (screen 4, debug): fakes a payment notification and shows what the intake pipeline did.
 */
@Composable
fun NotificationSimulatorScreen(model: NotificationSimulatorModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val form by model.form.collectAsState()
    val result by model.result.collectAsState()
    val sending by model.sending.collectAsState()
    NotificationSimulatorContent(
        form = form,
        result = result,
        sending = sending,
        onBack = onBack,
        onFormChange = model::update,
        onPresetSamsung = model::presetSamsung,
        onPresetToss = model::presetToss,
        onPresetCancel = model::presetCancel,
        onSend = model::send,
        modifier = modifier,
    )
}

/** Stateless body of [NotificationSimulatorScreen]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSimulatorContent(
    form: SimulatorForm,
    result: SimulatorResult?,
    sending: Boolean,
    onBack: () -> Unit,
    onFormChange: ((SimulatorForm) -> SimulatorForm) -> Unit,
    onPresetSamsung: () -> Unit,
    onPresetToss: () -> Unit,
    onPresetCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(Strings.SIMULATOR_TITLE, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(Strings.SIM_PRESETS)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onPresetSamsung, label = { Text(Strings.SIM_PRESET_SAMSUNG) })
                AssistChip(onClick = onPresetToss, label = { Text(Strings.SIM_PRESET_TOSS) })
                AssistChip(onClick = onPresetCancel, label = { Text(Strings.SIM_PRESET_CANCEL) })
            }
            SourcePicker(form.source) { s -> onFormChange { it.copy(source = s) } }
            if (form.source == SimSource.CUSTOM) {
                OutlinedTextField(
                    value = form.customPackage,
                    onValueChange = { v -> onFormChange { it.copy(customPackage = v) } },
                    label = { Text(Strings.SIM_PACKAGE) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> onFormChange { it.copy(title = v) } },
                label = { Text(Strings.SIM_TITLE) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.text,
                onValueChange = { v -> onFormChange { it.copy(text = v) } },
                label = { Text(Strings.SIM_TEXT) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Strings.SIM_TIME, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text(Strings.SIM_TIME_NOW, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onSend, enabled = form.canSend && !sending, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(Strings.SIM_SEND)
            }
            result?.let { ResultCard(it.summary) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SourcePicker(selected: SimSource, onSelect: (SimSource) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(Strings.SIM_SOURCE, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text("${selected.label}  ▾", modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SimSource.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(if (s.packageName != null) "${s.label} (${s.packageName})" else s.label) },
                        onClick = {
                            open = false
                            onSelect(s)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(summary: IntakeResultSummary) {
    val extra = LocalRealCashColors.current
    val (container, content) = when (summary.tone) {
        ResultTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ResultTone.ATTENTION -> extra.warningContainer to extra.warning
        ResultTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Strings.SIM_RESULT, style = MaterialTheme.typography.labelMedium)
            Text(summary.headline, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            summary.details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (summary.flags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlagBadges(summary.flags)
            }
        }
    }
}
