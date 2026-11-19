# INFINITY CONNECT — isolated RDP spike

This branch isolates the native RDP feasibility work from the INFINITY-0.9 app branch.

## Goals
- Pin and inspect an upstream FreeRDP release before integrating it.
- Validate native Android compilation for arm64-v8a in a repeatable CI environment.
- Establish compatibility with INFINITY's current Android toolchain and min API without upgrading the main app.
- Prototype a lifecycle-safe JNI session bridge only after the upstream build is proven.

## Baseline
The starting revision is the current INFINITY-0.9 state. Main app changes are not made in this spike. The app currently has profile management, not real RDP connectivity.

## Gates before source integration
1. Pin exact FreeRDP release tag and source commit.
2. Review license, security advisories, dependencies, and Android minimum API.
3. Demonstrate a reproducible native build for Android arm64-v8a.
4. Confirm compatibility with app minSdk 26 or document a justified compatibility decision.
5. Prototype bridge, session lifecycle and explicit certificate trust.
6. Validate on physical Android devices against a real RDP server.
7. Merge only after existing INFINITY debug/release builds remain green.

## Non-goals
- No false connected status or mock session.
- No password persistence, logging or clipboard storage.
- No silent certificate acceptance.
- No main app toolchain upgrades as a workaround.
- No public exposure of RDP port 3389.

## Current spike status
Planning and isolation branch only. Native build and session tests are not yet proven.
