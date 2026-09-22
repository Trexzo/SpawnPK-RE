# Exact v308 region lifecycle runtime evidence — 2026-09-22

Source capture:

`SpawnPK-Recon-Snapshot-20260922-191621.zip`

Exact client SHA-256:

`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`

## Directly observed normal-region lifecycle

The runtime probe observed six inbound packet-73 region changes and six outbound
opcode-121 acknowledgements. All six followed the normal observed loading path;
none used the probe's duplicate/fast-path classification.

Observed lifecycle:

1. inbound packet 73;
2. new region center installed;
3. base derived as `center * 8 - 48`;
4. existing local coordinates rebased by the inverse base delta, preserving
   global/world coordinates;
5. loading stage changes from 2 to 1;
6. `rs.Client.bw()I` polls readiness;
7. `rs.Client.l()V` rebuilds the scene;
8. loading stage returns to 2;
9. client emits opcode 121;
10. subsequent readiness status reaches 0.

Packet 73 was four bytes in all six captures. Its exact byte transforms were not
persisted by this runtime probe, so this evidence does not independently certify
the existing static packet-73 serializer.

## Architectural consequence

Packet 73 is a region coordinate/base reconfiguration operation. It is not
itself equivalent to teleporting the player.

For ordinary sliding-window region changes, the packet-73 handler preserved the
same world X/Y by changing the client's local coordinates by the inverse base
delta. LocalLab therefore must not force a packet-81 player relocation merely
because the scene window rebased.

Large relocation captures showed the same immediate packet-73 world-position
preservation, followed by a later change to the actual destination before the
121 completion ACK. LocalLab may therefore keep a distinct player-placement
operation for explicit teleport/respawn/dev relocation paths.

## LocalLab implementation aligned to this evidence

Implemented on #431:

- `RegionLoadLifecycle` tracks one session-local packet-73 -> opcode-121 cycle.
- opcode 121 is a typed `RegionLoadAckClientRequest`, routed on the existing
  World-owned request path.
- initial login packet 73 opens a tracked bootstrap region-load cycle.
- ordinary automatic scene-window rebases emit packet 73 without packet 81.
- authoritative world X/Y remains unchanged across ordinary rebases.
- explicit dev-region teleport, return-home relocation, and respawn placement
  retain a distinct packet-81 placement operation after packet 73.
- automatic streaming is fenced while a previous packet 73 is awaiting its
  opcode-121 ACK.
- a wire-level regression proves ordinary automatic rebasing emits 219 -> 73
  with no extra packet 81 and preserves world coordinates.
- duplicate/unmatched 121 events are diagnostic-only and do not invent state.

## Intentionally unresolved

This evidence does **not** establish:

- the exact four-byte packet-73 wire transforms;
- the precise meanings of readiness values -1 and -3;
- constructed/instanced-region handling;
- whether every possible region-change path must emit 121;
- duplicate-region fast-path semantics;
- every NPC/entity/ground-item rebasing rule;
- the exact production server threshold/condition for sending packet 73.

LocalLab's current near-edge streaming threshold therefore remains a local
policy and must not be described as recovered production authority.
