# INFINITY CONNECT — Windows host preparation (LAN test only)

## Compatibility
The supported host target is Windows 10 or Windows 11 **Pro, Enterprise, or Education** with Microsoft Remote Desktop host capability. Windows Home can act as an RDP client but does not provide Microsoft's built-in RDP host. The preparation script rejects Home editions.

## What the helper does
Run tools/windows/Prepare-InfinityRdpHost.ps1 in an elevated Windows PowerShell 5.1 window on a trusted private LAN. It:
- checks Windows 10/11 client edition and rejects Home;
- requires an existing Private network profile and will not silently change network classification;
- enables built-in RDP and Network Level Authentication (NLA);
- enables only built-in Remote Desktop firewall rules on the Private profile and restricts remote addresses to LocalSubnet;
- does not create firewall rules, configure router forwarding, open a public profile, or expose TCP 3389 to the Internet.

Use -WhatIf first to preview. Run tools/windows/Disable-InfinityRdpHost.ps1 elevated to disable Remote Desktop and its built-in firewall rules.

## Important limitation
This is a host-preparation helper for controlled LAN testing. It is **not** the INFINITY Windows companion agent, relay, VPN, or Android RDP engine. The Android app currently has profile management and TCP diagnostics only; it cannot yet establish an authenticated RDP desktop session. A successful TCP probe is not a remote desktop connection.

For different networks, use a separately configured, trusted VPN that provides private reachability. Do not configure router port forwarding or publish TCP 3389. A future INFINITY relay/agent design must add authenticated pairing, encrypted transport, certificate validation, revocation, and end-to-end tests before release.

## Manual validation
1. Confirm Windows edition with winver and Settings > System > About.
2. Confirm the trusted LAN is Private in Windows Settings.
3. Run the preparation script elevated, initially with -WhatIf, then without it after review.
4. Confirm Settings > System > Remote Desktop is enabled and NLA remains required.
5. Test only from a trusted device on the same private LAN.
6. Run the disable script when finished if RDP is no longer needed.

Never share Windows passwords, private IPs, or exported credentials in issue reports or chat.
