# PROJECT INFINITY — Architecture Master Document

**Status:** Architecture baseline for incremental implementation  
**Repository:** `jochydlacruz01-blip/INFINITY-CORE-5`  
**Current development line:** `INFINITY-PACK-14-REMOTE-FRAME-PIPELINE`  
**Android package:** `com.infinity.core`

> This document separates verified current capabilities from planned architecture. A module is not considered implemented merely because a screen, interface, or placeholder exists.

## 1. Product objective

PROJECT INFINITY is a native Android desktop environment with its own futuristic identity. It must work locally on Android independently of any Windows PC. INFINITY CONNECT is an optional, separately secured capability for connecting to a Windows computer.

The product has three architectural boundaries:

1. **INFINITY Android:** desktop shell, local apps, settings, storage access, notifications, and connection client.
2. **INFINITY Windows Companion:** a Windows service responsible for device identity and, in later approved stages, secure remote-session capabilities.
3. **INFINITY CONNECT protocol:** versioned contracts for pairing, identity, transport, session state, remote frames, and user input.

The Android desktop must remain useful when Windows is offline or the Companion is not installed.

## 2. Verified current state

The following facts were verified from source on the development branch:

- The Gradle root currently includes only `:app`; feature modules have not yet been split into independent Gradle modules.
- The Android app is Kotlin/Jetpack Compose, with current documented baseline AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, compile/target SDK 35, and min SDK 26.
- INFINITY CONNECT currently stores and edits Windows connection profiles, performs local preflight checks, and can probe a TCP endpoint.
- Pairing/fingerprint and session/frame pipeline primitives exist, but their presence does not establish a complete authenticated remote session.
- The Windows Companion currently exposes an identity-only endpoint. Its declared capability explicitly says remote desktop is not enabled.
- The current Android UI must continue to identify saved profiles as unverified until a real authenticated session is active.
- A remote-frame mailbox/packet abstraction is not itself a screen-capture pipeline, a network transport, or an RDP implementation.
- The current PR verification workflow is intentionally source-only: compile, unit tests, and lint. It does not build or publish APKs.

## 3. Architectural principles

1. **Local-first:** Android desktop and local applications function without Windows connectivity.
2. **Explicit trust:** no silent certificate acceptance, implicit pairing, or unauthenticated remote control.
3. **Least privilege:** request Android permissions only when a feature needs them; Windows service binds only to an explicitly configured private interface.
4. **No public RDP exposure:** never instruct users to expose TCP 3389 directly to the Internet. Prefer a private VPN or another authenticated private network.
5. **Truthful state:** distinguish saved, reachable, authenticated, connecting, connected, degraded, and disconnected states.
6. **Small changes:** integrate existing source before creating duplicate classes or modules.
7. **Compatibility:** do not upgrade the Android toolchain solely to satisfy an isolated native dependency experiment.
8. **Test gates:** every package must include automated checks and a documented manual/device test plan.
9. **No APK during development:** source checks only until the user explicitly authorizes a build.
10. **Privacy:** credentials must never appear in logs, analytics, clipboard, profile JSON, or crash reports.

## 4. Target logical architecture

```text
PROJECT INFINITY
├── android-app
│   ├── desktop-shell
│   ├── launcher-and-dock
│   ├── window-manager
│   ├── workspace
│   ├── local-apps
│   ├── file-access
│   ├── notifications
│   ├── settings
│   └── connect-client
├── shared-contracts
│   ├── protocol-version
│   ├── device-identity
│   ├── pairing
│   ├── session-state
│   └── remote-frame
├── windows-companion
│   ├── service-host
│   ├── identity-endpoint
│   ├── certificate-management
│   ├── screen-capture
│   ├── input-broker
│   └── session-host
├── remote-engine
│   ├── native-rdp-adapter
│   ├── jni-bridge
│   ├── frame-decoder
│   └── input-adapter
├── verification
│   ├── unit
│   ├── integration
│   ├── security
│   └── device
└── docs
    ├── architecture
    ├── protocol
    ├── threat-model
    ├── build-and-test
    └── user-guides
```

This is the target logical separation, not a claim that all listed Gradle or Windows projects already exist. Physical module extraction will occur only when dependency boundaries and tests justify it.

## 5. Android responsibilities

- Render the native INFINITY desktop and adapt it to phones/tablets.
- Manage windows, workspaces, dock, launcher, themes, notifications, and settings.
- Access user-selected files through Android Storage Access Framework (SAF).
- Store structured local state with Room only where relational queries and migrations are needed.
- Store small app preferences with DataStore; do not introduce a database for simple preferences.
- Store cryptographic keys in Android Keystore; never store raw credentials in ordinary preferences.
- Manage connection profiles and present explicit connection/session states.
- Host a dedicated remote viewer only after a real remote engine and authenticated transport are integrated.

## 6. Windows Companion responsibilities

- Run as a Windows service with an explicit installation/configuration path.
- Bind only to an administrator-selected private interface and configured port.
- Expose a minimal, versioned, authenticated API.
- Present a verifiable device identity and certificate.
- Keep screen capture and input-control capabilities disabled until their own threat model, authorization, and tests are approved.
- Never expose an unauthenticated remote shell or unrestricted file access.
- Record security-relevant events without recording passwords, private keys, or screen contents.

## 7. INFINITY CONNECT protocol boundaries

The protocol must be versioned and define:

- Device identity and protocol compatibility.
- Pairing challenge lifetime, replay protection, and user confirmation.
- Certificate pinning/trust renewal and revocation behavior.
- Authenticated transport and request authorization.
- Session lifecycle and explicit capability negotiation.
- Frame metadata, dimensions, pixel format, sequence number, and payload limits.
- Input event types, bounds, rate limits, and session authorization.
- Disconnect, timeout, cancellation, reconnect, and error semantics.

A successful TCP probe means only that a TCP endpoint accepted a connection. It does not prove identity, TLS trust, Windows authentication, or an active remote desktop session.

## 8. Data ownership and persistence

| Data | Owner | Storage policy |
|---|---|---|
| Desktop preferences | Android | DataStore |
| Structured app entities | Android | Room when relational persistence is required |
| User-selected file locations | Android | SAF persisted URI grants, only with user consent |
| Device connection profiles | Android | Validated local profile store; no secrets in profile JSON |
| Cryptographic private keys | Android | Android Keystore |
| Windows device identity | Windows Companion | OS-protected certificate/private-key storage |
| Active session state | Android client / Windows service | In-memory where possible; minimal diagnostic metadata only |
| Audit events | Each endpoint | Bounded, privacy-filtered logs |

No schema migration or database dependency should be added until an actual persisted domain model needs it. The current app does not yet justify splitting every logical area into a separate Gradle module.

## 9. Security baseline and threat model

Threats to address include hostile local networks, spoofed Windows endpoints, stolen pairing codes, replayed pairing messages, compromised certificates, credential leakage, malicious remote input, oversized frame payloads, denial-of-service through repeated connections, and accidental exposure of remote-control ports.

Required controls:

- TLS with authenticated peer identity; no trust-on-first-use without an explicit, reviewable user confirmation flow.
- Short-lived, single-use pairing challenges with replay protection.
- Explicit device allowlisting and revocation.
- Strict input validation, message size limits, timeouts, and rate limits.
- No password persistence in profile data or logs.
- No remote input before the user explicitly starts and authorizes a session.
- No public Internet binding by default; recommend a private VPN for different networks.
- Security tests for invalid certificates, expired/replayed challenges, malformed messages, and unauthorized input.

## 10. Implementation phases and acceptance gates

### Phase A — Repository baseline and architecture
- Inventory current source, workflows, branches, and tests.
- Record existing functionality and known limitations.
- Keep source-only CI and verify that no APK build/artifact steps are present.
- Acceptance: architecture and inventory documents match the repository.

### Phase B — Core Android stability
- Audit app startup, navigation, state ownership, lifecycle, and error handling.
- Preserve existing desktop, file manager, Orbit, Advanced, and workspace functionality.
- Acceptance: Kotlin compilation, unit tests, lint, and regression tests pass.

### Phase C — Local persistence
- Define only real persistent entities and their ownership.
- Introduce Room migrations if relational data is required; use DataStore for preferences.
- Acceptance: migration, corruption/recovery, and persistence tests pass.

### Phase D — Desktop experience
- Stabilize responsive desktop, dock, launcher, windows, workspace, themes, and touch targets.
- Acceptance: automated layout/state tests plus physical phone/tablet validation.

### Phase E — Identity and secure pairing
- Complete device identity, challenge lifecycle, trust confirmation, certificate pinning, and revocation.
- Acceptance: negative security tests pass; unknown or invalid identities never connect.

### Phase F — Windows Companion identity service
- Harden service lifecycle, private-interface binding, TLS, certificate provisioning, and diagnostics.
- Acceptance: Windows service tests and Android-to-Windows identity handshake on a private network.

### Phase G — Remote engine feasibility spike
- Pin and audit a stable FreeRDP revision, license, security advisories, ABIs, NDK/CMake requirements, and Android compatibility.
- Build in an isolated native integration branch/module without upgrading the main app toolchain.
- Acceptance: reproducible arm64-v8a native build and a documented license/security review.

### Phase H — Authenticated remote desktop
- Add native session lifecycle, certificate trust, credentials in memory only, frame decode/render, keyboard/pointer input, rotation, reconnect, and deterministic cleanup.
- Acceptance: real Windows session on physical hardware; connection states match actual engine state.

### Phase I — Advanced connectivity
- Add optional clipboard, file transfer, multi-display, and multiple-session support only after independent security reviews.
- Acceptance: per-feature authorization, bounded transfers, audit tests, and device tests.

### Phase J — Release readiness
- Complete accessibility, privacy, performance, battery, compatibility, backup/recovery, and physical-device testing.
- Generate a signed APK only after explicit user authorization.
- Acceptance: release checklist, reproducible build, verified signature, and installation/update test.

## 11. CI policy during active development

The active verification workflow must run source compilation, unit tests, static checks, and lint only. It must not run `assembleDebug`, `assembleRelease`, signing, APK upload, or artifact publication until the user explicitly authorizes it.

A green source-only workflow is not evidence of a distributable APK or a working remote desktop session.

## 12. Definition of done for every package

A package is complete only when:

1. Its scope and affected files are listed.
2. The implementation is present on the intended branch.
3. Automated tests cover normal, boundary, and failure cases.
4. Relevant compilation and lint checks pass.
5. Security and privacy implications are reviewed.
6. Existing features have regression coverage.
7. Limitations and manual device tests are documented.
8. The UI does not claim capabilities that are not actually operational.

## 13. Immediate next work

1. Add a repository inventory and source-only CI guard so future changes cannot silently reintroduce APK tasks during development.
2. Audit the current Connect pairing, preflight, identity endpoint, and remote-frame pipeline against the protocol/security requirements above.
3. Correct documentation that still describes the old APK-publishing workflow.
4. Add a bounded, process-local, single-use pairing challenge registry and regression tests; keep it explicitly separate from network enrollment until TLS client identity is implemented.
5. Define the CONNECT trust/protocol contract and map every capability to its real implementation status.
6. Continue with Android Keystore-backed certificate enrollment and a pinned TLS 1.3 client; do not begin FreeRDP native integration until the trust channel is verified.
