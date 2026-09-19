# v4 bank + equipment + NPC protocol closure

Authority: exact `client(6).jar` SHA-256 `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662` plus localhost runtime evidence.

## Bank

- bank root `5292`, bank container `5382` (8x44 = 352 slots)
- bank inventory overlay root `5063`, item widget `5064` (4x7 = 28 slots)
- placeholder toggle widget `39971`
- amount prompt: S2C 27 fixed0
- amount submit: C2S 208 fixed4 BE32
- container drag: C2S 214 fixed7, exact current-client transforms
- custom tab commands: opcode103 `setbanktab <slot> <tab>` / `swapbanktab ...`

v4 uses fixed arrays. Empty packet-53 slots are encoded as item id -1 -> wire item 0. Non-stackable local fixture items are materialized in distinct inventory slots.

## Equipment

- current SpawnPK item 28526 = Scythe of bloodrend, action `Wield`
- Wield/Equip menu action serializes as C2S opcode41 fixed6
- player appearance weapon slot = 3
- item appearance form = 512 + item id; Bloodrend = 29038
- equipment refresh is packet81 local-player mask 0x10 without movement

## NPC foundation

Packet65 is var-short. Minimal v4 new-NPC form:
- existing count: 8 bits = 0
- npc index: 14 bits
- signed dy: 5 bits
- signed dx: 5 bits
- optional extension flags: 0/0/0
- aH flag: 0
- definition: 14 bits
- sync-mask flag: 0

For one entry, the pinned decoder terminates naturally at the 7-byte payload boundary; no 16383 sentinel is appended.

Current config NPC 1799 is Blood Fountain, model 32999. v4 diagnostic placement is dx=+3,dy=+3 from the localhost player, i.e. world 3090,3498 for the current bootstrap. This coordinate is diagnostic only.
