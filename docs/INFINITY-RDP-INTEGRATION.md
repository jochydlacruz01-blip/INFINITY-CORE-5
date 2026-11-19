# INFINITY CONNECT — RDP integration plan

## Current state

INFINITY CONNECT currently stores and edits connection profiles only. It does not establish an RDP session. Keep that status visible in the UI until a real session has passed device testing.

## Candidate engine

Evaluate the official [FreeRDP repository](https://github.com/FreeRDP/FreeRDP) and its Android client implementation before selecting a dependency. FreeRDP is published under Apache-2.0. The upstream Android port consists of an Android Java UI, the FreeRDP native libraries/dependencies, and JNI bindings.

Primary upstream references:
- [Android build guide](https://github.com/FreeRDP/FreeRDP/blob/master/docs/README.android)
- [Android Studio project](https://github.com/FreeRDP/FreeRDP/tree/master/client/Android/Studio)
- [FreeRDP license](https://github.com/FreeRDP/FreeRDP/blob/master/LICENSE)
- [Security policy and advisories](https://github.com/FreeRDP/FreeRDP/security)

## Compatibility risks to resolve first

The current INFINITY app uses AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, compile/target SDK 35, and min SDK 26. The current upstream Android Studio project has moved to a newer toolchain (including AGP 9.x, NDK 29, and newer Android SDK defaults). Do not upgrade the main app toolchain merely to make the first RDP experiment compile.

The upstream Android build uses CMake and the Android NDK, compiles native dependencies, and packages JNI/native libraries. This is a substantial native integration, not a simple Kotlin dependency. Confirm the exact upstream release/tag, supported ABIs, minimum Android API, and dependency licenses before importing anything.

## Safe implementation sequence

1. Pin a stable upstream FreeRDP release/tag and review its release notes, security advisories, license, and Android source tree.
2. Create an isolated integration branch/module and prove a reproducible arm64-v8a build in GitHub Actions. Keep the existing APK workflow and app module unchanged during this spike.
3. Establish a minimal JNI bridge and lifecycle-safe session controller. Do not copy the upstream demo UI wholesale into INFINITY.
4. Add a dedicated Compose RDP viewer surface with touch pointer, keyboard input, scaling, rotation, reconnect, disconnect, and clear connection-state reporting.
5. Handle certificate trust explicitly. Never silently accept unknown certificates.
6. Request credentials only when connecting; keep passwords in memory only, never in profile JSON, logs, clipboard, or persistent preferences.
7. Add network failure handling, timeouts, cancellation, and deterministic native session cleanup on disconnect/background/Activity recreation.
8. Test against Windows Remote Desktop and at least one compatible non-Windows server, on physical Android devices and supported ABIs.
9. Only after all gates pass, merge the isolated module into the main application and expose a real Connect action.

## Release gates

- Existing INFINITY Debug and signed Release builds still pass.
- RDP native build is reproducible from a pinned upstream revision.
- No secrets or passwords are persisted or logged.
- Certificate warnings and trust decisions are visible and user-controlled.
- Connect, authentication failure, disconnect, app backgrounding, rotation, and reconnect are tested on physical devices.
- The UI never labels a saved profile as connected unless the native transport confirms an active session.

## Explicit non-goals for the first spike

- No automatic exposure of TCP 3389 to the public Internet.
- No silent certificate acceptance.
- No password storage in SharedPreferences.
- No claims of RDP support until a real session is established and tested.
