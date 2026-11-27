package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InfinityDesktopContextMenuUiLabelsTest {
    @Test fun allCommandsHaveNonBlankLabels() {
        InfinityDesktopContextAction.values().forEach { action ->
            assertEquals(false, infinityDesktopContextActionLabel(action).isBlank())
        }
    }
    @Test fun menuLabelChangesAreStableForKeyCommands() {
        assertEquals("Ordenar por nombre", infinityDesktopContextActionLabel(InfinityDesktopContextAction.SORT_NAME))
        assertEquals("Mostrar iconos", infinityDesktopContextActionLabel(InfinityDesktopContextAction.SHOW_ICONS))
        assertEquals("Actualizar escritorio", infinityDesktopContextActionLabel(InfinityDesktopContextAction.REFRESH_LAYOUT))
    }
}
