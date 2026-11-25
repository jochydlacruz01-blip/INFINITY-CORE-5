package com.infinity.core

/** Security and lifecycle policy for INFINITY Portable PC (tablet-to-Windows access).
 * This is policy/state groundwork, not an RDP transport implementation.
 */
internal enum class InfinityPortablePcConnectionState {
    DISCONNECTED, CHECKING, CONNECTING, CONNECTED, RECONNECTING, FAILED
}
internal enum class InfinityPortablePcAccessMode { LOCAL_TABLET, REMOTE_WINDOWS }

internal data class InfinityPortablePcChecklist(
    val hostValid: Boolean,
    val vpnRecommended: Boolean,
    val credentialsStoredSecurely: Boolean,
    val remoteTransportImplemented: Boolean
) {
    val canAttemptConnection: Boolean get() = hostValid && remoteTransportImplemented
    val releaseReady: Boolean get() = hostValid && credentialsStoredSecurely && remoteTransportImplemented
}

internal fun infinityPortablePcChecklist(host: String, credentialsStoredSecurely: Boolean,
    remoteTransportImplemented: Boolean, usingPrivateVpn: Boolean): InfinityPortablePcChecklist =
    InfinityPortablePcChecklist(
        hostValid = isValidInfinityHost(host.trim()),
        vpnRecommended = !usingPrivateVpn,
        credentialsStoredSecurely = credentialsStoredSecurely,
        remoteTransportImplemented = remoteTransportImplemented
    )

internal fun infinityPortablePcNextState(current: InfinityPortablePcConnectionState,
    event: String): InfinityPortablePcConnectionState = when (event) {
    "check" -> if (current == InfinityPortablePcConnectionState.DISCONNECTED ||
        current == InfinityPortablePcConnectionState.FAILED) InfinityPortablePcConnectionState.CHECKING else current
    "connect" -> if (current == InfinityPortablePcConnectionState.CHECKING ||
        current == InfinityPortablePcConnectionState.DISCONNECTED) InfinityPortablePcConnectionState.CONNECTING else current
    "connected" -> if (current == InfinityPortablePcConnectionState.CONNECTING ||
        current == InfinityPortablePcConnectionState.RECONNECTING) InfinityPortablePcConnectionState.CONNECTED else current
    "retry" -> if (current == InfinityPortablePcConnectionState.CONNECTED ||
        current == InfinityPortablePcConnectionState.CONNECTING) InfinityPortablePcConnectionState.RECONNECTING else current
    "fail" -> if (current != InfinityPortablePcConnectionState.CONNECTED) InfinityPortablePcConnectionState.FAILED else current
    "disconnect" -> InfinityPortablePcConnectionState.DISCONNECTED
    else -> current
}

/** Do not allow remote actions to be presented as active while the session is not connected. */
internal fun infinityPortablePcRemoteControlsEnabled(state: InfinityPortablePcConnectionState): Boolean =
    state == InfinityPortablePcConnectionState.CONNECTED
