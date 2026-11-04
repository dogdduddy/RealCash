package com.jim.realcash.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A single-choice row of equally wide segments (weekdays, TTL options) that never wraps, unlike a row of chips.
 *
 * @param labels one label per segment.
 * @param selectedIndex the selected segment, or -1 for none.
 */
@Composable
fun SegmentedChoice(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest,
                contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .selectable(selected = selected, onClick = { onSelect(i) }, role = Role.RadioButton),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
