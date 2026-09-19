# v5.1 equipment pose + slot architecture

## Why v5.1 exists

v5 correctly generalized the item catalog/inventory path, but its equipment boundary was too permissive and its scythe pose was implemented as a one-field packet-serialization special case.

Two concrete v5 defects were found during the static double-back:

1. any item exposing `Wield` was treated as weapon appearance slot 3;
2. scythe handling changed only stand animation 808 -> 8057 inside `BootstrapPackets` rather than resolving a reusable seven-field equipment pose.

Current SpawnPK custom metadata proves why #1 is unsafe: item 21026 (`Infernal max cape`) exposes `Wield`, but that is not evidence that it belongs in weapon appearance slot 3.

## v5.1 layers

### ItemDefinitionRepository

Still owns broad item existence/name/stackability/action/clone data. It does **not** infer equipment slot from an action string.

### EquipmentMetadataRepository

Owns authoritative LocalLab equipment metadata:

- item/family -> appearance slot;
- two-handed flag;
- equip action;
- reusable pose profile;
- evidence string.

Unknown slot => equip is rejected. No guessed slot is written to packet 81.

### EquipmentState

Owns all 12 appearance slots generically. Bloodrend remains only the initial localhost regression fixture.

### EquipmentPoseProfile / EquipmentPoseRepository

Packet 81 always receives one seven-field profile in exact client order:

1. stand
2. stand-turn
3. walk
4. turn 180
5. turn 90 clockwise
6. turn 90 counter-clockwise
7. run

`SCYTHE_VITUR_FAMILY` currently serializes:

`8057, 823, 819, 820, 821, 822, 824`

Only field 1 (stand=8057) is marked weapon-specific recovered evidence (`weaponSpecificMask=0x1`). The other six are explicit baseline fallbacks, not falsely labeled as recovered Bloodrend values. When a real SpawnPK Bloodrend appearance vector is captured, all seven can be changed in the profile without touching packet serialization or inventory code.

## Exact pinned-client checks

The client appearance parser consumes all seven fields independently. The client also contains the verified guarded correction:

- if incoming stand is 808;
- and equipped weapon is 4151 / 25000 / 20523 / 20689;
- and the relevant client mode guard is false;
- then parsed stand becomes 11973.

v5.1 parity test proves this directly against `client(6).jar`: whip wire stand remains 808 and the client parses it as 11973.

## Coverage boundary

The item lifecycle is broad (28,673 catalog IDs). Equipment-slot metadata is intentionally conservative and not yet 28,673-item complete. v5.1 fixes correctness of the architecture; it does not manufacture unknown equipment slots or claim the remaining six Bloodrend-specific movement IDs have been recovered.
