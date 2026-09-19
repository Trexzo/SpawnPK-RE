# Minigame / event contracts newly recovered in the current client

## Hunger Games (`rs.l.e.a.o`)

Exact embedded presentation contract includes:

- lobby requires **5–15 players** for a match to start;
- Safety Countdown state;
- Ceasefire stage;
- explicit chest locked/unlocked state;
- explicit combat-disabled state;
- next-event timer;
- next-power-up timer;
- fog warning: go to the Safe Zone south of the map;
- wins / matches / points and match-style presentation fields.

These strings and state slots are current-client authority. Match selection, spawn contents, winner logic and actual timers remain server/game authority.

## Event Activity Viewer (`rs.n.c.J`)

The client has a reusable per-activity token-limit contract: timed earning limits, a lock state when the limit is reached, and reset when the timer reaches 0:00. Specific limits/durations are not embedded as a universal table.

## Monster Spawner (`rs.n.c.T`)

The interface states that one activation provides **5 spawns**, then requires reactivation. It exposes `Spawn x3`, `Spawn distanced`, NPC selection and a master spawner toggle. Exact NPC eligibility/spawn positions remain server authority.

## PvP Hotspot (`rs.n.c.S`, `rs.n.c.R`)

Static client text documents wild-casket rewards from PKs and says Blood orbs earn better caskets. There is also a vote-to-skip surface where enough player votes automatically changes the hotspot. Active hotspot identity, threshold and reward probabilities are not statically embedded here.
