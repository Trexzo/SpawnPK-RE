# S2C250 application bus

Exact wire framing already established by the protocol master and re-confirmed against the exact current client:

`S2C250 = VAR_BYTE parent -> u16_be subtype -> subtype payload`

All 43 subtype handlers use only `u8`, `u16_be`, `i32_be`, `i64_be`, and newline-terminated strings. R4 preserves the complete 43-entry outer atlas in `tables/s2c250_application_index.csv` and adds operation-level decoding for selected high-value handlers.

This is important for LocalLab architecture: implement one `ScriptPacket250Writer` with typed subtype encoders rather than feature code manually assembling opcode 250.
