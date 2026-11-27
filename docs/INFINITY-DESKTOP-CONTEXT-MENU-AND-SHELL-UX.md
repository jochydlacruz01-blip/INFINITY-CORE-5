# Desktop context menu and shell experience

## Proposed desktop right-click menu
- Sort icons by name, type, or saved/manual order.
- Align to grid; show icons; refresh layout.
- Desktop appearance, wallpaper, icon size, and workspace switcher.

The menu should be anchored to the pointer/touch location and dismiss on outside click, Escape, or selection. On touch-only devices expose a long-press alternative. Actions should be routed to the desktop state owner, not independently mutate UI state.

## Dock simplification
Remove the direct "Personalizar" shortcut from the Dock/task surface in the shell redesign and expose appearance customization from the desktop context menu and a dedicated desktop settings panel. Do not remove the underlying settings screen or its routes.

## Arrangement
Manual order must persist; automatic name/type order should remain a view transformation until the user explicitly saves it. Grid arrangement must account for viewport, reserved top/bottom zones and avoid overlapping icons.

## Shell quality goals
- Right-click / long press context menu with keyboard dismissal.
- Accessible focus, selected icon feedback and keyboard shortcuts.
- Workspace awareness, persistent arrangement and display-safe bounds.
- Settings surface for wallpaper, icon size, density, theme and taskbar/dock behavior.
- Maintain existing application windows and avoid destructive reset actions.
This document and rules package are not UI wiring; production integration requires connecting menu state/actions to Compose desktop state.
