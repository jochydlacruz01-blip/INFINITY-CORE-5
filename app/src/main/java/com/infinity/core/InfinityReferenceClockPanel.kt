package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Responsive glass clock and brand statement; remains a live, independent desktop widget. */
@Composable
internal fun InfinityReferenceClockPanel(clock: String, date: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = 360.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0x30101D3A), Color(0x18040B1B))),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, Color(0x335BCBFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val compact = maxWidth < 300.dp
        val titleSize = if (compact) 13.sp else 17.sp
        val lineSpacing = if (compact) 3.sp else 5.sp
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
            Text(
                clock, color = Color.White,
                fontSize = if (compact) 42.sp else 54.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp,
                textAlign = TextAlign.End, maxLines = 1
            )
            Text(
                date, color = Color(0xFFF0F5FF),
                fontSize = if (compact) 12.sp else 15.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                maxLines = 1
            )
            Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)
            ) {
                Box(
                    Modifier.width(2.dp).height(if (compact) 94.dp else 112.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFF35D8FF), Color(0x998D70FF), Color.Transparent)))
                )
                Column(horizontalAlignment = Alignment.Start) {
                    Text("INNOVACIÓN", color = Color(0xFFEAF7FF), fontSize = titleSize,
                        fontWeight = FontWeight.Medium, letterSpacing = lineSpacing, maxLines = 1)
                    Text("TECNOLOGÍA", color = Color(0xFF00D9FF), fontSize = titleSize,
                        fontWeight = FontWeight.Medium, letterSpacing = lineSpacing, maxLines = 1)
                    Text("PRODUCTIVIDAD", color = Color(0xFF00D9FF), fontSize = titleSize,
                        fontWeight = FontWeight.Bold, letterSpacing = if (compact) 2.sp else 3.sp, maxLines = 1)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Un solo entorno,\ninfinitas posibilidades.",
                        color = Color(0xFFF4F7FF), fontSize = if (compact) 12.sp else 15.sp,
                        fontStyle = FontStyle.Italic, lineHeight = if (compact) 16.sp else 19.sp
                    )
                }
            }
        }
    }
}
