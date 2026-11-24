# INFINITY DOCK — Responsive overflow pack

## Problem addressed
The Dock contains fixed-size primary actions, optional search, running-window buttons and a clock. On phone-sized widths those children can exceed the available horizontal space, making trailing actions difficult or impossible to reach.

## Change
- Added `infinityDockRequiresHorizontalScroll(viewportWidthDp)`.
- The Dock uses Compose horizontal scrolling below 600 dp.
- Tablet layouts (600 dp and wider) keep the existing full-width/centered arrangement.
- Existing task-count limiting and the “more windows” action remain unchanged.
- The Dock remains anchored to the bottom edge; this change does not move or cover the desktop work area.

## Validation
Unit tests cover phone widths, the tablet threshold, and invalid viewport values. CI must still run Debug/Release unit tests, lint, signed APK builds, and signature verification. Physical-device verification is still required for touch/gesture interaction and landscape layouts.
