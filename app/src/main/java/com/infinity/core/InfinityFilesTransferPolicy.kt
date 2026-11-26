package com.infinity.core

/**
 * Guards file transfers before calling a Storage Access Framework provider.
 * Document IDs are scoped to the selected tree.
 */
internal object InfinityFilesTransferPolicy {
    fun validate(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        destinationDocumentId: String
    ): String? {
        if (sourceDocumentId.isBlank() ||
            sourceParentDocumentId.isBlank() ||
            destinationDocumentId.isBlank()
        ) {
            return "No se pudo identificar el archivo o la carpeta de destino."
        }
        if (destinationDocumentId == sourceDocumentId) {
            return "No se puede usar el archivo seleccionado como carpeta de destino."
        }
        if (destinationDocumentId == sourceParentDocumentId) {
            return "Selecciona una carpeta distinta de la carpeta de origen."
        }
        return null
    }
}
