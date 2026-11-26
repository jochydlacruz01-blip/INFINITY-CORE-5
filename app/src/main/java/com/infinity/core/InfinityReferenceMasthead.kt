package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * Interactive desktop-independent brand artwork. This is real Compose typography and shapes,
 * not a baked-in bitmap; it scales with the available tablet viewport.
 */
@Composable
internal fun InfinityReferenceMasthead(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.widthIn(max = 520.dp), contentAlignment = Alignment.Center) {
        val logoSize = (maxWidth.value * .15f).coerceIn(44f, 72f)
        Box(Modifier.size((logoSize * 3.35f).dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize(.98f).rotate(-18f)
                    .border(5.dp, Brush.sweepGradient(listOf(
                        Color(0xFF39DFFF), Color(0xFFB8F7FF), Color(0xFF397BFF),
                        Color(0xFF9C55FF), Color(0xFF39DFFF)
                    )), CircleShape)
                    .shadow(18.dp, CircleShape)
            )
            Box(
                Modifier.fillMaxSize(.78f).rotate(22f)
                    .border(2.dp, Brush.sweepGradient(listOf(
                        Color.Transparent, Color(0xFF50DFFF), Color(0xFF9E68FF),
                        Color.Transparent
                    )), CircleShape)
            )
            Text("∞", color = Color(0xFFB7F5FF), fontSize = (logoSize * 1.42f).sp,
                fontWeight = FontWeight.Black)
        }
        Column(
            Modifier.align(Alignment.Center).offset(y = (logoSize * .20f).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("P R O J E C T", color = Color(0xFFE7F5FF),
                fontSize = (logoSize * .27f).sp, fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic, letterSpacing = (logoSize * .055f).sp)
            Spacer(Modifier.height((logoSize * .02f).dp))
            Text("INFINITY", color = Color.White,
                fontSize = (logoSize * .78f).sp, fontWeight = FontWeight.Black,
                letterSpacing = (-logoSize * .045f).sp, textAlign = TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFF45BFFF), blurRadius = 18f
                    )
                ))
            Text("B E Y O N D   L I M I T S", color = Color(0xFFEAF5FF),
                fontSize = (logoSize * .22f).sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (logoSize * .025f).sp)
        }
    }
}

/**
 * Keep the reference masthead on desktop/tablet-sized viewports only.
 * The threshold preserves the original 600 dp visibility rule.
 */
internal fun infinityReferenceMastheadVisible(widthDp: Float): Boolean =
    widthDp.isFinite() && widthDp >= 600f
