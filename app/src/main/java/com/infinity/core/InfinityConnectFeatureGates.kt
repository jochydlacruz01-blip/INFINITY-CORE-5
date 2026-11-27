package com.infinity.core

/**
 * Production remains blocked until physical acceptance. The dedicated validation APK may
 * enable remote display/input only during its embedded, short-lived acceptance window.
 * File transfer is never enabled by the validation build.
 */
internal object InfinityConnectFeatureGates {
    const val REMOTE_DESKTOP_VALIDATED = false
    const val FILE_TRANSFER_VALIDATED = false

    internal fun isValidationWindowOpen(
        isValidationBuild: Boolean,
        expiresAtEpochSeconds: Long,
        nowEpochSeconds: Long
    ): Boolean = isValidationBuild && expiresAtEpochSeconds > 0L &&
        nowEpochSeconds > 0L && nowEpochSeconds < expiresAtEpochSeconds

    internal fun isRemoteDesktopEnabled(nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L): Boolean =
        REMOTE_DESKTOP_VALIDATED || isValidationWindowOpen(
            BuildConfig.INFINITY_REMOTE_ACCEPTANCE_BUILD,
            BuildConfig.INFINITY_REMOTE_ACCEPTANCE_EXPIRES_AT_EPOCH,
            nowEpochSeconds
        )

    internal fun requireCapabilitiesEnabled(requestedCapabilities: Set<String>) {
        if (requestedCapabilities.any { it == "remote.display" || it == "remote.input" }) {
            requireRemoteDesktopEnabled()
        }
        if ("file.transfer" in requestedCapabilities) requireFileTransferEnabled()
    }

    internal fun requireRemoteDesktopEnabled() {
        check(isRemoteDesktopEnabled()) {
            "INFINITY remote desktop is blocked outside its approved physical-validation window."
        }
    }

    internal fun requireFileTransferEnabled() {
        check(FILE_TRANSFER_VALIDATED) {
            "INFINITY file transfer is blocked until physical security validation is accepted."
        }
    }
}
