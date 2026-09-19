# Raid UI operation protocol — subtype 41

R4 resolves the internal raid operations rather than treating subtype41 as one flattened blob.

High-value exact behavior:
- operation2 action state: Ready / Cancel / Enter Raid / Start Mass / Waiting for leader
- operation3 party row: member index + name + status; `invit` => Cancel invitation, otherwise Kick player; `max` disables action
- operation6 stage 0..5 rendered as colored `stage/5` affliction/progress bar
- operation18 RaidPartyOverlay timer lifecycle
- operation19 updates `Your points:`
- operation20 updates `Time:` using m:ss
- operation22 rewrites difficulty labels `Adept (Req. 10+ X)`, `Expert (Req. 50+ X)`, `Master (Req. 100+ X)` using either default noun `raids` or a supplied noun

This recovers raid **presentation/state transport**, not encounter mechanics, eligibility, loot or party authority.
