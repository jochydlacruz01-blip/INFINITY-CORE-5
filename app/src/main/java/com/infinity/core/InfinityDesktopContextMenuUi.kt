package com.infinity.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Desktop context menu UI; caller controls trigger, state and side effects. */
@Composable
internal fun InfinityDesktopContextMenu(
    expanded: Boolean,
    iconsVisible: Boolean,
    onAction: (InfinityDesktopContextAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.background(Color(0xF0182A42), RoundedCornerShape(16.dp))
    ) {
        infinityDesktopContextActions(iconsVisible).forEach { action ->
            DropdownMenuItem(
                text = { Text(infinityDesktopContextActionLabel(action), color = Color.White) },
                onClick = { onDismiss(); onAction(action) }
            )
        }
    }
}

