# Chat 2 G19.4.9/50 — canonical NPC attack hitmask obeys source batch commit

**Exact parent:** [G1947/48 #2812](https://github.com/Trexzo/SpawnPK-RE/pull/2812) commit `4cf1785bce24a69ec818102d01a78bc40bc62f4d` (669 focused candidate). The production change in that parent redirects canonical NPC hitmarks from session-only `sendMaskLocal` through the established `NpcRegistry.sendMask` semantic multiplayer relay. This branch adds **tests only**; no further production changes.

## Distinct ownership risk

The server may stage NPC hitmark packet65 in a packet writer batch. A hit from `LocalCanonicalNpcAttackHandler` still mutates World-owned HP and updates cadence on acceptance, but the outbound bytes are not committed until `LocalSession.endWorldTickBatch`. If the source packet batch fails capacity admission, broadcasting a staged hitmark to other viewers would be a phantom presentation. `SharedNpcWorldRelay.beginSourceMaskBatch/abortSourceMaskBatch/commitSourceMaskBatch` provide a source-owned semantic publication boundary, but prior general mask tests did not drive the **actual accepted canonical attack** through it.

## Test-only executable controls

**G1949:** World registers attacker/spectator, each with queue-backed ServerPacketWriter and independent active scene projection mapped to same WorldNpc. Decode real current-client ISAAC opcode72. Begin source relay batch plus writer byte batch, land one 10HP canonical hit, and assert HP=20/cadence committed but spectator has no pending mask before source bytes commit. Reserve the source queue's 1024-byte capacity and force batch admission failure. Abort source mask batch and assert source queue and spectator event queue empty; committed World NPC HP remains 20 (no fictitious damage rollback). No fabricated rewards.

**G1950:** same fixture with a successful source packet batch. Before `endWorldTickBatch` the remote event must remain absent; after byte commit it must still remain absent until `SharedNpcWorldRelay.commitSourceMaskBatch` publishes precisely one canonical NPC semantic mask. Spectator flush consumes and publishes that remote hit exactly once.

Manifest **669→671 unique**; two JavaExec tasks. Independent hosted exact-head **671/671 focused**, Java11 major55, G1949/G1950 marker checks and full Gradle build are required. Remains a draft; `main` unchanged.

## Limitations

This does not roll back World damage if the attacker outbound batch fails; it prevents only falsely broadcasting an *uncommitted presentation*. It does not invent delivery acknowledgments, observer replay, cross-process durability, original SpawnPK multiplayer behavior or kill-credit rules. The spectator barrier and generation ownership remain delegated to the existing World NPC semantic relay.
