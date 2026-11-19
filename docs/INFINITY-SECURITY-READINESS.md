# INFINITY — Security and readiness review

Branch: `INFINITY-0.9`. This is a source-level review checklist, not a device certification.

## Verified from source and CI
- Notification feed text is bounded (title 300 chars, body 2,000 chars, app label 200 chars) and strips ISO control characters except line breaks and tabs.
- Notification privacy mode clears title/body values in the in-memory feed; notification content is not intentionally persisted by this feed.
- Feed capacity is capped at 100 items.
- Notification access is granted through Android's special settings; the app does not silently grant it.
- File access uses user-selected SAF document-tree grants rather than broad storage access.
- CONNECT profile fields include endpoint/name/user data; no Windows password field or RDP credential vault is implemented.
- CONNECT is profile management only. No live RDP session is implemented.
- CI #223 succeeded for Debug and signed Release build, available unit/static checks, release signature verification, and artifact publishing.

## Security / privacy cautions
- Notification access can expose sensitive information from other apps. Keep privacy hiding enabled by default and make the permission's purpose clear before sending the user to settings.
- Do not put notification contents, connection profile data, or credentials into diagnostic logs.
- Clipboard use for endpoint copying is user initiated; clipboard contents may be accessible to other apps depending on Android version and device behavior.
- SAF providers can have inconsistent capabilities; treat provider errors as recoverable and retain failed targets for retry.
- Storage, RAM, battery, and thermal figures are estimates or platform-reported values, not diagnostic guarantees.

## Remaining acceptance gates
- [ ] Test listener enable/revoke, reconnect, notification opening and dismissal on real devices.
- [ ] Confirm privacy-mode toggling clears already-visible feed content immediately in device UX.
- [ ] Test SAF operations with multiple providers and permission revocation.
- [ ] Review UI overflow, accessibility, touch targets and rotation on phone/tablet.
- [ ] Install/upgrade validation with the existing signed APK lineage.
- [ ] Complete RDP integration only after upstream dependency, license, native ABI, TLS and lifecycle review plus device tests.

