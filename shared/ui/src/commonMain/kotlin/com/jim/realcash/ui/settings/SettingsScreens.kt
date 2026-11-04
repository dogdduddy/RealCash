package com.jim.realcash.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jim.realcash.ui.DebugEntry
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.PlaceholderBody
import com.jim.realcash.ui.components.SectionLabel

/** 가계부 tab placeholder (filled in T5b). */
@Composable
fun LedgerPlaceholderScreen(modifier: Modifier = Modifier) {
    PlaceholderBody(Strings.LEDGER_TITLE, Strings.PLACEHOLDER_NOTE, modifier)
}

/**
 * 설정 tab: a T5b placeholder plus the "디버그" section with the notification simulator.
 *
 * @param onOpenSimulator opens the notification simulator.
 * @param platformEntries platform-only debug screens, listed after the simulator (see [DebugEntry]).
 * @param onOpenPlatformEntry opens `platformEntries[index]`.
 */
@Composable
fun SettingsPlaceholderScreen(
    onOpenSimulator: () -> Unit,
    modifier: Modifier = Modifier,
    platformEntries: List<DebugEntry> = emptyList(),
    onOpenPlatformEntry: (Int) -> Unit = {},
) {
    PlaceholderBody(Strings.SETTINGS_TITLE, Strings.PLACEHOLDER_NOTE, modifier) {
        Spacer(Modifier.height(24.dp))
        SectionLabel(Strings.DEBUG_SECTION)
        Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
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
