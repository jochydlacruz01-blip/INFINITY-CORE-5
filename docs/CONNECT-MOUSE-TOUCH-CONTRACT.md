# INFINITY CONNECT — Mouse & touch command contract

Pure Kotlin command builders, independent of the native RDP implementation.

- Click: button down followed by button up at the same remote point.
- Double click: two click cycles.
- Drag: button down at origin, move to destination, release.
- Scroll: nonzero signed delta (positive up, negative down).
- Move: single movement event.
- Mouse coordinates should be mapped through the viewport rules first.
- An engine adapter must preserve ordering and release buttons on cancellation/disconnect to avoid stuck input.

This contract is not an RDP transport implementation; native engine integration and device testing remain outstanding.
