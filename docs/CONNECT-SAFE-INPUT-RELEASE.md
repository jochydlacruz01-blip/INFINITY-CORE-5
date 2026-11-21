# CONNECT — Safe input release

The engine-neutral tracker records key/button down and up state. On disconnect, viewport cancellation or adapter failure, call releaseAll() and dispatch the returned release events to the engine before disposing input/session resources.

- Release events only cover inputs recorded as held.
- Releases are emitted in reverse order and are idempotent.
- This is a contract/helper; it is not wired to a live RDP engine yet.
- Adapter integration must ensure best-effort release even when transport is failing.
