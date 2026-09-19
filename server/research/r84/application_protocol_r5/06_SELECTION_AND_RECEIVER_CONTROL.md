# Subtypes 16 and 20

## Subtype 16 — server selection list (root 40405)
- op0: reset 50 rows, cursor/count state and row sprites/text
- op1: `u8 indentationOrRowMode; u8 selectable; string label` -> append one row. Selectable rows get action `Select`; first argument changes row spacing/indent behavior.
- op2: `u16 rowIndex; string label` -> update one existing row text directly

## Subtype 20 — exact correction
R8/R4's higher-level label was too specific. Bytecode behavior is exact and simple:
- op0 -> stop receiver (`AtomicBoolean=false`)
- op1 -> start receiver (`AtomicBoolean=true`, spawn thread)

The receiver connects to configured host `rs.f.a.j` on TCP port 2456, reads UTF records for up to about 10 seconds, recognizes `Login`, `Logout`, `END`, and `Could...` messages, timestamps them, and forwards display work to Swing. Therefore R5 names this structurally as `EXTERNAL_LOGIN_LOG_RECEIVER_CONTROL`; no claim is made about the server-side purpose beyond those observed strings/behaviors.
