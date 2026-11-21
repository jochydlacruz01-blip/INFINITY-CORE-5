# ORBIT — Integrated diagnostic pack

This pack adds a pure, testable diagnostic snapshot layer over existing ORBIT health rules.

- Bounds and sanitizes battery, RAM, storage and thermal readings.
- Keeps unavailable readings explicitly missing; never substitutes zero.
- Produces stable advisory alerts and an overall status.
- Overall status prioritizes CRITICAL, WARNING, then UNKNOWN for incomplete data, then NORMAL.
- Spanish summary labels are deterministic.
- No device actions, permissions, collection, background service or thermal control are added.

Integrators must provide already collected and freshness-checked values. This helper does not collect telemetry or guarantee a live snapshot.
