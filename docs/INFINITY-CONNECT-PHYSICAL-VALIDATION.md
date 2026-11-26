# PROJECT INFINITY — CONNECT and Desktop Physical Validation

Status: **PREPARATION ONLY — NOT EXECUTED ON PHYSICAL DEVICES**

This checklist is the acceptance gate for the Samsung Galaxy Tab S7 FE (Android 14) and Windows 10 Pro PC. Passing Android unit tests or compiling the Windows Companion does not count as physical interoperability evidence.

## Safety invariants

- Keep `InfinityConnectFeatureGates.REMOTE_DESKTOP_VALIDATED` and `FILE_TRANSFER_VALIDATED` set to `false` until every applicable test below passes and is reviewed.
- Do not publish or expose Windows RDP (TCP 3389) to the LAN or Internet. The Companion must reach the RDP service only through loopback (127.0.0.1).
- Use a private LAN or a separately reviewed private VPN. Never port-forward the Companion or RDP ports on a public router.
- Pairing is a deliberate administrator-approved operation. Verify the CSR fingerprint out of band before issuing a client certificate.
- Use a dedicated test Windows account with no administrator privileges and no production data.
- Do not place PFX files, private keys, bearer tokens, passwords, or unredacted connection logs in GitHub artifacts or screenshots.
- Test file transfer only inside the Companion-managed transfer directory. Do not grant arbitrary filesystem access.

## Test setup record

Record before testing:
- Android model, Android build number, app commit SHA, and installation source.
- Windows edition/build, Companion version and commit SHA.
- Network type and whether a private VPN is involved (do not record public IPs).
- CA SHA-256 fingerprint, server certificate SHA-256 fingerprint, and Windows device ID (store securely; redact from public screenshots).
- Confirmation that Windows Defender Firewall has no public inbound RDP rule and no router port-forward exists.

## A. Pairing, identity and certificate trust

| ID | Test | Pass condition |
|---|---|---|
| A1 | Generate Android identity key and CSR | Private key remains non-exportable in Android Keystore; CSR contains the expected public key. |
| A2 | Approve a valid CSR on Windows | Administrator approval is explicit and audited; issued client certificate matches the CSR public key. |
| A3 | Reject unknown or unapproved CSR | No certificate is issued and no session token is created. |
| A4 | Install certificate chain | Valid chain, validity dates, clientAuth EKU, key usage and out-of-band CA fingerprint are verified. |
| A5 | Tamper with CA/server fingerprint | TLS connection is rejected before credentials or session data are sent. |
| A6 | Expired, not-yet-valid, wrong-device or wrong-purpose certificate | Enrollment or connection is rejected with a safe user-facing message. |
| A7 | Revoke client certificate | New connections fail; existing session and issued bearer token are invalidated within the documented revocation window. |
| A8 | Remove Android identity | Private key alias, certificate chain, saved credential and pairing record are removed; old token cannot reconnect. |

## B. Authentication, authorization and session lifecycle

| ID | Test | Pass condition |
|---|---|---|
| B1 | Valid mTLS identity + correct device ID + approved session | Session reaches CONNECTED only after server authentication and authorization. |
| B2 | Missing/invalid bearer token | HTTP/WebSocket upgrade is rejected; no RDP socket is opened. |
| B3 | Expired, replayed or wrong-session token | Request is rejected; token is single-session and bound to the expected device/session. |
| B4 | Missing remote.display capability | RDP tunnel is not created. |
| B5 | Missing remote.input capability | RDP tunnel is not created. |
| B6 | User disconnects | Input is released, local loopback socket closes, WebSocket closes, queues stop, and lifecycle returns to IDLE. |
| B7 | App process is stopped or Android loses network | Session is terminated or expires server-side; stale credentials cannot silently resume it. |
| B8 | Reconnect after transient network loss | Bounded retry policy applies only to transient network/timeout/server errors; authentication/configuration failures are not retried. |
| B9 | Administrator revokes device/session during active connection | Server stops accepting frames/input and invalidates the session; client cannot reconnect using old token. |
| B10 | Simultaneous connect/disconnect callbacks | Lifecycle remains in a legal state; no duplicate session or leaked socket is observed. |

## C. Remote desktop and input

| ID | Test | Pass condition |
|---|---|---|
| C1 | Display negotiation | Frame dimensions, orientation and scaling match the tablet viewport without stretching or unbounded allocation. |
| C2 | Keyboard input | Printable keys, modifiers, enter, backspace, escape and layout mapping are correct. |
| C3 | Touch/mouse input | Coordinates are transformed correctly for portrait/landscape and display scaling. |
| C4 | Disconnect while keys/buttons are held | All pressed keys/buttons are released on both peers. |
| C5 | Rotate tablet during session | No stale viewport, stuck input or unrecoverable session state. |
| C6 | Background/resume Android app | Session behavior follows explicit policy; no ghost session remains after timeout. |
| C7 | Resource pressure | Bounded frame queues apply backpressure; memory remains stable and the session fails closed on overflow. |

## D. Managed file transfer

| ID | Test | Pass condition |
|---|---|---|
| D1 | Transfer a small allowed file | Hash of received file matches source; destination is inside managed directory. |
| D2 | Transfer large file and interrupt network | Partial file is not exposed as complete; cleanup/resume policy is deterministic. |
| D3 | Path traversal, absolute path, symlink and alternate data stream attempts | Every unsafe path is rejected; no file escapes managed directory. |
| D4 | Unauthorized role or missing transfer capability | Transfer is rejected before opening a file handle. |
| D5 | Disk full, permission denied and duplicate name | Safe error, no overwrite unless explicitly authorized, and no orphaned temporary file. |
| D6 | Revoke session during transfer | Transfer stops and incomplete data is removed or quarantined. |

## E. Network exposure and Windows hardening

- From a second LAN device, verify TCP 3389 on the Windows PC is not reachable.
- Verify the Companion listener is bound only to the intended private interface and protected by Windows Firewall; document the exact port and rule scope.
- Verify the Companion's RDP upstream target is fixed to 127.0.0.1 and cannot be changed by client input.
- Verify TLS 1.3, server certificate pinning, client certificate authentication and expected device identity checks.
- Verify logs do not include passwords, bearer tokens, private-key material, PFX contents or raw sensitive RDP payloads.
- Verify Companion service shutdown/restart closes listeners, sessions, sockets and temporary files.

## F. Visual desktop acceptance

Capture actual screenshots from the running app (not mockups or the reference wallpaper alone):
1. Tablet landscape: full desktop, left rail, central identity, Dock, clock/date and system controls.
2. Tablet portrait: no clipped rail, Dock remains reachable, menus scroll inside their available region.
3. Phone portrait and landscape: adaptive layout, readable labels and no overlapping touch targets.
4. START open: Dock remains visible and tappable.
5. ORBIT open: workspace controls remain reachable and switching workspaces preserves their icons/windows.
6. AURORA window open, maximized, restored, minimized and dragged: window never covers the Dock or system gesture region.
7. CONTROL and notification panels: no overlap, correct z-order and working controls.
8. CONNECT panel in disconnected, pairing, connecting, connected, failed and revoked states (redact host/device identifiers).

For every screenshot record device, orientation, app SHA, screen density and test ID. Keep original images private if they contain identifying data.

## Evidence and release gate

Attach sanitized logs, screenshot filenames, test results and the two device build identifiers to the validation record. Each row must be marked PASS, FAIL or NOT RUN with timestamp and tester.

**Do not set either remote feature gate to true until:**
1. all applicable A–E tests pass on the real tablet and PC;
2. certificate revocation and disconnect cleanup are demonstrated;
3. there are no unresolved high/critical security findings;
4. screenshots in F demonstrate the required visual states;
5. the test record is reviewed and tied to the exact source SHAs.

Current state for this document: all physical tests are **NOT RUN**. No claim of end-to-end CONNECT interoperability is made.


## Automated coverage boundary (source audit)

The current Android unit tests exercise **translation and policy logic**, not a live Windows input/display session:
- Keyboard: `InfinityConnectKeyboardRulesTest` verifies key down/up ordering, modifier chords and invalid chords.
- Mouse: `InfinityConnectMouseRulesTest` verifies click, drag, double-click and scroll event construction.
- Touch: `InfinityConnectTouchRulesTest` verifies tap, long-press, drag, cancel-release and scroll translation.
- Viewport: `InfinityConnectViewportRulesTest` verifies aspect-ratio fitting and pointer coordinate mapping.
- RDP tunnel: `InfinityConnectRdpTunnelTest` currently verifies capability rejection for identity-only and display-without-input grants. It does **not** prove a successful live tunnel, frame delivery, input reaching Windows, reconnect, or real disconnect cleanup.
- FreeRDP client: `InfinityFreeRdpClientTest` currently verifies certificate fingerprint normalization. It does **not** prove native RDP interoperability with a Windows host.
- Session controller tests use a fake engine; they verify lifecycle/cancellation contracts, not network authentication against the Companion.
- Windows Companion CI currently runs its .NET security/unit test project, but the CI success and published x64 folder are not evidence of a signed production installer or physical tablet interoperability.

Therefore the current automated suite does not yet demonstrate end-to-end screen frames, keyboard events, mouse events, or file transfer across the Android ↔ Windows boundary. Mark those physical tests NOT RUN until tested on the actual devices.

## Installation and controlled physical-test procedure

### 1. Prepare Windows 10 Pro (test machine only)
1. Install the supported .NET 8 runtime if it is not already present.
2. Download the Windows Companion CI artifact from the exact successful Actions run and verify its SHA-256 locally.
3. Do not treat the CI publish folder as an installer. Run the Companion only in a controlled test session after reviewing its configuration and service/listener requirements.
4. Create a dedicated standard Windows test account; do not use a daily administrator account for remote sessions.
5. Keep Remote Desktop disabled for LAN exposure. If the Companion needs the Windows RDP service, bind/allow it only on loopback and confirm from a second LAN device that TCP 3389 is not reachable.
6. Configure the Companion bind address to a reviewed private LAN/VPN interface, a unique device GUID, and a server certificate PFX. Store the PFX password using a protected local secret mechanism; never pass it on a command line, commit it, or attach it to CI.
7. Export only the public CA certificate. Record and compare its SHA-256 fingerprint through a separate trusted channel.
8. Apply a narrowly scoped Windows Firewall rule for the Companion port on the private profile only. Do not create router port forwarding or public firewall rules.

### 2. Prepare Samsung Galaxy Tab S7 FE
1. Record Android version/build, app SHA, screen density and device model.
2. Install only a test build produced from the reviewed PR branch using a controlled local developer workflow; this phase must not publish an APK artifact.
3. Generate the device key and CSR inside the app. Confirm the private key remains in Android Keystore.
4. Compare the CSR/device identity on the Windows console and approve it explicitly. Install the signed certificate chain and verify the CA/server fingerprint before creating a session.
5. Start with identity-only permission. Grant remote display/input or file-transfer capability only as a separate explicit administrator approval for the corresponding test.

### 3. Execute and record
1. Follow sections A–E above in order. Do not proceed to RDP/input/transfer until certificate, identity and authorization checks pass.
2. For screen/input, use a non-production Windows desktop and verify each action at the host, not merely that Android callbacks fired.
3. For transfer, use disposable test files and compare SHA-256 at source and destination; attempt traversal and symlink attacks.
4. Disconnect manually, revoke the certificate, stop the Companion and interrupt the network in separate runs. Confirm no session, token, socket, pressed key or temporary file remains usable.
5. Capture actual tablet screenshots for section F and store them privately until identifiers and host information are redacted.
6. Record every case PASS/FAIL/NOT RUN with timestamp, exact Android SHA, exact Windows Companion SHA, Windows build, Android build, and sanitized logs.

### 4. Rollback
- Stop the Companion and remove its narrowly scoped firewall rule.
- Revoke the test client certificate and remove the test device authorization.
- Delete the test PFX/private key and temporary transfer directory using the approved local secure-storage process.
- Confirm RDP is not reachable from the LAN and no router port forwarding was introduced.
- Keep both Android feature gates false after the test unless the entire acceptance gate is reviewed and formally approved.

## CI artifact retention policy

- Android unit-test XML and HTML reports: 14 days; reports only, no APK.
- Windows Companion CI publish artifact: 14 days; not a production installer.
- FreeRDP native build outputs: no upload by default; the run logs retain the build verification. Add a native-library artifact only if a reproducible device-test need is approved.
- Keep source commit SHAs and Actions run URLs in the validation record so the evidence can be traced after artifact expiry.
