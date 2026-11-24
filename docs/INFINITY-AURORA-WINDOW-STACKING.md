# PACK 05 — AURORA WINDOWS: active window stacking

## Change
- A focused AURORA window is moved to the end of the desktop window stack.
- The last rendered window is therefore drawn above inactive windows.
- The operation preserves the window object, ID, workspace, size, position, minimized/maximized state, and relative order of every other window.
- Focusing a window from another workspace remains governed by the existing workspace visibility rules.

## Tests
- Active window moves to the top.
- Already-top window is stable.
- Unknown window ID leaves the stack unchanged.
- Empty stack is safe.

## Scope boundary
This pack improves stacking/focus behavior. It does not claim to implement a native Windows window manager or a full remote desktop session.
