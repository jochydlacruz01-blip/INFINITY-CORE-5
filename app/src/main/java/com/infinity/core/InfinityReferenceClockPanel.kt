package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Transparent reference-style clock and innovation panel. */
@Composable
internal fun InfinityReferenceClockPanel(clock: String, date: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(clock, color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-1.5).sp, textAlign = TextAlign.End, maxLines = 1)
        Text(date, color = Color(0xFFF0F5FF), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End)
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.width(1.dp).height(122.dp).background(Color(0xAAE2F5FF)))
            Column(horizontalAlignment = Alignment.Start) {
                Text("INNOVACIÓN", color = Color(0xFFEAF7FF), fontSize = 18.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 5.sp)
                Text("TECNOLOGÍA", color = Color(0xFF00D9FF), fontSize = 18.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 5.sp)
                Text("PRODUCTIVIDAD", color = Color(0xFF00D9FF), fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Spacer(Modifier.height(14.dp))
                Text("Un solo entorno,\ninfinitas posibilidades.",
                    color = Color(0xFFF4F7FF), fontSize = 16.sp, fontStyle = FontStyle.Italic,
                    lineHeight = 20.sp)
            }
        }
    }
}
