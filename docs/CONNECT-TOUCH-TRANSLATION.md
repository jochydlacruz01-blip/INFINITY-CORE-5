# INFINITY CONNECT — Touch translation pack

Gesture-recognition UI can supply classified intent; this pure rules module translates it into engine-neutral mouse commands.

| Gesture | Translation |
| --- | --- |
| Tap | Left click (or requested button) |
| Double tap | Two left click cycles |
| Long press | Right click |
| Drag | Down, move, release |
| Scroll | Signed scroll delta |
| Cancel drag | Explicit left-button release |

Recognition thresholds, multi-touch arbitration, and real RDP delivery are deliberately delegated to UI/engine integration. CancelDrag is a safety release event, not a complete input-state manager.
