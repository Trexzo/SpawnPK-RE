# SpawnPK Client Discovery Audit R2 — 2026-09-18

This is an **additive second-pass audit** over the exact current SpawnPK client already pinned by LocalLab research.

Exact `client.jar` SHA-256:
`6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`

Goal: not to revisit the known R1 gaps, but to inventory **other client subsystems that contain recoverable static authority or useful server/UI contracts**.

No LocalLab binary or R8.1/R8.2 installer was modified.

Start with:
- `14_STATIC_VS_SERVER_BOUNDARY.md`
- `10_UI_FEATURE_ATLAS.csv`
- `01_TIMED_POPUP_EFFECT_CATALOG.csv`
- `02_STANDARD_SPELL_FILTER_CATALOG.csv`
- `03_CONSTRUCTION_ROOM_CATALOG.csv`
- `05_ACHIEVEMENT_CHAPTER_BOOTSTRAP.csv`
- `06_TIMED_TASK_RULES.csv`
- `12_ITEM_LIBRARY_CLIENT_CONTRACT.md`

This audit proves there is still meaningful recoverable content in the exact client even though R1 correctly closed the original equipment/projectile/world-authority questions.

Additional closure after the initial R2 package build: `17_CLIENT_CONTROL_TOKEN_CENSUS.txt` and `18_CONTROL_TOKEN_BEHAVIOR_MAP.csv` document the current string-control dispatcher and exact UI/state handlers.
