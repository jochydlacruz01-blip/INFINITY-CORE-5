# PACK 06 — INFINITY CONNECT: security preflight

## Included
- Before running a TCP reachability probe, CONNECT executes the existing local preflight for host, port, and Windows username.
- A blocked preflight prevents the network probe from starting.
- The panel displays each preflight result with severity-aware styling (information, warning, or blocker).
- The existing TCP probe remains a reachability check only. A successful TCP connection does not prove RDP negotiation, TLS identity, authentication, or an interactive desktop session.
- The UI continues to warn against exposing RDP/3389 directly to the public Internet and recommends VPN for remote access.

## Tests
Existing `InfinityConnectPreflightTest` covers valid endpoints, invalid host, invalid port, blank username, custom-port warning, and the network-security warning.

## Security boundary
This pack does not add a remote-desktop transport or claim end-to-end RDP support. A real session still requires a reviewed RDP client/transport and secure host setup.
