# CONNECT Keyboard Input Contract

Engine-neutral keyboard event model.

- Tap emits key-down followed by key-up.
- Chords emit modifier-down in listed order, main key-down, main key-up, and modifier-up in reverse order.
- Duplicate modifiers and a main key duplicated as modifier are rejected.
- Keys are modeled as scan-code-oriented enums for later native engine translation.

This is a tested event contract, not a live keyboard transport. Text/Unicode input, IME composition, Android key dispatch, RDP scan-code translation and server-side behavior remain pending native engine implementation.
