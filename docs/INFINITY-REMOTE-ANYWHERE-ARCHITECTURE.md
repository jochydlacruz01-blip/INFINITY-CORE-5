# INFINITY REMOTE — Architecture comparison and recommendation

**Status:** Design decision document only. No transport implementation authorized or performed.
**Target branch:** `INFINITY-REMOTE-ANYWHERE-ACCESS`
**Design baseline:** `ab77f9e4550e534bcd7c4305faadb07e1ef61c98`
**Repository:** `jochydlacruz01-blip/INFINITY-CORE-5`
**Date:** 2026-11-25

## 1. Executive recommendation

Adopt a **phased hybrid strategy**:

1. **First deliver a VPN-based, cross-network MVP**, with Tailscale as the recommended managed overlay for the initial acceptance path. Keep WireGuard as a lower-level/self-managed alternative for advanced deployments.
2. Keep the Android client and Windows host independent of the network provider through a transport interface. The remote session protocol must not depend on Tailscale-specific APIs.
3. After the MVP is physically validated, evaluate an **INFINITY-owned coordination and relay service**. Build it only if product requirements justify operating global infrastructure, support, monitoring, abuse prevention and bandwidth costs.
4. Do not implement either transport in this change. This document records architecture, trade-offs, constraints and acceptance gates only.

This approach minimizes the first delivery's operational risk while preserving a credible route to a fully branded INFINITY service.

## 2. Existing repository baseline (verified source documentation)

The current repository already documents these source-level components:

- Android identity key/CSR and client certificate installation.
- TLS 1.3 mutual certificate authentication and Windows certificate pinning.
- Windows Companion with local-console CA/CSR issuance, explicit client approval and revocation.
- Capability-bound, short-lived sessions with default-deny policy.
- An Android WebSocket tunnel and a Windows RDP tunnel whose upstream is fixed to `127.0.0.1:3389`.
- Managed file transfer with per-device ownership, size/hash checks and a restricted storage root.
- Feature gates `REMOTE_DESKTOP_VALIDATED` and `FILE_TRANSFER_VALIDATED` remain false.

These are source-level capabilities, not proof of physical interoperability or production readiness. The current host setup explicitly supports Windows 10/11 Pro, Enterprise or Education; it states that Windows Home cannot host Microsoft's built-in RDP sessions. Cross-network operation currently expects a separately configured private VPN. No automatic public relay or INFINITY-operated gateway is documented as implemented.

Relevant existing documents:
- `docs/INFINITY-CONNECT-PROTOCOL.md`
- `docs/CONNECT-WINDOWS-HOST-SETUP.md`
- `docs/INFINITY-CONNECT-SECURITY-PREFLIGHT.md`
- `windows/README-INFINITY-CONNECT.md`

## 3. Architecture A — Private VPN / overlay network

### 3.1 Shape

```text
INFINITY Android client
        |
        | authenticated private overlay address
        v
Private VPN / overlay
  | direct encrypted peer path when possible
  | provider relay fallback when direct NAT traversal fails
        |
        v
INFINITY Windows Host
        |
        +-- authenticated session broker
        +-- remote display/input provider
        +-- managed file-transfer endpoint
```

Both devices join the same private overlay. The Android client addresses the PC using its private overlay identity/address. The existing Companion mTLS and capability authorization remain an application-level security layer; VPN membership does not itself grant remote-control permission.

### 3.2 Tailscale as the recommended managed MVP

Tailscale is the recommended first acceptance path because it provides device coordination, NAT traversal and DERP relay fallback as a managed overlay. The user installs/enrolls the supported Tailscale client on both Windows and Android devices and authorizes them into the same tailnet. INFINITY then connects to the PC's private tailnet address or stable device name.

Advantages:
- Shorter path to testing tablet LTE ↔ PC on another ISP.
- No requirement for the user to open public RDP or Companion ports.
- Direct peer path where network conditions permit; relay fallback when direct connectivity cannot be established.
- Device access policy and revocation can be administered separately from INFINITY's own per-session capability consent.
- Avoids operating a global relay fleet in the first product milestone.

Trade-offs:
- Requires an external provider account/client and its availability.
- User experience is not fully self-contained; VPN installation, sign-in and device authorization are external setup steps.
- Provider plan, policy, service availability, data path and privacy terms must be reviewed before commercial commitments.
- INFINITY must detect and explain VPN-not-installed, device-not-authorized, offline and relay/degraded-path states. Never label a device connected based only on a saved VPN address.

### 3.3 WireGuard self-managed alternative

A self-managed WireGuard deployment can provide a private encrypted network, but a practical roaming design normally needs a reachable public endpoint (for example, a maintained VPS) or another reachable peer to act as the hub. Both devices initiate outbound tunnels to that endpoint; this avoids router port forwarding on the user's Windows PC but does not eliminate the need to operate a public VPN endpoint.

Advantages:
- Open protocol and greater control over keys, addressing and infrastructure.
- No dependency on a managed overlay provider once infrastructure is operated.
- Can be appropriate for advanced users, organizations or private deployments.

Trade-offs:
- INFINITY or the operator must provision, patch, monitor and pay for the endpoint.
- Key distribution, peer lifecycle, routing, DNS, endpoint changes, abuse controls and support become product responsibilities.
- WireGuard alone is not a complete managed NAT-traversal/relay coordination product. A public hub may become a bandwidth bottleneck and single point of failure.
- More complex setup and recovery for non-technical users.

## 4. Architecture B — INFINITY-owned coordination and relay gateway

### 4.1 Shape

```text
INFINITY Android client                 INFINITY Windows Host
        |                                         |
        +------ outbound authenticated control --+
                          |
                          v
              INFINITY Coordination Service
              - account/device registry
              - short-lived rendezvous tickets
              - online presence / signaling
              - policy and abuse controls
                          |
             direct encrypted path if possible
                          |
             otherwise encrypted relay path
                          |
                          v
                INFINITY Relay Gateway
             - bounded sessions and bandwidth
             - rate limits / quotas
             - regional routing / health
             - no plaintext session access
```

The coordination plane authenticates accounts/devices and issues short-lived, one-time rendezvous tickets. It must not itself authorize desktop or file capabilities. The existing Companion mTLS identity and explicit capability consent remain mandatory.

The data plane attempts a direct encrypted connection where technically possible. When NAT/firewall conditions prevent it, both peers establish outbound connections to a relay. The relay forwards opaque encrypted frames/packets and must not receive session keys. The relay must not terminate application end-to-end encryption.

### 4.2 Advantages

- Fully branded and integrated onboarding.
- No separate VPN client required by the user.
- INFINITY controls device lifecycle, pairing UX, service telemetry and product policy.
- Can support a consistent experience across Windows editions and future host platforms.
- Potential to provide regional relay selection and product-level service guarantees.

### 4.3 Costs and risks

- Requires production backend, database, device registry, identity service, key lifecycle and abuse prevention.
- Requires globally reachable relay infrastructure, bandwidth capacity planning, regional availability, DDoS mitigation, monitoring, alerting and incident response.
- Video/screen traffic can create substantial recurring bandwidth and egress costs.
- Must design quotas, paid/free tiers if applicable, retention controls, privacy disclosures and support processes.
- A gateway outage can prevent new sessions and relayed sessions; redundancy and graceful failure are required.
- Security review must cover account takeover, rendezvous abuse, replay, relay exhaustion, denial of service, metadata leakage and operational key compromise.

This is not a small extension to the current Windows Companion. It is a separately operated internet service and should not be treated as complete merely because a relay endpoint compiles.

## 5. Comparison

| Criterion | Managed VPN overlay (A1) | Self-managed WireGuard (A2) | INFINITY-owned gateway (B) |
|---|---|---|---|
| Cross-network access | Yes, subject to provider/network availability | Yes, with reachable endpoint and correct routing | Yes, with coordination and relay infrastructure |
| LTE ↔ home/office ISP | Supported use case | Supported if hub/peer reachable | Supported use case |
| Public RDP/3389 exposure | Not required | Not required | Not required |
| External setup | VPN client/account on both devices | VPN provisioning; usually server endpoint | INFINITY account/pairing; no separate VPN client |
| Direct path | Overlay attempts direct path | Depends on topology and NAT | Must be engineered |
| Relay fallback | Provider-managed relay where available | Must be operated/designed separately | INFINITY operates relay |
| Operational burden for INFINITY | Low to medium | Medium to high | Very high |
| Branding/control | Partial | High for deployment, medium UX | Highest |
| Recurring infrastructure cost | Provider plan / terms | VPS, traffic, operations | Coordination + relay fleet + egress + operations |
| Best initial use | MVP and physical proof | Advanced/self-hosted deployment | Later product-scale service |

## 6. Windows Home and Pro compatibility — critical design decision

The current RDP tunnel is not sufficient to meet the approved Windows Home + Pro requirement. Windows Home cannot act as the built-in Microsoft RDP session host. Therefore, the product must not rely on `127.0.0.1:3389` as the only remote-display/input implementation.

Recommended host architecture:

1. **Per-user interactive Host Agent:** runs in the logged-in desktop session and owns screen capture, input injection and session lifecycle. This is required to interact with the user's visible desktop; a Windows service running only in Session 0 must not be treated as a screen-capture agent.
2. **Optional privileged Windows Service/Broker:** handles installation/update, protected machine identity, narrowly scoped privileged operations and service health. It must not become a general remote shell.
3. **Edition-independent Remote Session Protocol:** carries authenticated display frames, input events, resize messages and capability requests independently of RDP.
4. **Optional RDP adapter:** may be offered on eligible Pro/Enterprise/Education configurations as a separately tested mode, but it is not the compatibility foundation and must never expose 3389 publicly.

The capture/input implementation must be evaluated against Windows Graphics Capture and/or Desktop Duplication APIs, input injection constraints, multi-monitor behavior, lock screen, UAC secure desktop, fast-user switching, sleep/resume and console-session ownership. These behaviors need explicit support boundaries; do not promise control of secure desktop/UAC prompts or disconnected sessions until physically proven.

### Windows 10 lifecycle qualification

As of this document date (2026-11-25), ordinary Windows 10 Home and Pro 22H2 support ended on 2025-10-14. Microsoft documents consumer ESU security updates through 2027-10-12 for eligible enrolled devices; LTSC releases follow separate lifecycle dates. Therefore:
- Keep Windows 10 Home/Pro in the compatibility test matrix.
- Mark standard non-ESU Windows 10 installations as unsupported/at-risk for secure production use.
- Require current applicable security updates (ESU or a supported LTSC lifecycle) for production acceptance.
- Prefer a supported Windows 11 release for the recommended setup.

Official references:
- https://learn.microsoft.com/en-us/lifecycle/announcements/windows-10-end-of-support
- https://support.microsoft.com/en-us/windows/deployment/updates-lifecycle/windows-10-support-has-ended-on-october-14-2025
- https://learn.microsoft.com/en-us/lifecycle/products/windows-11-home-and-pro

## 7. Proposed logical components

These are design boundaries, not a commitment to create all components in the same release.

### Android
- `RemoteConnectionOrchestrator`: connection state machine and retries.
- `RemoteTransport`: provider-neutral connect/send/receive/close contract.
- `OverlayVpnTransport`: optional VPN-address transport adapter.
- `GatewayTransport`: future INFINITY rendezvous/relay adapter.
- `RemoteTrustStore`: pinned host identity and revocation state.
- `RemoteSessionController`: consent, capabilities, expiry and disconnect.
- `RemoteDisplaySurface`: bounded frame decode/render and resolution negotiation.
- `RemoteInputController`: keyboard, pointer, touch and release-on-disconnect.
- `RemoteFileTransferController`: isolated from screen/input capability.

### Windows
- `Infinity.HostAgent`: interactive per-user session agent.
- `Infinity.HostBroker`: optional narrowly privileged service.
- `HostIdentityManager`: device identity and certificate lifecycle.
- `CaptureProvider`: display capture abstraction.
- `InputProvider`: validated input abstraction.
- `RemoteSessionServer`: capability-bound session endpoint.
- `TransferStore`: existing managed transfer boundary, not arbitrary filesystem access.
- `TransportAdapter`: VPN/private interface now; future gateway signaling/relay later.

### Cloud (future, only for Architecture B)
- `IdentityAndDeviceRegistry`
- `RendezvousCoordinator`
- `RelayAllocator`
- `RelayDataPlane`
- `AbuseAndRateLimitService`
- `OperationalTelemetry` with privacy minimization

## 8. Security and privacy invariants

These are mandatory for either architecture:

- Never expose public TCP 3389; do not add router port forwarding or UPnP.
- TLS 1.3 mTLS and explicit server certificate pinning remain mandatory for the application control channel.
- Pairing is explicit, local-user confirmed, time-limited and single-use.
- Unknown/changed/revoked host identity fails closed.
- VPN membership or gateway account authentication does not grant remote display, input or file-transfer capabilities.
- Every capability is independently requested, visibly consented to, bounded by policy and revocable.
- Screen/input/file channels use end-to-end authenticated encryption. A relay must not decrypt payloads.
- No general remote shell, arbitrary filesystem browsing, clipboard or unattended access by default.
- File transfer remains in the managed per-device store with quotas, hashes, path isolation and explicit user selection.
- Protect credentials and private keys with platform keystores/OS ACLs; never log secrets or screen contents.
- Provide visible active-session indicator, immediate disconnect and device revocation.
- Apply rate limits, bounded message/frame sizes, timeouts, concurrency limits and denial-of-service controls.
- Define data minimization, log retention, deletion and user-visible privacy policy before public release.

## 9. Network and failure behavior

The UI must expose truthful states:
`OFFLINE` → `DISCOVERING` → `PATH_NEGOTIATING` → `AUTHENTICATING` → `HOST_VERIFIED` → `AWAITING_CONSENT` → `SESSION_AUTHORIZED` → `CONNECTED_DIRECT` or `CONNECTED_RELAY`.

Failure states include `VPN_NOT_READY`, `HOST_OFFLINE`, `NETWORK_BLOCKED`, `RELAY_UNAVAILABLE`, `CERTIFICATE_REJECTED`, `CONSENT_DENIED`, `CAPABILITY_DENIED`, `SESSION_EXPIRED` and `REVOKED`.

Never display "Connected" for a saved profile, reachable TCP port, VPN membership or successful TLS handshake alone. Distinguish direct and relayed paths, and show when relay use may affect latency or data usage.

## 10. Acceptance plan

### Phase 0 — design (this document)
- Architecture comparison approved.
- No transport code or feature-gate changes.

### Phase 1 — host compatibility proof
- Build a Windows Home and Pro test matrix.
- Prototype and evaluate the interactive Host Agent capture/input boundary separately from existing RDP tunnel.
- Document secure desktop, UAC, lock/unlock, multi-monitor, DPI and session lifecycle support.
- No Android feature activation.

### Phase 2 — managed VPN MVP
- Android tablet on LTE; Windows PC on a different ISP/network.
- Tailscale overlay enrollment and direct/relay path detection.
- Verify Companion mTLS and pinned identity over the overlay.
- Verify default-deny capabilities and explicit session consent.
- Verify remote display/input on Windows Home and Pro through the edition-independent host path.
- Verify reconnect, network switching, sleep/resume and device revocation.
- Keep 3389 inaccessible from the public Internet.

### Phase 3 — transfer and security
- File transfer integrity (SHA-256), quotas, cancellation, interruption recovery and device isolation.
- Certificate rotation/revocation, replay, expired sessions, invalid identity and hostile-network tests.
- External security review before any production release.

### Phase 4 — evaluate INFINITY gateway
Proceed only if product evidence shows VPN setup friction or provider dependency is unacceptable and the project can fund/operate coordination and relay infrastructure. Reuse the provider-neutral transport and session protocol; do not fork remote-control semantics.

### Required release gates
- Real physical Android tablet + Windows PC tests; CI green alone is insufficient.
- Windows Home and Pro separately tested.
- Cross-network LTE ↔ separate ISP tested.
- Both direct and relay path tested where available.
- No public 3389 exposure.
- Security checklist passed and feature gates enabled only through a separately reviewed change.
- Signed, reproducible Windows installer and Android release build only after explicit release authorization.

## 11. Out of scope for this design-only change

- No VPN client integration or configuration changes.
- No Tailscale/WireGuard dependency added.
- No public gateway, relay, server, account service or cloud resources created.
- No new network listener or protocol endpoint.
- No changes to FreeRDP or its pinned submodule.
- No changes to Android or Windows application source.
- No changes to feature gates.
- No changes to workflows, Gradle or build configuration.
- No APK, Windows installer, commit to another branch or PR merge.

## 12. Decision

**Recommended decision:** Architecture A1 (managed VPN overlay, Tailscale) for the first cross-network MVP, with a provider-neutral transport/session boundary and a documented future path to Architecture B (INFINITY-owned coordination and relay). WireGuard self-managed remains an advanced deployment option, not the default consumer onboarding path.

This is a recommendation for approval, not implementation authorization. The next implementation request must identify a narrow phase, exact files, tests and security gates. The current Android feature gates remain false.
