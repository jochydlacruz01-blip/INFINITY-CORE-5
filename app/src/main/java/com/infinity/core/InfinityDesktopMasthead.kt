package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PROJECT INFINITY wordmark treatment based on the supplied desktop reference.
 * Kept as a transparent overlay so the user's selected desktop wallpaper remains visible.
 */
@Composable
internal fun InfinityDesktopMasthead(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < 760.dp
        val logoWidth = (maxWidth * 0.68f).coerceIn(280.dp, 700.dp)
        val logoHeight = logoWidth * 0.44f
        val titleSize = if (compact) 50.sp else 72.sp
        val ringWidth = logoWidth * 1.04f

        Box(
            modifier = Modifier
                .width(logoWidth)
                .height(logoHeight),
            contentAlignment = Alignment.Center
        ) {
            // Ambient light behind the orbital mark; no opaque card hides the wallpaper.
            Box(
                Modifier
                    .width(logoWidth * 0.88f)
                    .height(logoHeight * 0.78f)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0x552C9FEA),
                                Color(0x332D65C9),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                Modifier
                    .width(ringWidth)
                    .height(logoHeight * 0.72f)
                    .rotate(-13f)
                    .border(
                        2.dp,
                        Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFF45DFFF),
                                Color(0xAA8D75FF),
                                Color(0x00C6F6FF),
                                Color(0xFF45DFFF)
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                Modifier
                    .width(logoWidth * 0.78f)
                    .height(logoHeight * 0.58f)
                    .rotate(12f)
                    .border(
                        1.dp,
                        Brush.sweepGradient(
                            listOf(
                                Color(0x0079DFFF),
                                Color(0xAA54CFFF),
                                Color(0x009C70FF),
                                Color(0x887D67FF),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "PROJECT",
                    color = Color(0xFFEAF3FF),
                    fontSize = if (compact) 11.sp else 14.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (compact) 5.sp else 7.sp
                )
                Text(
                    text = "INFINITY",
                    style = TextStyle(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFD8E6F5),
                                Color(0xFF8196B1),
                                Color(0xFFF8FCFF)
                            )
                        )
                    ),
                    fontSize = titleSize,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Black,
                    letterSpacing = if (compact) 1.5.sp else 2.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = "BEYOND LIMITS",
                    color = Color(0xFFE4F5FF),
                    fontSize = if (compact) 8.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = if (compact) 3.sp else 4.sp
                )
            }
        }
    }
}
