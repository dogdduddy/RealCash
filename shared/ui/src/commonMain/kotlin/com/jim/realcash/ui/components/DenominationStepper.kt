package com.jim.realcash.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.format.faceLabel
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.money.billAspectRatio
import com.jim.realcash.ui.money.BillFace
import com.jim.realcash.ui.money.CoinFace
import com.jim.realcash.ui.money.LocalMoneyArt

/**
 * One denomination row with a miniature face, the subtotal and −/count/+ (allowance split, exchange "받을 돈").
 */
@Composable
fun DenominationStepperRow(
    d: Denomination,
    count: Int,
    onIncrement: (Denomination) -> Unit,
    onDecrement: (Denomination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val art = LocalMoneyArt.current
    val faceLabel = "${d.faceLabel}원"
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (d.kind == Denomination.Kind.BILL) {
            BillFace(d, Modifier.size(width = 52.dp, height = 52.dp / d.billAspectRatio), art.painter(d))
        } else {
            Row(Modifier.width(52.dp), horizontalArrangement = Arrangement.Center) {
                CoinFace(d, Modifier.size(24.dp), art.painter(d))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(faceLabel, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (count > 0) {
                Text(
                    (d.amount * count).formatted(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FilledTonalIconButton(
            onClick = { onDecrement(d) },
            enabled = count > 0,
            modifier = Modifier.size(36.dp).semantics { contentDescription = "$faceLabel 빼기" },
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        FilledTonalIconButton(
            onClick = { onIncrement(d) },
            modifier = Modifier.size(36.dp).semantics { contentDescription = "$faceLabel 더하기" },
        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}
