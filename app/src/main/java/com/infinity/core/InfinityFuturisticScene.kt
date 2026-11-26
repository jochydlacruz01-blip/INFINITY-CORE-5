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
 * Resolution-independent, procedural artwork for the official INFINITY reference desktop.
 * It deliberately draws scenery only: the logo, clock, icons, Dock, windows and menus remain
 * independent interactive Compose components layered above this scene.
 */
@Composable
internal fun InfinityFuturisticScene(
    backgroundPreset: String = "interstellar",
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val s = min(w, h)
            val horizon = h * .765f

            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF010611),
                    .38f to Color(0xFF061630),
                    .68f to Color(0xFF102C5C),
                    1f to Color(0xFF020817)
                )
            )

            // Deep blue/violet nebula and distant stellar field.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x663B91F4), Color(0x223D58C8), Color.Transparent),
                    center = Offset(w * .53f, h * .39f), radius = s * .78f
                ), s * .78f, Offset(w * .53f, h * .39f)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x554D26B4), Color(0x223A318A), Color.Transparent),
                    center = Offset(w * .79f, h * .34f), radius = s * .54f
                ), s * .54f, Offset(w * .79f, h * .34f)
            )
            for (i in 0 until 145) {
                val x = ((i * 73 + 19) % 997) / 997f * w
                val y = ((i * 193 + 41) % 991) / 991f * h * .78f
                val r = if (i % 17 == 0) 2.2f else if (i % 5 == 0) 1.25f else .65f
                drawCircle(Color(0xFFE5F3FF).copy(alpha = if (i % 7 == 0) .92f else .48f), r, Offset(x, y))
            }

            // Large blue planet in the upper-left, matching the reference composition.
            val leftPlanet = Offset(w * .135f, h * .105f)
            val leftRadius = s * .34f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF4C9DEB), Color(0xFF183F82), Color(0xFF06152F), Color(0xFF020817)),
                    center = Offset(leftPlanet.x - leftRadius * .28f, leftPlanet.y - leftRadius * .32f),
                    radius = leftRadius * 1.55f
                ), leftRadius, leftPlanet
            )
            drawArc(
                Brush.horizontalGradient(listOf(Color.Transparent, Color(0xAA65CFFF), Color(0x668D9CFF), Color.Transparent)),
                8f, 150f, false,
                Offset(leftPlanet.x - leftRadius * 1.04f, leftPlanet.y - leftRadius * .98f),
                Size(leftRadius * 2.08f, leftRadius * 2.08f),
                style = Stroke((s * .006f).coerceAtLeast(1.5f))
            )

            // Main planetary horizon: bright atmospheric edge above the futuristic city.
            val planetCenter = Offset(w * .67f, h * 1.02f)
            val planetRadius = s * .83f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x554C9DFF), Color(0x223B5DC5), Color.Transparent),
                    center = Offset(planetCenter.x, horizon), radius = planetRadius * 1.12f
                ), planetRadius * 1.12f, Offset(planetCenter.x, horizon)
            )
            drawArc(
                Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF4BBEFF), Color(0xFFE1F7FF), Color(0xFF697DFF), Color.Transparent)),
                190f, 160f, false,
                Offset(w * .22f, horizon - s * .30f),
                Size(w * .98f, s * .62f),
                style = Stroke((s * .012f).coerceAtLeast(2.5f))
            )
            drawArc(
                Color(0x8869BFFF), 190f, 160f, false,
                Offset(w * .22f, horizon - s * .30f),
                Size(w * .98f, s * .62f),
                style = Stroke((s * .035f).coerceAtLeast(5f))
            )

            // Right-side skyline, with varied towers and illuminated windows.
            for (i in 0 until 31) {
                val x = w * (.53f + i * .0152f)
                val bw = w * (.008f + (i % 4) * .002f)
                val bh = h * (.075f + ((i * 37) % 100) / 100f * .22f)
                val top = horizon - bh
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFF173D70), Color(0xFF07162D), Color(0xFF030B19)), top, horizon + h * .13f),
                    Offset(x, top), Size(bw, bh + h * .13f)
                )
                drawRect(Color(0xFF42BFFF).copy(alpha = .7f), Offset(x + bw * .45f, top - h * .012f), Size(bw * .10f, h * .012f))
                for (row in 0 until 5) {
                    if ((i + row) % 3 != 0) {
                        drawRect(Color(0xFF71D8FF).copy(alpha = .52f), Offset(x + bw * .25f, top + h * (.018f + row * .022f)), Size(bw * .16f, h * .006f))
                    }
                }
            }

            // Distinctive central needle towers.
            for ((x, height) in listOf(.79f to .34f, .91f to .43f, .965f to .29f)) {
                val baseY = horizon + h * .12f
                val topY = horizon - h * height
                val tower = Path().apply {
                    moveTo(w * x, topY)
                    lineTo(w * (x - .012f), baseY)
                    lineTo(w * (x + .012f), baseY)
                    close()
                }
                drawPath(tower, Brush.horizontalGradient(listOf(Color(0xFF07152A), Color(0xFF58BFFF), Color(0xFF102D54))))
                drawLine(Color(0xFF63D9FF).copy(alpha = .85f), Offset(w * x, topY), Offset(w * x, baseY), s * .002f)
            }

            // Foreground orbital platform and glass-like energy globe on the left.
            val platformY = h * .84f
            drawOval(
                Brush.verticalGradient(listOf(Color(0xFF1B5C9A), Color(0xFF07172F), Color(0xFF020817))),
                Offset(-w * .12f, platformY - h * .045f), Size(w * .54f, h * .17f)
            )
            drawOval(
                Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF38D9FF), Color(0xFF7D8CFF), Color.Transparent)),
                Offset(-w * .10f, platformY - h * .045f), Size(w * .52f, h * .15f),
                style = Stroke(s * .009f)
            )
            val globe = Offset(w * .17f, h * .64f)
            val globeR = s * .115f
            drawCircle(
                Brush.radialGradient(listOf(Color(0x883CCBFF), Color(0x443E89E7), Color(0x113A63B6), Color.Transparent), globe, globeR * 1.55f),
                globeR * 1.55f, globe
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0xCC61DFFF), Color(0x6640A5F7), Color(0x22203D8A), Color.Transparent), Offset(globe.x - globeR * .25f, globe.y - globeR * .3f), globeR * 1.3f),
                globeR, globe, style = Stroke(s * .004f)
            )
            drawOval(Color(0xAA51DFFF), Offset(globe.x - globeR * 1.28f, globe.y - globeR * .20f), Size(globeR * 2.56f, globeR * .42f), style = Stroke(s * .003f))
            drawOval(Color(0x9957AFFF), Offset(globe.x - globeR * 1.18f, globe.y - globeR * .72f), Size(globeR * 2.36f, globeR * 1.44f), style = Stroke(s * .002f))

            // Low luminous deck lines create depth without becoming part of the controls.
            for (i in 0 until 6) {
                val y = h * (.89f + i * .018f)
                drawLine(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color(0xAA2CAFFF), Color(0x886A7BFF), Color.Transparent)),
                    Offset(w * .04f, y), Offset(w * .98f, y - h * .015f), (s * .002f).coerceAtLeast(1f)
                )
            }

            // Small distant craft silhouettes with blue/violet engine glows.
            for (i in 0 until 3) {
                val cx = w * (.69f + i * .115f)
                val cy = h * (.58f - i * .075f)
                val ship = Path().apply {
                    moveTo(cx - w * .018f, cy)
                    lineTo(cx + w * .025f, cy - h * .012f)
                    lineTo(cx + w * .008f, cy + h * .006f)
                    lineTo(cx - w * .014f, cy + h * .004f)
                    close()
                }
                drawPath(ship, Brush.horizontalGradient(listOf(Color(0xFF0B2348), Color(0xFFB8EFFF), Color(0xFF193A78))))
                drawCircle(Color(0xFF9A6CFF).copy(alpha = .9f), s * .004f, Offset(cx - w * .019f, cy + h * .006f))
                drawLine(Color(0xFF46CFFF).copy(alpha = .6f), Offset(cx - w * .03f, cy + h * .012f), Offset(cx - w * .08f, cy + h * .028f), s * .002f)
            }

            // Soft horizon bloom remains behind live desktop controls.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .55f to Color.Transparent,
                    1f to Color(0x55020A18)
                )
            )
            // The procedural scene sits above the root preset gradient. A restrained
            // preset tint keeps Nebula, Punk and Aurora visibly distinct in BEYOND LIMITS.
            drawRect(infinityFuturisticSceneTint(backgroundPreset))
        }
    }
}


/** Color wash applied above the procedural scene so the selected preset remains visible. */
internal fun infinityFuturisticSceneTint(preset: String): Color = when (preset.lowercase()) {
    "punk" -> Color(0x664E0A3B)
    "aurora" -> Color(0x5540B6A4)
    "nebula" -> Color(0x443C63D8)
    "interstellar" -> Color.Transparent
    else -> Color(0x443C63D8)
}
