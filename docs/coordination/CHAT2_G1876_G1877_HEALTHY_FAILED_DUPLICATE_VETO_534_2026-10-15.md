# Chat 2 — paired healthy and G14-failed terminal duplicate veto, 534 focused

Date: 2026-10-15.

## Exact source and CI hierarchy

**Certified cumulative parent:** [G21.215 + G18.7.5 + G18.7.6 draft #2682](https://github.com/Trexzo/SpawnPK-RE/pull/2682), exact SHA `1641b37c11f1df4ee3e66b6b1cc73708d586e79a`, hosted [Actions #38758161289](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38758161289) SUCCESS, **533 unique focused** with Java11 major55 and full Gradle. Parent includes the independently successful G18.7.6 all-healthy installed-observer flow and same-NPC `finalizeIfOwned` NOT_OWNED duplicate veto.

**Separate G18.7.7 test donor:** [draft #2683](https://github.com/Trexzo/SpawnPK-RE/pull/2683), exact commit `81ea3d79db60022102a989dd43ecdfaaf14c7a68`, [Actions #38759609199](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38759609199). **IN PROGRESS at composition**; this is NOT source certification. New G1877 Java Git blob `306b13fc595c3cebebcfbc99db591f5b306a721d` and note Git blob `a3cdc2d14d27ed9e5fbb5f194ebf25573b893db9` are copied byte-identically. If donor CI fails, repair donor and this combined candidate consistently; do not promote either unverified head.

## Exactly additive combination

Four files and four commits vs certified #2682: G1877 new Java class, exact G1877 original note, `server/build.gradle` ONE focused entry plus both fixed guard constants **533→534**, and this coordination file. The certified G1876 healthy test remains unchanged. ZERO production Java, account G21.215/G21.216/G21.217 RAW OS gate or session lease, prior G1872-75, client/default/main changes.

## Two different guarantees — never conflate

1. **G18.7.6 healthy path:** All G1, G4, G14, G15, G16, G18 activated with valid state. Two distinct genuine NPC deaths each settle once and credit eligible consumers; immediate duplicate `finalizeIfOwned` for either already-finalized NPC returns NOT_OWNED and credits nothing.
2. **G18.7.7 failed callback path:** A first genuine NPC settles coin/G1/G4, then a TEST-ONLY malformed G14 extension throws and skips G15/G16/G18. The old NPC entry is no longer owned. After A is replaced with clean B, `finalizeIfOwned(oldNpc)`/`retrySettlement(oldNpcId)` return NOT_OWNED, `retryFinalizationIfPending` returns null, `retryPendingSettlementsOnce()` returns0, and no historical consumer credit appears. Only a NEW second NPC with immutable B/generation origin credits B's valid G14/G15/G16/G18 once. Two distinct NPCs; NOT a compensation or replay of missed events.

Both paths are **CUSTOM_LOCALLAB** same-World ephemeral runtime semantics. They do NOT implement durable post-settlement consumer ACK, original SpawnPK reward/attacker attribution, a cross-JVM event outbox, remote persistence injection or crash recovery. Existing [#2587 per-consumer gameplay outbox](https://github.com/Trexzo/SpawnPK-RE/issues/2587) and [#2558 original reward authority](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remain open; account #2540/#2559 and ACTIVE review remain unresolved.

## Hosted acceptance

This exact combined 534-test head requires **fresh** hosted CI confirming BOTH G1876 and G1877 markers, 534/534 focused unique, Java11 major55, full Gradle SUCCESS. Separate green parent or eventual donor GREEN is insufficient. DRAFT/unmerged. For newer G21.216/217 cumulative import only new G1877 Java+note and the focused manifest delta, never overwrite a newer core Gradle blob with this 534-test older source or duplicate G1876.