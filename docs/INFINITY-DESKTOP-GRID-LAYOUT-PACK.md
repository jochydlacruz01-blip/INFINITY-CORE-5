# INFINITY Desktop Grid Layout Pack

This pack adds a pure, deterministic grid-position calculator and Spanish labels for context-menu actions. It provides a testable layout foundation for the desktop's grid-alignment command and menu wiring.

- Calculates columns from viewport width and item dimensions.
- Wraps icons onto subsequent rows.
- Sanitizes invalid viewport/gap inputs.
- Does not mutate preferences or reposition UI by itself: shell integration remains a follow-on task.
- Unit tests cover wrapping, empty input, invalid metrics and action labels.

