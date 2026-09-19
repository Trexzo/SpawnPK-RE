# SpawnPK Client Application Protocol Audit R4 — 2026-09-18

This is an additive exact-current-client audit built after R3.1. It does **not** modify LocalLab.

R8.2 already proved that S2C250 is a 43-subtype VAR_BYTE application multiplexer and recovered one flattened read grammar per subtype. R4 goes one level deeper: it splits high-value handlers into their internal operation IDs and records the operation-specific payload grammar and concrete client effect.

Deep-decoded in this package: Shop Tabs (17), Chapter Rewards (22), Confirmation Dialog (28), Tradepost Listings (30), Mail UI (31), Custom Magic (32), Make-X (35), Dynamic Widget Actions (37), Client Int Map (39), Selection Dialog (40), Raid UI (41), NPC Runtime Overrides (42), and Client Integer Flags (43).

No production economy, rewards, raid rules, mail backend, market matching, or RNG is inferred.
