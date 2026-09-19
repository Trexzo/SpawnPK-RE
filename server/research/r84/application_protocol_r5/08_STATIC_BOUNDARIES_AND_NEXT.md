# R5 boundaries and next lanes

## Newly implementation-grade client-facing protocols
- generic Item List/Search/Transfer UI, including menu action overrides
- Event Task presentation and rich-row update mechanics
- Active Events/Hotspot timers and labels
- dynamic scene-object override add/remove/clear-by-object-id
- tile polygon/highlight add/remove
- Hunger Games lobby/live-match presentation state
- generic server selection list
- external login/logout receiver start/stop behavior

## Still server-owned
Item ownership/transfer validation; actual event tasks and rewards; event scheduling; hotspot choice/vote result; world-object business rules; HG matchmaking, combat, winner/rewards; selection-list consequences; purpose/authentication of the port-2456 service.

## Highest-value remaining S2C250 lanes
1. subtype33 screen/status panel controller (large operation language)
2. subtype8 combat metric overlay
3. subtype19 timed-effect state vs the already recovered 64-entry catalog
4. subtype4 token-roll reward UI
5. subtype7 boss-bar overlay
6. finish remaining simple overlay/state subtypes so all 43 have operation-level authority
