package com.infinity.core

/** Explicit mobile gesture intent; UI supplies classified gestures after touch recognition. */
sealed interface ConnectTouchGesture {
    data class Tap(val point: ConnectRemotePoint, val button: ConnectMouseButton = ConnectMouseButton.LEFT) : ConnectTouchGesture
    data class DoubleTap(val point: ConnectRemotePoint) : ConnectTouchGesture
    data class LongPress(val point: ConnectRemotePoint) : ConnectTouchGesture
    data class Drag(val from: ConnectRemotePoint, val to: ConnectRemotePoint) : ConnectTouchGesture
    data class Scroll(val point: ConnectRemotePoint, val delta: Int) : ConnectTouchGesture
    data class CancelDrag(val lastPoint: ConnectRemotePoint) : ConnectTouchGesture
}

/** Converts classified touch gestures to engine-neutral mouse events. */
object InfinityConnectTouchRules {
    fun translate(gesture: ConnectTouchGesture): ConnectMouseSequence = when (gesture) {
        is ConnectTouchGesture.Tap -> InfinityConnectMouseRules.click(gesture.point, gesture.button)
        is ConnectTouchGesture.DoubleTap -> InfinityConnectMouseRules.doubleClick(gesture.point)
        is ConnectTouchGesture.LongPress -> InfinityConnectMouseRules.click(gesture.point, ConnectMouseButton.RIGHT)
        is ConnectTouchGesture.Drag -> InfinityConnectMouseRules.drag(gesture.from, gesture.to)
        is ConnectTouchGesture.Scroll -> InfinityConnectMouseRules.scroll(gesture.point, gesture.delta)
        is ConnectTouchGesture.CancelDrag -> ConnectMouseSequence(listOf(ConnectMouseEvent(ConnectMouseAction.UP, gesture.lastPoint, ConnectMouseButton.LEFT)))
    }
}
