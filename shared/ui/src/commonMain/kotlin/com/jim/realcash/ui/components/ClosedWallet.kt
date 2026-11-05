package com.jim.realcash.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.resources.Res
import com.jim.realcash.ui.resources.wallet_closed
import org.jetbrains.compose.resources.painterResource

/** Reviewed wallet.closed.b artwork, shown whole at its manifest aspect ratio (1024×945). */
@Composable
fun ClosedWallet(opening: Float, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.wallet_closed),
        contentDescription = Strings.OPEN_WALLET,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxWidth().aspectRatio(1024f / 945f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = Strings.OPEN_WALLET, onClick = onClick)
            .graphicsLayer {
                scaleX = 1f + 0.04f * opening
                scaleY = 1f + 0.04f * opening
                translationY = -12.dp.toPx() * opening
            },
    )
}
