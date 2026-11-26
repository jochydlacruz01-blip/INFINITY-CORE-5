# INFINITY Desktop Context Command Plan Pack

This pack adds a UI-independent command-plan boundary for context-menu integration.

- Command reducers remain side-effect-free.
- Arrangement commands produce deterministic ID-to-offset placement maps.
- Refresh has a separate placement path and increments the reducer refresh token.
- Wallpaper, appearance and workspace commands are represented as explicit shell requests; this layer does not open UI or mutate Android preferences.
- Duplicate icon IDs are collapsed by the existing placement helper.

**Integration boundary:** MainActivity still needs to bind these plans to UI, preferences, pointer gestures and dialogs. This pack does not claim those user-facing commands are wired.

