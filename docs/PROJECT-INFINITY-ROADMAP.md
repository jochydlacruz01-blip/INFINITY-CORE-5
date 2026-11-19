# PROJECT INFINITY — Completion Roadmap

Target branch: `INFINITY-0.9`
Current app version remains 0.10.0 / versionCode 7 until a deliberate release milestone.
This roadmap is a work plan; checkboxes indicate completion only after evidence and testing.

## Stabilization and regression
- [x] CI Debug/Release build and signed Release artifact validation workflow
- [x] Latest privacy changes passed Debug/Release builds, lint, unit tests and Release APK signature verification (CI #212); Debug and signed Release artifacts published.
- [x] Unit tests for CONNECT host/port validation
- [x] Unit tests for ORBIT system metric rules
- [x] Bounded notification text rules
- [x] File Manager capability helper rules and regression tests (CI verified)
- [ ] Physical-device SAF testing across document providers
- [x] Retain failed delete targets in selection for retry (logic covered by unit tests; CI #192 verified)
- [x] Expand filename boundary, MIME fallback and retry-order regression tests (CI #195 verified)
- [x] File Manager refresh selection-retention regression test (CI #199 verified)
- [x] Implement partial-failure and cancellation-state recovery for multi-file operations (logic and regression tests CI #208 verified; provider/device acceptance tests remain pending)

- [x] CONNECT endpoint duplicate matching normalizes internationalized DNS names (CI #196 verified)


- [x] Notification text sanitizer removes control characters while preserving readable line breaks and tabs (unit regression tests added; pending CI validation).

## Core modules
- [ ] File Manager: provider compatibility, refresh, navigation, selections, clipboard operations, viewers
- [x] SAF enumeration tolerates providers omitting optional MIME/size columns; missing size is displayed as unavailable (CI #217 verified).
- [ ] ORBIT: verify metric labels, sampling lifecycle, thermal and battery reporting, layout on narrow screens
- [x] ORBIT monitor clears displayed telemetry when a sampling cycle fails instead of presenting stale values (validated by successful CI #220)
- [ ] Notifications: physical-device permission UX, disconnect/reconnect, privacy display options, open/dismiss behavior
- [x] Add direct system-settings shortcut when notification-listener permission is not granted (validated by successful CI #220; device UX still pending)
- [x] Notification privacy redaction: hidden titles/messages are not retained in the in-memory feed; existing feed entries are sanitized when protection is enabled (unit tests and CI #212 verified). Android permission and physical-device behavior remain pending.
- [ ] CONNECT: profile management UX, security review, RDP native feasibility and device testing

## Real RDP integration gate
- [ ] Pin an upstream FreeRDP release and audit license/security changes
- [ ] Build native libraries reproducibly for supported Android ABI(s)
- [ ] Confirm compatibility with existing minSdk and toolchain without broad project upgrades
- [ ] Validate JNI lifecycle and real server connection on physical device
- [ ] Validate TLS/certificate prompt behavior, keyboard, scaling, reconnect and disconnect
- [ ] Integrate only after the isolated spike passes; never display connected without native confirmation

## Release readiness
- [ ] UI and accessibility review on phone/tablet portrait/landscape
- [ ] Install and upgrade test using signed APK
- [ ] Security/privacy review, no credentials in logs, clipboard or persistent storage
- [ ] Complete regression suite and publish verified artifacts

Do not mark sections complete based on builds alone. Device-specific acceptance tests require actual device evidence.
