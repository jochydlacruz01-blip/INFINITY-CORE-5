# INFINITY Desktop Context State Reducer Pack

This pack adds a pure immutable reducer for desktop context-menu commands, to make preference changes deterministic and unit-testable before wiring them to the Compose desktop surface.

- Sorting commands select a persisted arrangement mode.
- Show/hide changes a visibility preference and the menu catalog exposes the inverse toggle.
- Refresh increments a token so a UI integration can trigger a layout refresh.
- Icon-size command increases size by a bounded step (24–96 px).
- Appearance, wallpaper and workspace commands are intentionally no-ops in the reducer: these need UI/navigation handlers and do not silently alter unrelated state.

This is state logic only. It does not claim to be connected to MainActivity UI, saved preferences, or device-level context-click gestures yet.
