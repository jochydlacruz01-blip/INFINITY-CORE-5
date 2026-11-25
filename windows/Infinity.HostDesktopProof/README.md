# INFINITY HOST AGENT — Desktop Proof

**Purpose:** isolated, local-only feasibility prototype for interactive Windows desktop capture and input.

## Scope and safety

- This project is separate from `Infinity.WindowsCompanion` and the Android application.
- It opens no socket, starts no server, creates no tunnel, and does not use RDP.
- It requires an explicit local consent checkbox before capture or input.
- It does not elevate privileges or attempt to bypass UAC, the secure desktop, the lock screen, or Windows access controls.
- Input is limited to explicit local test buttons in the visible proof UI.
- Stop the capture before closing the application. Closing also stops its timer and releases the preview bitmap.

## First proof implementation

- Screen capture: `Graphics.CopyFromScreen` (GDI) at 2 frames/second.
- Mouse: `SendInput` with absolute virtual-desktop coordinates.
- Keyboard: `SendInput` Unicode text events.
- Multi-monitor: captures `SystemInformation.VirtualScreen`, including its virtual-desktop origin.
- The preview is local and intentionally not streamed to Android or another machine.

This is a **local capability probe**, not the final Host Agent. GDI capture is used as a low-dependency baseline. A subsequent comparison must evaluate Windows Graphics Capture and Desktop Duplication for performance, protected content, multi-monitor behavior, and lifecycle. The current prototype does not claim UAC secure-desktop, lock-screen, elevated-window, or unattended-session support.

## Build and run on a Windows development PC

Install the .NET 8 SDK, then from the repository root:

```powershell
dotnet build .\windows\Infinity.HostDesktopProof\Infinity.HostDesktopProof.csproj -c Release
dotnet run --project .\windows\Infinity.HostDesktopProof\Infinity.HostDesktopProof.csproj
```

Run as a standard user, not elevated. Keep a harmless test application open (for example, Notepad). Start capture, enter coordinates on the virtual desktop, and use **Enviar clic**. To test text input, first focus a harmless text field in another window, then return to the proof UI and press **Enviar texto**; Unicode input is delivered to whichever control currently has focus.

## Acceptance checklist

- [ ] Release build succeeds on Windows 10 Home.
- [ ] Release build succeeds on Windows 10 Pro.
- [ ] Release build succeeds on Windows 11 Home.
- [ ] Release build succeeds on Windows 11 Pro.
- [ ] Preview updates at the expected cadence and reports real virtual-screen dimensions.
- [ ] Click reaches a harmless test target at the requested virtual-screen coordinate.
- [ ] Unicode text reaches the currently focused harmless test field.
- [ ] Multi-monitor coordinates work with monitors positioned left/above the primary display.
- [ ] Stop halts frame updates and disposes the prior preview image.
- [ ] UAC secure desktop, lock screen, and elevated-window limitations are observed and documented.
- [ ] No network listener is opened and no traffic leaves the machine.

## Known limitations / next engineering step

This proof deliberately does not establish remote control over LTE/Internet. It validates only local Windows capture/input primitives. It is not yet suitable for production: there is no frame encoding, transport, Android client, session protocol, authorization broker, privacy overlay, rate limiting, or telemetry. Before transport work, replace or benchmark GDI capture against Windows Graphics Capture and add automated tests for coordinate normalization and input event construction.
