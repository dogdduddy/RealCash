package com.jim.realcash.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.resources.Res
import com.jim.realcash.ui.resources.wallet_closed
import org.jetbrains.compose.resources.painterResource

/** Reviewed closed-wallet art; its silhouette also forms the dark, stacked leather edges. */
@Composable
fun ClosedWallet(sideLayers: Int, opening: Float, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val painter = painterResource(Res.drawable.wallet_closed)
    Box(
        modifier.padding(bottom = 18.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = Strings.OPEN_WALLET, onClick = onClick)
            .graphicsLayer {
                scaleX = 1f + 0.06f * opening
                scaleY = 1f + 0.06f * opening
            },
    ) {
        for (layer in sideLayers downTo 1) {
            Image(
                painter, contentDescription = null,
                colorFilter = ColorFilter.tint(Color(0xFF251F1B).copy(alpha = 0.82f)),
                modifier = Modifier.fillMaxWidth().aspectRatio(1024f / 540f).offset(y = (layer * 2.5f).dp),
            )
        }
        Image(
            painter, contentDescription = Strings.OPEN_WALLET,
            modifier = Modifier.fillMaxWidth().aspectRatio(1024f / 540f).graphicsLayer {
                rotationX = -70f * opening
                cameraDistance = 32f * density
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
        )
    }
}
