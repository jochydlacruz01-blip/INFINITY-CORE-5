package com.infinity.core

/** Engine-neutral keyboard commands. Native RDP adapter will translate these to scan codes. */
enum class ConnectKey(val scanCode: Int) {
    ESC(0x01), TAB(0x0F), ENTER(0x1C), BACKSPACE(0x0E), SPACE(0x39),
    LEFT(0x4B), RIGHT(0x4D), UP(0x48), DOWN(0x50),
    CTRL(0x1D), ALT(0x38), SHIFT(0x2A),
    F1(0x3B), F2(0x3C), F3(0x3D), F4(0x3E), F5(0x3F), F6(0x40),
    F7(0x41), F8(0x42), F9(0x43), F10(0x44), F11(0x57), F12(0x58)
}
data class ConnectKeyEvent(val key: ConnectKey, val pressed: Boolean)
data class ConnectKeySequence(val events: List<ConnectKeyEvent>) {
    init { require(events.isNotEmpty()) }
}
object InfinityConnectKeyboardRules {
    fun tap(key: ConnectKey) = ConnectKeySequence(listOf(ConnectKeyEvent(key,true), ConnectKeyEvent(key,false)))
    /** Ensure modifier press precedes the key and release follows in reverse order. */
    fun chord(modifiers: List<ConnectKey>, key: ConnectKey): ConnectKeySequence {
        require(modifiers.distinct().size == modifiers.size) { "Modifiers must be unique." }
        require(key !in modifiers) { "Main key cannot also be a modifier." }
        val down = modifiers.map { ConnectKeyEvent(it,true) } + ConnectKeyEvent(key,true)
        val up = listOf(ConnectKeyEvent(key,false)) + modifiers.asReversed().map { ConnectKeyEvent(it,false) }
        return ConnectKeySequence(down + up)
    }
}
