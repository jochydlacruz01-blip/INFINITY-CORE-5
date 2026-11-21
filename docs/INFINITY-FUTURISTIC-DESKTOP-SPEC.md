# INFINITY Futuristic Desktop — implementation contract

Reference image supplied by the user is the design target.

## Required visual fidelity
- Desktop icon column on the left; recognizable icons, captions, spacing and draggable placement.
- Centered INFINITY identity/ring and futuristic planet/city environment, without turning the reference image into an overlay that blocks interaction.
- Clock/date in the top-right corner, updating from device time.
- Bottom taskbar/dock with Start/search, app shortcuts, active-task indicators, system controls and notification entry.
- Right-click / touch long-press context menu, with rearrangement, grid, icon visibility, wallpaper and appearance actions.
- Responsive layout for landscape and portrait; prevent dock, clock, menus and icon column from clipping or colliding.
- Preserve the current/legacy wallpaper and theme as a selectable option.
- User wallpaper selection remains supported.

## Catalog groundwork
`InfinityFuturisticDesktopPreset.kt` adds a stable preset ID catalog and layout contract, with tests. It does not yet render the supplied PNG nor replace the MainActivity Compose layout. The supplied artwork still needs to be bundled as an app resource and the desktop rendering/preset selector wired into MainActivity.

## Integration sequence
1. Bundle the optimized artwork as a drawable/resource and preserve a legacy option.
2. Implement preset selection and persisted preference.
3. Render desktop layout elements as live Compose controls, not artwork baked into the screen.
4. Integrate context-menu sorting/actions and persisted icon layout.
5. Test landscape/portrait, long press, keyboard, visibility, docking and background switching.
6. Run Android CI; record result without claiming unverified success.
