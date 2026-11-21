package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Prominent responsive PROJECT INFINITY wordmark for the futuristic tablet desktop. */
@Composable
internal fun InfinityDesktopMasthead(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val compact = maxWidth < 760.dp
        val logoWidth = (maxWidth * 0.46f).coerceIn(280.dp, 420.dp)
        val logoHeight = if (compact) 136.dp else 158.dp
        val titleSize = if (compact) 42.sp else 52.sp

        Box(
            modifier = Modifier
                .width(logoWidth)
                .height(logoHeight)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xCC245A91),
                            Color(0xAA102B50),
                            Color(0x99101A36),
                            Color(0x55101830)
                        )
                    )
                )
                .border(
                    1.4.dp,
                    Brush.linearGradient(
                        listOf(Color(0xFF8BEAFF), Color(0xAA6A83FF), Color(0xFFE3F5FF))
                    ),
                    RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(logoWidth * 0.91f)
                    .height(if (compact) 92.dp else 108.dp)
                    .rotate(-7f)
                    .border(
                        1.dp,
                        Brush.sweepGradient(
                            listOf(
                                Color(0x00FFFFFF),
                                Color(0x9977DFFF),
                                Color(0x00FFFFFF),
                                Color(0xAA9B7CFF),
                                Color(0x00FFFFFF)
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                Modifier
                    .width(logoWidth * 0.78f)
                    .height(if (compact) 78.dp else 92.dp)
                    .rotate(7f)
                    .border(1.dp, Color(0x447CEBFF), CircleShape)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    "P R O J E C T",
                    color = Color(0xFFEAF7FF),
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (compact) 3.5.sp else 5.sp
                )
                Text(
                    "INFINITY",
                    color = Color.White,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = if (compact) 2.5.sp else 3.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    "B E Y O N D   L I M I T S",
                    color = Color(0xFFD8F4FF),
                    fontSize = if (compact) 8.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = if (compact) 1.6.sp else 2.2.sp
                )
            }
        }
    }
}
