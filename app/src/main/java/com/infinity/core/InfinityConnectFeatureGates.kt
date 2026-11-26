package com.infinity.core

/**
 * Remote capability kill-switches.
 * Keep false until the Samsung tablet / Windows PC interoperability test is completed
 * with an installable build and the corresponding security checklist.
 */
internal object InfinityConnectFeatureGates {
    const val REMOTE_DESKTOP_VALIDATED = false
    const val FILE_TRANSFER_VALIDATED = false

    /** Enforce kill-switches at the transport boundary, not only in Compose visibility. */
    internal fun requireCapabilitiesEnabled(requestedCapabilities: Set<String>) {
        if (requestedCapabilities.any { it == "remote.display" || it == "remote.input" }) {
            requireRemoteDesktopEnabled()
        }
        if ("file.transfer" in requestedCapabilities) {
            requireFileTransferEnabled()
        }
    }

    internal fun requireRemoteDesktopEnabled() {
        check(REMOTE_DESKTOP_VALIDATED) {
            "INFINITY remote desktop is blocked until physical security validation is accepted."
        }
    }

    internal fun requireFileTransferEnabled() {
        check(FILE_TRANSFER_VALIDATED) {
            "INFINITY file transfer is blocked until physical security validation is accepted."
        }
    }
}
