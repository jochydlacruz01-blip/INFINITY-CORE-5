# INFINITY CONNECT — Framebuffer viewport and input mapping

This batch adds pure Kotlin viewport geometry for the future RDP renderer.

## FIT layout
- Preserve the remote framebuffer aspect ratio.
- Center the framebuffer in the local viewport (letterbox/pillarbox).
- Translate touch/pointer coordinates from local space into remote pixel coordinates.
- Clamp touches in letterbox areas to framebuffer edges, preventing invalid pixel coordinates.

## API
- `InfinityConnectViewportRules.fit(local, remote)`
- `InfinityConnectViewportRules.mapPointer(localX, localY, viewport, remote)`
- `InfinityConnectViewportRules.normalizePointer(localX, localY, viewport)`

## Boundaries
This is tested geometry only. It does not implement touch event dispatch, keyboard injection, RDP transport, framebuffer rendering, or native FreeRDP. Those remain gated on real engine integration and device validation.
