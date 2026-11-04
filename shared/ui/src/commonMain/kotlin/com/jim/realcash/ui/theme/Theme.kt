package com.jim.realcash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Calm palette: cream paper surfaces and a green-teal accent that matches the 10,000원 bill. */
private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D6B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE9DF),
    onPrimaryContainer = Color(0xFF0B3A30),
    secondary = Color(0xFF6D6A4F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE6CC),
    onSecondaryContainer = Color(0xFF2B2915),
    tertiary = Color(0xFFA9741C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7E2B8),
    onTertiaryContainer = Color(0xFF3A2600),
    background = Color(0xFFFAF6EC),
    onBackground = Color(0xFF1E1C17),
    surface = Color(0xFFFAF6EC),
    onSurface = Color(0xFF1E1C17),
    surfaceVariant = Color(0xFFEAE4D3),
    onSurfaceVariant = Color(0xFF5B574A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1E4),
    surfaceContainer = Color(0xFFF1EBDC),
    surfaceContainerHigh = Color(0xFFEBE5D5),
    surfaceContainerHighest = Color(0xFFE5DFCE),
    outline = Color(0xFF8C8775),
    outlineVariant = Color(0xFFD3CCB8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF86D3BF),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF1C5446),
    onPrimaryContainer = Color(0xFFCDE9DF),
    secondary = Color(0xFFCFC9AD),
    onSecondary = Color(0xFF353222),
    secondaryContainer = Color(0xFF4B4834),
    onSecondaryContainer = Color(0xFFEDE6CC),
    tertiary = Color(0xFFEBC07A),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF634100),
    onTertiaryContainer = Color(0xFFF7E2B8),
    background = Color(0xFF15140F),
    onBackground = Color(0xFFE8E2D4),
    surface = Color(0xFF15140F),
    onSurface = Color(0xFFE8E2D4),
    surfaceVariant = Color(0xFF494639),
    onSurfaceVariant = Color(0xFFCCC6B4),
    surfaceContainerLowest = Color(0xFF100F0B),
    surfaceContainerLow = Color(0xFF1E1C17),
    surfaceContainer = Color(0xFF22201B),
    surfaceContainerHigh = Color(0xFF2C2A24),
    surfaceContainerHighest = Color(0xFF37352E),
    outline = Color(0xFF969180),
    outlineVariant = Color(0xFF494639),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Extra semantic colors that Material3 has no slot for. */
@Immutable
data class RealCashColors(
    /** Positive money flow (change, refunds). */
    val positive: Color,
    /** Warning badges (flags such as "직접 꺼내지 않은 결제"). */
    val warning: Color,
    /** Container behind [warning] text. */
    val warningContainer: Color,
)

private val LightExtra = RealCashColors(
    positive = Color(0xFF2E7D6B),
    warning = Color(0xFF8A4B00),
    warningContainer = Color(0xFFFBE3C2),
)

private val DarkExtra = RealCashColors(
    positive = Color(0xFF86D3BF),
    warning = Color(0xFFFFC98A),
    warningContainer = Color(0xFF4F3000),
)

/** Access to [RealCashColors] inside [RealCashTheme]. */
val LocalRealCashColors = staticCompositionLocalOf { LightExtra }

private val RealCashShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

/**
 * The app theme: Material3 with the RealCash light/dark palettes.
 *
 * @param darkTheme dark palette when true; follows the system by default.
 */
@Composable
fun RealCashTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalRealCashColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = RealCashShapes,
            content = content,
        )
    }
}
