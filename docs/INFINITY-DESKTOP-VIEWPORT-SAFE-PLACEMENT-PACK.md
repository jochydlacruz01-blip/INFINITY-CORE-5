# Desktop viewport-safe placement pack

Adds a deterministic, pure placement contract for a finite desktop viewport.

- Reserves top and bottom areas for desktop identity/system UI and Dock.
- Sanitizes non-finite or invalid dimensions and gaps.
- Deduplicates icon IDs.
- Places only the icons that fit without overlap.
- Reports remaining IDs in `overflowIds`; callers must expose these through scrolling or an overflow surface rather than stacking them.

This helper is intentionally additive and does not replace the existing drag, saved-offset, overlap-resolution, Dock, Orbit, or window-management paths. UI adoption is a separate integration step and must preserve hidden/overflow icon discoverability.
