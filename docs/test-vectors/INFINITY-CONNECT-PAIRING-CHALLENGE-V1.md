# Pairing Challenge v1 — shared cross-platform test vector

This fixed transcript vector is normative for Android/Kotlin and Windows/.NET.
It validates UUID network byte order, domain separation, integer endianness, and byte concatenation.
It is not a live credential, certificate, signature, or production pairing payload.

| Field | Value |
|---|---|
| protocolVersion | 1 |
| requestId | `abcdefab-cdef-abcd-efab-cdefabcdefab` |
| companionDeviceId | `aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee` |
| issuedAtUnixMs | `1700000000123` |
| expiresAtUnixMs | `1700000300123` |
| nonce | bytes `00` through `1f` |
| caSha256 | bytes `a0` through `bf` |

Expected 152-byte signature input (hex):

```text
494e46494e4954592d434f4e4e4543542d50414952494e472d4348414c4c454e47452d5631000001abcdefabcdefabcdefabcdefabcdefabaaaaaaaabbbbccccddddeeeeeeeeeeee0000018bcfe5687b0000018bcfe9fc5b000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf
```

Both platform test suites assert byte-for-byte equality with this value.
