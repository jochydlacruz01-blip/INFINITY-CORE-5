package com.infinity.core

/** Engine-neutral mouse/touch commands for a remote desktop. */
enum class ConnectMouseButton { LEFT, RIGHT, MIDDLE }
enum class ConnectMouseAction { MOVE, DOWN, UP, CLICK, DOUBLE_CLICK, SCROLL }
data class ConnectMouseEvent(
    val action: ConnectMouseAction,
    val point: ConnectRemotePoint? = null,
    val button: ConnectMouseButton? = null,
    val scrollDelta: Int = 0
)
data class ConnectMouseSequence(val events: List<ConnectMouseEvent>) {
    init { require(events.isNotEmpty()) }
}

/** Stateless event builders. Adapters must send events in sequence and honor button release. */
object InfinityConnectMouseRules {
    fun move(point: ConnectRemotePoint) = ConnectMouseSequence(listOf(ConnectMouseEvent(ConnectMouseAction.MOVE, point)))
    fun click(point: ConnectRemotePoint, button: ConnectMouseButton = ConnectMouseButton.LEFT) =
        ConnectMouseSequence(listOf(ConnectMouseEvent(ConnectMouseAction.DOWN, point, button), ConnectMouseEvent(ConnectMouseAction.UP, point, button)))
    fun doubleClick(point: ConnectRemotePoint, button: ConnectMouseButton = ConnectMouseButton.LEFT) =
        ConnectMouseSequence(click(point, button).events + click(point, button).events)
    fun drag(from: ConnectRemotePoint, to: ConnectRemotePoint, button: ConnectMouseButton = ConnectMouseButton.LEFT): ConnectMouseSequence =
        ConnectMouseSequence(listOf(ConnectMouseEvent(ConnectMouseAction.DOWN, from, button), ConnectMouseEvent(ConnectMouseAction.MOVE, to, button), ConnectMouseEvent(ConnectMouseAction.UP, to, button)))
    /** Positive delta means scroll up; negative means scroll down. Zero is rejected. */
    fun scroll(point: ConnectRemotePoint, delta: Int): ConnectMouseSequence {
        require(delta != 0) { "Scroll delta must not be zero." }
        return ConnectMouseSequence(listOf(ConnectMouseEvent(ConnectMouseAction.SCROLL, point, scrollDelta = delta)))
    }
}
