# Subtype 1 — Hunger Games / matchmaking presentation

The renderer confirms this handler feeds the Hunger Games UI.

## Outer mode 1 — lobby
Nested opcodes repeat until nested opcode 0:
- 1: `u16 players` -> `<img=233> Players in lobby: ...`
- 2: `string countdownText` -> `<img=37> Starting in: ...`
- 3: `u8 playersNeeded` -> `<img=73> Waiting for X+ players..`
- 4: no payload -> `Starting a match, please wait..`
- 5: `string statsText` -> lobby stats line (default `Wins: 0 | Matches: 0 | Points: 0`)

The client joins the countdown/waiting text and lobby-player count into its main lobby status line.

## Outer mode 2 — live match
Nested opcodes repeat until nested opcode 0:
- 1: `u8 survivors`
- 2: `u8 kills`
- 3: `u16 seconds` -> next-event deadline
- 4: `u16 seconds` -> next-power-up deadline
- 5: `u8 stage; u16 seconds` -> stage + deadline; renderer names stages 0/1 Safety Countdown / Ceasefire and gates chest/fight messaging
- 6: no payload -> disables live-match overlay
- 7: `string matchStyle` -> `Match style: ...`
- 8: `u8 fogWarningEnabled`

The renderer explicitly contains the 5-15-player requirement, safety/ceasefire text, next event/power-up labels, survivors/kills, match-style text, and fog warning. Matchmaking, winner selection, spawn rules and rewards remain server-owned.
