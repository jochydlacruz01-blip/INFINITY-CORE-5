# Enrollment Proof v1 — shared cross-platform test vector

Normative transcript vector for Android/Kotlin and Windows/.NET. This tests byte order, domain separation, Android UUID encoding, and CSR digest binding. It is not a live credential or production pairing payload.

| Field | Value |
|---|---|
| Challenge signature input | The 152-byte vector in `INFINITY-CONNECT-PAIRING-CHALLENGE-V1.md` |
| Android device ID | `11111111-2222-3333-4444-555555555555` |
| CSR DER test bytes | `00` through `1f` |
| SHA-256(CSR DER) | `630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd` |

The exact signature input is:

1. ASCII `INFINITY-CONNECT-ENROLLMENT-PROOF-V1` plus NUL.
2. The complete deterministic Pairing Challenge v1 signature input.
3. Android device UUID as 16 RFC 4122/network-order bytes.
4. SHA-256 of the exact DER CSR bytes.

Expected transcript (237 bytes, lowercase hexadecimal):

```text
494e46494e4954592d434f4e4e4543542d454e524f4c4c4d454e542d50524f4f462d563100494e46494e4954592d50414952494e472d4348414c4c454e47452d5631000001abcdefabcdefabcdefabcdefabcdefabaaaaaaaabbbbccccddddeeeeeeeeeeee0000018bcfe5687b0000018bcfe9fc5b000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf11111111222233334444555555555555630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd
```

CSR proof-of-possession remains a separate PKCS#10 signature over CertificationRequestInfo using ECDSA P-256/SHA-256. Enrollment Proof signs the transcript above with that same Android Keystore identity key using ECDSA P-256/SHA-256. Windows must independently validate the CSR and verify that the proof-signature public key is the CSR public key.
