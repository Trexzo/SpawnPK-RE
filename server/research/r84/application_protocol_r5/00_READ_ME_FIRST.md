# SpawnPK Client Application Protocol Audit R5 — 2026-09-18

R5 is a cumulative continuation of R4 against the exact current SpawnPK client JAR.

Exact client SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`

R4 is preserved byte-for-byte under `R4_BASE/`. R5 adds per-operation closure for S2C250 subtypes 1, 2, 10, 12, 13, 14, 16, and 20, bringing the high-confidence per-operation application-protocol set from 13 to 21 of 43 subtypes.

Major new closures:
- 26/26 Item List/Search/Transfer operations.
- 11/11 Event Task UI operations.
- 7/7 Active Events/Hotspot operations.
- Dynamic scene object override add/remove/clear-by-object-ID.
- World tile polygon/highlight add/remove with exact RGBA fields.
- Hunger Games / matchmaking lobby and live-match presentation state.
- Server selection list reset/add/update protocol.
- Correction: subtype 20 is exact start/stop control for a client receiver socket on port 2456, not a match-result payload.

This is static client authority only. It does not infer server-owned rewards, matchmaking, event selection, object business rules, or gameplay outcomes.
