package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small desktop identity badge inspired by the PROJECT INFINITY reference desktop. */
@Composable
internal fun InfinityDesktopBrandBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xF20B1730), Color(0xE8142850))
                )
            )
            .border(1.dp, Color(0x775D9DFF), RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF32D7FF), Color(0xFF8D48FF))
                    )
                )
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "∞",
                color = Color.White,
                fontSize = 35.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                "PROJECT INFINITY",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Sistema Operativo Inteligente",
                color = Color(0xFFB9D6FF),
                fontSize = 10.sp
            )
        }
    }
}
