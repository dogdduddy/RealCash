package com.jim.realcash.ui.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.format.faceLabel

/** Placeholder colors of one denomination (used until pipeline images replace the Canvas faces). */
@Immutable
data class DenominationColors(
    /** Light end of the face gradient. */
    val light: Color,
    /** Dark end of the face gradient. */
    val dark: Color,
    /** Numeral / ornament color on top of the gradient. */
    val ink: Color,
)

/** Face colors per denomination: 50,000 yellow-ochre, 10,000 green-teal, 5,000 orange-red, 1,000 blue. */
fun Denomination.colors(): DenominationColors = when (this) {
    Denomination.W50000 -> DenominationColors(Color(0xFFE9C46A), Color(0xFFB07F1E), Color(0xFF4A3305))
    Denomination.W10000 -> DenominationColors(Color(0xFF6DBBA5), Color(0xFF256E5D), Color(0xFFFFFFFF))
    Denomination.W5000 -> DenominationColors(Color(0xFFF08D63), Color(0xFFB8431F), Color(0xFFFFFFFF))
    Denomination.W1000 -> DenominationColors(Color(0xFF7FA9DB), Color(0xFF2F5F99), Color(0xFFFFFFFF))
    Denomination.C500 -> DenominationColors(Color(0xFFE3E6EA), Color(0xFF9AA3AC), Color(0xFF39414A))
    Denomination.C100 -> DenominationColors(Color(0xFFDCE0E4), Color(0xFF8F98A2), Color(0xFF39414A))
    Denomination.C50 -> DenominationColors(Color(0xFFE6E2DA), Color(0xFFA49C8E), Color(0xFF443D31))
    Denomination.C10 -> DenominationColors(Color(0xFFE8B98F), Color(0xFFA9683A), Color(0xFF3E220D))
}

/**
 * Relative physical size: bills by width (154/148/142/136 mm), coins by diameter (26.5/24/21.6/18 mm),
 * normalised to the largest of their kind. Identification is color + numeral + size (design doc §5.5).
 */
val Denomination.relativeSize: Float
    get() = when (this) {
        Denomination.W50000 -> 1f
        Denomination.W10000 -> 0.96f
        Denomination.W5000 -> 0.92f
        Denomination.W1000 -> 0.88f
        Denomination.C500 -> 1f
        Denomination.C100 -> 0.91f
        Denomination.C50 -> 0.82f
        Denomination.C10 -> 0.68f
    }

/** Width / height ratio of a bill face. */
const val BILL_ASPECT: Float = 2.15f

private val BillCorner = 6.dp

/** Below this face height the small corner numeral is unreadable and is left out (chips, stepper rows). */
private val MIN_HEIGHT_FOR_CORNER_LABEL = 36.dp

/**
 * One bill or coin face; dispatches on [Denomination.kind].
 *
 * @param painter pipeline artwork; when `null` a Canvas placeholder is drawn.
 */
@Composable
fun MoneyFace(denomination: Denomination, modifier: Modifier = Modifier, painter: Painter? = null) {
    if (denomination.kind == Denomination.Kind.BILL) {
        BillFace(denomination, modifier, painter)
    } else {
        CoinFace(denomination, modifier, painter)
    }
}

/**
 * The face of a bill. The caller decides the size (keep [BILL_ASPECT]).
 *
 * @param painter artwork from the image pipeline (design doc §5.5). When `null`, a Canvas placeholder is drawn:
 * a gradient in the denomination's color, a watermark disc, a fine inner frame and the numeral.
 */
@Composable
fun BillFace(denomination: Denomination, modifier: Modifier = Modifier, painter: Painter? = null) {
    val shape = RoundedCornerShape(BillCorner)
    val label = "${denomination.faceLabel}원"
    Box(
        modifier = modifier
            .shadow(2.dp, shape)
            .clip(shape)
            .semantics { contentDescription = label },
    ) {
        if (painter != null) {
            Image(painter, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            BillPlaceholder(denomination)
        }
    }
}

@Composable
private fun BillPlaceholder(denomination: Denomination) {
    val c = denomination.colors()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(c.light, c.dark), start = Offset.Zero, end = Offset(size.width, size.height)))
            // Watermark disc on the left third.
            val r = size.height * 0.34f
            drawCircle(Color.White.copy(alpha = 0.22f), radius = r, center = Offset(size.width * 0.24f, size.height * 0.5f))
            drawCircle(
                Color.White.copy(alpha = 0.35f),
                radius = r * 0.78f,
                center = Offset(size.width * 0.24f, size.height * 0.5f),
                style = Stroke(width = size.height * 0.015f),
            )
            // Fine guilloche-like waves on the right.
            val step = size.height / 9f
            for (i in 1..7) {
                drawArc(
                    color = c.ink.copy(alpha = 0.08f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.45f + i * step * 0.6f, step * 0.5f),
                    size = Size(size.width * 0.55f, size.height - step),
                    style = Stroke(width = 1f),
                )
            }
            // Inner frame.
            val inset = size.height * 0.07f
            drawRoundRect(
                color = c.ink.copy(alpha = 0.25f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius(inset, inset),
                style = Stroke(width = 1.2f),
            )
        }
        val numeral = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (h * 0.30f).toSp() },
        )
        val small = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (h * 0.14f).toSp() },
        )
        if (h >= MIN_HEIGHT_FOR_CORNER_LABEL) {
            Text(
                denomination.faceLabel,
                style = small,
                color = c.ink.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(start = h * 0.14f, top = h * 0.11f),
            )
        }
        Text(
            denomination.faceLabel,
            style = numeral,
            color = c.ink,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = h * 0.14f, bottom = h * 0.08f),
        )
    }
}

/**
 * The face of a coin (circle). The caller decides the size.
 *
 * @param painter artwork from the image pipeline; when `null`, a metallic radial gradient with a rim and the numeral.
 */
@Composable
fun CoinFace(denomination: Denomination, modifier: Modifier = Modifier, painter: Painter? = null) {
    val c = denomination.colors()
    val label = "${denomination.faceLabel}원"
    BoxWithConstraints(
        modifier = modifier
            .shadow(1.5.dp, CircleShape)
            .clip(CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(painter, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val d = minOf(maxWidth, maxHeight)
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(listOf(c.light, c.dark), center = Offset(radius * 0.7f, radius * 0.6f), radius = radius * 1.5f),
                    radius = radius,
                )
                drawCircle(c.ink.copy(alpha = 0.25f), radius = radius * 0.82f, style = Stroke(width = radius * 0.06f))
            }
            Text(
                denomination.faceLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (d * 0.30f).toSp() },
                ),
                color = c.ink,
                maxLines = 1,
            )
        }
    }
}

/** Offset of one back layer in a stack. */
private val LayerStep = 3.dp

/** Maximum number of back layers drawn behind the front face. */
private const val MAX_BACK_LAYERS = 3

/**
 * A stack of [count] bills of one denomination: the front face plus up to three offset back layers
 * (HardPay's `backLayers` idea) and a count badge. Fills the available width scaled by [Denomination.relativeSize].
 *
 * The stack always reserves room for the maximum offset so that stacks of different counts line up.
 *
 * @param painter pipeline artwork passed to every [BillFace] of the stack.
 * @param onClick tap action (T5b: take one bill out); `null` = not clickable.
 */
@Composable
fun BillStack(
    denomination: Denomination,
    count: Int,
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    onClick: (() -> Unit)? = null,
) {
    val backLayers = (count - 1).coerceIn(0, MAX_BACK_LAYERS)
    val reserve = LayerStep * MAX_BACK_LAYERS
    Box(modifier.padding(end = reserve, bottom = reserve, top = 6.dp)) {
        val faceModifier = Modifier.fillMaxWidth(denomination.relativeSize).aspectRatio(BILL_ASPECT)
        repeat(backLayers) { i ->
            val depth = backLayers - i
            BillFace(
                denomination,
                faceModifier.offset(x = LayerStep * depth, y = LayerStep * depth).alpha(0.75f),
                painter,
            )
        }
        BillFace(
            denomination,
            if (onClick != null) faceModifier.clickable(onClick = onClick) else faceModifier,
            painter,
        )
        CountBadge(count, Modifier.align(Alignment.TopEnd).offset(x = reserve - 2.dp, y = (-6).dp))
    }
}

/**
 * A stack of [count] coins of one denomination: the front coin plus up to three back layers stacked upwards,
 * and a count badge.
 *
 * @param size diameter of the largest coin (500원); smaller coins scale by [Denomination.relativeSize].
 */
@Composable
fun CoinStack(
    denomination: Denomination,
    count: Int,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    painter: Painter? = null,
    onClick: (() -> Unit)? = null,
) {
    val backLayers = (count - 1).coerceIn(0, MAX_BACK_LAYERS)
    val reserve = LayerStep * MAX_BACK_LAYERS
    val d = size * denomination.relativeSize
    Box(modifier.size(width = size + 10.dp, height = size + reserve + 6.dp), contentAlignment = Alignment.BottomCenter) {
        repeat(backLayers) { i ->
            val depth = backLayers - i
            CoinFace(denomination, Modifier.size(d).offset(y = -LayerStep * depth).alpha(0.8f), painter)
        }
        val front = Modifier.size(d)
        CoinFace(denomination, if (onClick != null) front.clickable(onClick = onClick) else front, painter)
        CountBadge(count, Modifier.align(Alignment.TopEnd))
    }
}

/** Small pill showing `×N`. Hidden for N <= 1. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Text(
        "×$count",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * Compact chip for one denomination and count (used in the "손에 든 돈" card): a miniature face and `×N`.
 */
@Composable
fun UnitChip(denomination: Denomination, count: Int, modifier: Modifier = Modifier, painter: Painter? = null) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(50))
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (denomination.kind == Denomination.Kind.BILL) {
            BillFace(denomination, Modifier.size(width = 42.dp, height = (42 / BILL_ASPECT).dp), painter)
        } else {
            CoinFace(denomination, Modifier.size(20.dp), painter)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "×$count",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
