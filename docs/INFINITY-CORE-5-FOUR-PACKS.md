# PROJECT INFINITY-CORE-5 — Dock, AURORA, Control Center and Desktop

This note records the intended behavior and verification boundaries for the four integrated desktop modules. It does not treat an unexecuted emulator check as a passing test.

## Dock INFINITY

- `dock_visible` persists the visible/hidden state.
- `dock_position` supports `TOP` and `BOTTOM`; both positions reserve the measured safe area for desktop content.
- A visible recovery action remains available when the Dock is hidden.
- Automatic hide is intentionally not enabled for the touch-first tablet/phone layout: hiding a primary navigation surface behind hover behavior would make it inaccessible on touch-only devices.
- Scale, icon scale, transparency, spacing, horizontal scrolling, indicators and clock format remain separate preferences. The five existing desktop color themes are not changed.

## AURORA windows

- The active window collection in the desktop shell remains the canonical source for open, minimized, maximized and workspace state.
- `focusedWindowId` identifies the active window; z-order and window records are persisted separately.
- Opening a window reuses an existing window of the same kind in the same workspace instead of creating an accidental duplicate.
- Minimize, restore, maximize and close operate on the window record. Maximization retains the original geometry so restore can return to the prior size and position.
- Drag/resize geometry is treated as a transaction: commit persists the new bounds; cancellation restores the original bounds. Maximization and viewport changes cancel an active gesture transaction before applying new geometry.
- Dock running/minimized indicators are derived from the same window collection.

## Control Center

- Brightness and media volume use Android system APIs. A change is reported as applied only when the API accepts it and the resulting value can be read back.
- The media-volume slider is disabled when Android reports a fixed or unavailable stream.
- Wi-Fi and airplane-mode state are displayed only when Android exposes a readable value. Their tiles open the relevant Android settings screen rather than pretending to toggle restricted system settings.
- Bluetooth state may remain “Consultar en Android” on Android 12+ because reading adapter state is protected by `BLUETOOTH_CONNECT`; the app does not request that runtime permission solely to decorate a tile.
- Mobile-data changes remain under Android/SIM settings.

## Desktop icons and workspaces

- Built-in desktop icons retain stable identities and workspace-specific positions.
- User-pinned application shortcuts are stored per workspace, filtered against installed launcher applications, and removed from the shortcut list when an app is no longer installed.
- Shortcut IDs are stable hashes of package names; each shortcut opens the corresponding installed application and uses its application icon.
- Shortcut ordering and desktop positions pass through the existing collision-resolution/layout rules. Start's installed-app catalog is the add/remove surface for desktop shortcuts.
- Returning from Android settings refreshes the installed-app catalog so removed packages do not remain as stale shortcuts.

## Verification boundaries

- Unit tests cover pure preference, shortcut, geometry, bounds and status-mapping rules.
- Source compilation, Android unit tests and lint are reported separately from instrumented emulator tests.
- An emulator job that is queued, timed out or failed is not counted as a passing functional test.
- Physical Galaxy Tab S7 FE validation remains a separate device check and must not be inferred from emulator results.
