package com.infinity.core

/** Tracks inputs currently held down so an adapter can safely release them on cancellation. */
class InfinityConnectInputReleaseTracker {
    private val heldKeys = linkedSetOf<ConnectKey>()
    private val heldButtons = linkedSetOf<ConnectMouseButton>()

    fun keyDown(key: ConnectKey) { heldKeys.add(key) }
    fun keyUp(key: ConnectKey) { heldKeys.remove(key) }
    fun buttonDown(button: ConnectMouseButton) { heldButtons.add(button) }
    fun buttonUp(button: ConnectMouseButton) { heldButtons.remove(button) }

    /** Releases held buttons and keys in reverse press order within their tracked collections; idempotent. */
    fun releaseAll(): List<ConnectInputRelease> {
        val releases = heldButtons.toList().asReversed().map { ConnectInputRelease.MouseUp(it) } +
            heldKeys.toList().asReversed().map { ConnectInputRelease.KeyUp(it) }
        heldButtons.clear()
        heldKeys.clear()
        return releases
    }

    fun hasHeldInputs(): Boolean = heldKeys.isNotEmpty() || heldButtons.isNotEmpty()
}

sealed interface ConnectInputRelease {
    data class KeyUp(val key: ConnectKey) : ConnectInputRelease
    data class MouseUp(val button: ConnectMouseButton) : ConnectInputRelease
}
