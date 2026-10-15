# G18.9.7–G18.9.8 — refuse LocalLab terminal dispatcher entry after World close

## Exact source and authority
- **Certified cumulative parent:** [G21.239 + G18.9.6 #2733](https://github.com/Trexzo/SpawnPK-RE/pull/2733), exact `2853740cffe0efbc6609eef0e734932c8566b163`, [Actions #38784434386](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38784434386) **SUCCESS**, 579/579 focused, Java11 major55 and full Gradle build. Includes preceding G1891–94 producing World and exact event one-shot, G1895–96 DMM lifetime evidence, and core G21.239 late World publisher lock continuity checks.
- **Authority**: `CUSTOM_LOCALLAB_TERMINAL_STAGE_ISOLATION_EXPERIMENT`, **DEFAULT OFF**; this is package-local test/extension lifecycle hardening only. Original SpawnPK monster credit/kill rules are UNKNOWN.

## Source-backed gap / narrowly scoped production change
A `LocalLabTerminalStageIsolation` instance could be explicitly bound to an **already closed** World by package-local `bindTo(World)`, or a previously bound isolated dispatcher could be handed a truly settled `FinalizeResult` **after** its World closed. Source World identity and once-event validation prevented *wrong* Worlds and duplicate *same objects*, but did not fence this *closed* World case before invocation. An unclaimed settled result from ordinary, stage-isolation-OFF `LocalLabMonsterSpawnerProvisioning.create(World)` can exist; feeding it to a separately bound opt-in dispatcher after World.close would previously record meaningless skip outcomes, and must never cause post-teardown callback behavior.

**Only one production Java file modified:** `LocalLabTerminalStageIsolation.java`.
- In `bindTo(World)`: read package-local World `closed()` before installing an injected stage dispatcher reference; if closed, fail `TERMINAL_DIAGNOSTICS_WORLD_CLOSED`. Repeating a previously valid same-World bind after World.close also refuses.
- In `deliver(World,Finalized)`: after verifying the dispatcher's exact bound World, but **BEFORE** inspecting event payload/claim or any G4/G14/G15/G16/G18 callback or report, refuse `closed()` with the same marker.
- This is a fast **entry check**, NOT an atomic fence against a concurrently starting World.close after the check, and no durable event, World epoch or consumer receipt is created. No changes to World lifecycle implementation, Monster Spawner finalized-event source, G1/ground settlement, the existing one-shot flag, any progression consumer, player registry, Chat1 G21 account locks/RAW marker publisher, original client/network, default-OFF property, main, or frozen R25.

## Two real installed-World tests
| Test | Exact behavior | Required marker |
|---|---|---|
| G1897 | On live World, inject stage dispatcher through actual `LocalLabMonsterSpawnerProvisioning.create(World,dispatcher)`, spawn and lethally kill genuine NPC, get one G1 record/ground receipt/one diagnostic. Original owner unregisters and actual `world.close()`. Attempts to `bindTo(closedWorld)` via old dispatcher, new dispatcher and package-local factory with fresh injection ALL fail `TERMINAL_DIAGNOSTICS_WORLD_CLOSED`, with no additional report | `G1897_CLOSED_WORLD_TERMINAL_PROVISIONING_VETO_PASS` |
| G1898 | Normal default-stage-isolation-OFF factory, active real G15 task, NPC genuinely dies and settles; capture its **unclaimed** real canonical `FinalizeResult` and verify G15 progress1/G1 receipt1. Independently create opt-in dispatcher, bind it to the still-open SAME World (not installed), then unregister original player and close that World. Explicit attempts to deliver captured real event twice into this closed World MUST fail the close marker BEFORE a report, stage counter or consumer callback. | `G1898_CLOSED_WORLD_TERMINAL_DELIVERY_VETO_PASS` |

No fabricated terminal objects, no grants, no retry. `server/build.gradle` adds exactly two unique focused Java test names and two dedicated tasks, hardguards **579→581**, programmatically verified 581 distinct entries.

## Certification and separate unresolved issues
- Required fresh **exact-head** hosted GitHub Actions Java11 major55/full Gradle **581/581** focused, G1897/G1898 and inherited G1891–96/G21.239 markers. A green ancestor cannot certify this source. DRAFT/unmerged, main untouched.
- This is not proof the observer can never race World.close or that the core World mutation fence is durable. It does not implement original kill attribution [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), per-consumer applied objective ACK/outbox [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), DMM reset/reconnect choice [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547), cross-JVM account writer epoch or safe ACTIVE marker grant/recovery #2540/#2559.
