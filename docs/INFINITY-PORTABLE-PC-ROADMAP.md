# INFINITY Portable PC — foundation pack

## Product goal
Turn the Android tablet into a desktop-style work surface and optionally access a Windows 10 Pro host as a distinct remote session. Local Android apps/files remain separate from remote PC resources.

## Security baseline
- Remote access must not expose RDP (3389) directly to the public internet.
- Prefer private overlay VPN (e.g. Tailscale) for devices on different networks.
- Validate host/port inputs; keep secrets out of logs, analytics, and plain preferences.
- Use encrypted credential storage; support explicit disconnect and session cleanup.
- Remote controls should be enabled only after a real authenticated session is confirmed.
- Never represent preflight checks as successful transport/authentication or as an active RDP session.

## Tablet desktop experience
Touch gestures, mouse, keyboard, external display/landscape layouts, taskbar/Dock and window management are planned as the INFINITY native shell. Windows remote session is a separate workspace/view with its own connection state and clear exit control.

## Current pack status
This pack adds pure policy/checklist and lifecycle rules plus unit tests. It does NOT implement RDP protocol, VPN provisioning, Windows host setup, credential vault wiring, or UI integration. Release readiness intentionally remains false until transport, secure storage and real session checks are implemented.

## Release track
1. Shell layout/adaptive tablet usability.
2. Secure connection onboarding and private network setup guidance.
3. Choose and review a licensed/maintained RDP transport implementation.
4. Integrate transport, keyboard/mouse/touch mapping and disconnect/reconnect lifecycle.
5. Security review, device tests and CI; then packaging/signing and Play Store compliance.
