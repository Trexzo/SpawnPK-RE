# G19.4.7/48 — canonical NPC hitmark relay to independent World viewers

**Exact parent:** [#2811](https://github.com/Trexzo/SpawnPK-RE/pull/2811) commit `2770ba90dd613c507f7a5bd46c4231877a5cc75b` (667 unique focused candidates), the cumulative Chat1 G21.278 account-security and Chat2 G19.4.6 completion-tick cadence source. Fresh hosted CI for this parent and this branch is independently required.

## Gap found by source comparison

`LocalCanonicalNpcAttackHandler.handleUnderPlayerCadence` applied HP to the canonical **World-owned** NPC, but called `NpcRegistry.sendMaskLocal(clicked,mask,writer)`. That publishes packet-65 only into the attacker's viewer-local writer; it never invokes `SharedNpcWorldRelay.relayMask`. Other WorldPlayer viewers of the same canonical NPC therefore do not receive the immediate hitmark/HP update through the existing World NPC semantic presentation queue.

`NpcRegistry.sendMask`, already used by other shared NPC producers, first invokes `sendMaskLocal` and **only after successful packet output** queues the semantic mask via `SharedNpcWorldRelay.relayMask`, which uses canonical NPC entity ID + definition and translates to each recipient's own scene index after its player-81 ordering barrier. This is the intended World-shared presentation service. No second HP application is needed or allowed.

## Narrow production correction

Replace the single canonical attack `npcs.sendMaskLocal(...)` invocation with `npcs.sendMask(...)`. Preserve the existing exact 10 HP LocalLab fallback, source packet65 type1, cadence, finalizer, current/owned WorldPlayer check, exception propagation and post-HP terminal-failure handling (G1937–46). The source packet is still published first. If it throws, there is no remote relay. If the source has no registered shared relay context (e.g. isolated tests), the original attacker-local semantics remain.

## Exact runnable tests

- **G1947:** two independently registered WorldPlayers in one World with separate session writers and distinct viewer `NpcRegistry` projections bound to one canonical 30-HP NPC. Decode the actual ISAAC client option2 opcode72, land one 10 HP canonical hit, and require one WorldNpcPresentationEvents semantic `Target.CANONICAL` mask for the spectator (correct source/player generation), **none for source**. Flush the spectator's presentation barrier: one packet delivered, event marked consumed, second flush delivers nothing.
- **G1948:** identical two-viewer setup, but attacker writer becomes terminal before the click. Exact canonical HP still commits 30→20 (known postdamage checked IOException), caller sees the checked failure, and no ghost mask is queued or delivered to the spectator.

Manifest **667→669 unique**, with two explicit JavaExec targets. Require independent hosted **669/669**, Java11 major55 and full Gradle before certifying; existing game/cadence and Chat1 account/persistence production remain unchanged.

## Boundaries

This is LocalLab **presentation**, not replication of NPC damage authority (already World-owned), a durable network delivery acknowledgment, an exactly-once observer after a crash, or recovered original SpawnPK client/server combat rules. Player81 barrier and source/viewer generation ownership are delegated to the existing `SharedNpcWorldRelay` / `WorldNpcPresentationEvents` source. Genuine remote disconnects and partial packet writes can still prevent delivery. Neither recipient kill-credit nor rewards are inferred by the new hitmask.
