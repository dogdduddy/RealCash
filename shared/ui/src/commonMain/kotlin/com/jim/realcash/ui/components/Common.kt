package com.jim.realcash.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.theme.LocalRealCashColors

/** Screen header with an optional back button. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = Strings.BACK }) {
                RcIconGlyph(RcIcon.BACK)
            }
        } else {
            Box(Modifier.padding(start = 12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Small uppercase-ish section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Badge for a [SettlementFlag] (warning tone except for MANUAL). */
@Composable
fun FlagBadge(flag: SettlementFlag, modifier: Modifier = Modifier) {
    val extra = LocalRealCashColors.current
    val (bg, fg) = when (flag) {
        SettlementFlag.MANUAL -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        SettlementFlag.INSUFFICIENT_FUNDS -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> extra.warningContainer to extra.warning
    }
    Text(
        Strings.flagLabel(flag),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
        modifier = modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Row of [FlagBadge]s in a stable order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlagBadges(flags: Set<SettlementFlag>, modifier: Modifier = Modifier) {
    if (flags.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettlementFlag.entries.filter { it in flags }.forEach { FlagBadge(it) }
    }
}
