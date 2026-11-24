package com.infinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfinityFilesTransferPolicyTest {
    @Test
    fun allowsDifferentDestinationFolder() {
        assertNull(
            InfinityFilesTransferPolicy.validate(
                sourceDocumentId = "source-file",
                sourceParentDocumentId = "folder-a",
                destinationDocumentId = "folder-b"
            )
        )
    }

    @Test
    fun rejectsCopyOrMoveIntoOriginalFolder() {
        assertEquals(
            "Selecciona una carpeta distinta de la carpeta de origen.",
            InfinityFilesTransferPolicy.validate("source-file", "folder-a", "folder-a")
        )
    }

    @Test
    fun rejectsUsingSourceFileAsDestination() {
        assertEquals(
            "No se puede usar el archivo seleccionado como carpeta de destino.",
            InfinityFilesTransferPolicy.validate("source-file", "folder-a", "source-file")
        )
    }

    @Test
    fun rejectsBlankDocumentIds() {
        assertEquals(
            "No se pudo identificar el archivo o la carpeta de destino.",
            InfinityFilesTransferPolicy.validate("", "folder-a", "folder-b")
        )
    }
}
