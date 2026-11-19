# PROJECT INFINITY — Completion Roadmap

Target branch: `INFINITY-0.9`
Current app version remains 0.10.0 / versionCode 7 until a deliberate release milestone.
This roadmap is a work plan; checkboxes indicate completion only after evidence and testing.

## Stabilization and regression
- [x] CI Debug/Release build and signed Release artifact validation workflow
- [x] Unit tests for CONNECT host/port validation
- [x] Unit tests for ORBIT system metric rules
- [x] Bounded notification text rules
- [x] File Manager capability helper rules and regression tests (CI verified)
- [ ] Physical-device SAF testing across document providers
- [x] Retain failed delete targets in selection for retry (logic covered by unit tests; CI #192 verified)
- [x] Expand filename boundary, MIME fallback and retry-order regression tests (CI pending)
- [ ] Exercise partial failure and cancellation handling for multi-file operations (provider/device tests pending)

## Core modules
- [ ] File Manager: provider compatibility, refresh, navigation, selections, clipboard operations, viewers
- [ ] ORBIT: verify metric labels, sampling lifecycle, thermal and battery reporting, layout on narrow screens
- [ ] Notifications: permission UX, disconnect/reconnect, privacy display options, open/dismiss behavior
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
