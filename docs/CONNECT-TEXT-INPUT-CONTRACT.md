# INFINITY CONNECT — Text Input Contract

This pack defines engine-neutral Unicode text payload validation and chunking for remote text entry.

- Input is bounded to 16,384 UTF-16 code units.
- NUL is rejected; normal whitespace, newlines, and Unicode are preserved.
- Chunk boundaries are code-point safe (surrogate pairs remain intact); default chunk size is 256 code points.
- Empty input produces no chunks.
- The rules do not transmit data or prove that an RDP engine supports Unicode injection.
- A native adapter must implement the appropriate RDP Unicode/text-input method, handle clipboard policy separately, avoid logging text, and cancel pending input on session teardown.

This is not a live text-entry implementation until wired to and tested with a real RDP transport.
