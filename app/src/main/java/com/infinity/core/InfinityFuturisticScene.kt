package com.infinity.core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/**
 * Procedural, resolution-independent fallback scene for the INFINITY reference desktop.
 * All controls remain Compose UI above this artwork; no screenshot is used as a fake UI.
 */
@Composable
internal fun InfinityFuturisticScene(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val shortSide = min(w, h)
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF020714),
                    .42f to Color(0xFF0A1B3B),
                    .72f to Color(0xFF15275B),
                    1f to Color(0xFF050D20)
                )
            )

            // Distant nebula haze.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x663A7DDB), Color(0x223B4DB2), Color.Transparent),
                    center = Offset(w * .52f, h * .48f),
                    radius = shortSide * .72f
                ),
                radius = shortSide * .72f,
                center = Offset(w * .52f, h * .48f)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x554D2E9A), Color.Transparent),
                    center = Offset(w * .83f, h * .38f),
                    radius = shortSide * .48f
                ),
                radius = shortSide * .48f,
                center = Offset(w * .83f, h * .38f)
            )

            // Deterministic star field (no runtime randomness or allocations per star).
            for (i in 0 until 110) {
                val x = ((i * 73 + 19) % 997) / 997f * w
                val y = ((i * 193 + 41) % 991) / 991f * h * .82f
                val radius = if (i % 13 == 0) 2.1f else if (i % 4 == 0) 1.25f else .72f
                val alpha = if (i % 7 == 0) .9f else .48f
                drawCircle(Color(0xFFDCEBFF).copy(alpha = alpha), radius, Offset(x, y))
            }

            // Planetary horizon and atmospheric rim behind the central wordmark.
            val planetCenter = Offset(w * .53f, h * .78f)
            val planetRadius = shortSide * .42f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x553D9DFF), Color(0x333A57C8), Color.Transparent),
                    center = planetCenter,
                    radius = planetRadius * 1.55f
                ),
                radius = planetRadius * 1.55f,
                center = planetCenter
            )
            drawCircle(
                Brush.verticalGradient(
                    listOf(Color(0xFF263F83), Color(0xFF111E49), Color(0xFF080F29)),
                    startY = planetCenter.y - planetRadius,
                    endY = planetCenter.y + planetRadius
                ),
                radius = planetRadius,
                center = planetCenter
            )
            drawCircle(
                Color(0xFF68D8FF).copy(alpha = .78f),
                radius = planetRadius,
                center = planetCenter,
                style = Stroke(width = (shortSide * .004f).coerceAtLeast(1.2f)
                )
            )
            drawArc(
                Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF57D9FF), Color(0xFF9277FF), Color.Transparent)),
                startAngle = 188f,
                sweepAngle = 164f,
                useCenter = false,
                topLeft = Offset(planetCenter.x - planetRadius * 1.16f, planetCenter.y - planetRadius * .28f),
                size = Size(planetRadius * 2.32f, planetRadius * .68f),
                style = Stroke(width = (shortSide * .009f).coerceAtLeast(2f))
            )

            // Abstract luminous skyline at the bottom edge gives the scene depth.
            val skyline = Path().apply {
                moveTo(0f, h * .88f)
                lineTo(0f, h * .82f)
                lineTo(w * .07f, h * .82f)
                lineTo(w * .09f, h * .75f)
                lineTo(w * .12f, h * .75f)
                lineTo(w * .14f, h * .84f)
                lineTo(w * .20f, h * .84f)
                lineTo(w * .22f, h * .78f)
                lineTo(w * .25f, h * .78f)
                lineTo(w * .27f, h * .86f)
                lineTo(w * .34f, h * .86f)
                lineTo(w * .37f, h * .80f)
                lineTo(w * .40f, h * .80f)
                lineTo(w * .43f, h * .87f)
                lineTo(w * .51f, h * .87f)
                lineTo(w * .55f, h * .79f)
                lineTo(w * .58f, h * .79f)
                lineTo(w * .61f, h * .87f)
                lineTo(w * .69f, h * .87f)
                lineTo(w * .72f, h * .77f)
                lineTo(w * .76f, h * .77f)
                lineTo(w * .79f, h * .86f)
                lineTo(w * .86f, h * .86f)
                lineTo(w * .89f, h * .80f)
                lineTo(w * .93f, h * .80f)
                lineTo(w * .95f, h * .87f)
                lineTo(w, h * .87f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                skyline,
                Brush.verticalGradient(
                    listOf(Color(0xCC122B57), Color(0xF2050B1A)),
                    startY = h * .76f,
                    endY = h
                )
            )
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, Color(0xAA4EDCFF), Color(0x887C78FF), Color.Transparent)),
                Offset(0f, h * .875f),
                Offset(w, h * .875f),
                strokeWidth = (shortSide * .003f).coerceAtLeast(1f)
            )
        }
    }
}
