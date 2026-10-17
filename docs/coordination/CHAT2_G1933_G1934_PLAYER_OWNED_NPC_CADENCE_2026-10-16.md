# G19.3.3/34 — shared WorldPlayer cadence across distinct NPC attack handlers

**Parent:** [G1931/32 #2790](https://github.com/Trexzo/SpawnPK-RE/pull/2790), source exact commit `c9114e20d9aedc2f0269de3dd8047c3fb30fb033` (637 focused; own hosted CI after one G1931 test-only checked interruption fix required). [G1929/30 #2789](https://github.com/Trexzo/SpawnPK-RE/pull/2789) at `8c68e23e8baa0ba2fcc6c2ee2993c561f1638936` is independently hosted green 635/635.

## Residual gameplay race

A monitor on `LocalCanonicalNpcAttackHandler.handle` only serializes calls to **that exact handler instance**, not two session/wrapper-level handler objects that reference the **same registered WorldPlayer**. Because `nextAllowedAttackTick` was held separately by each handler, both could still admit an opcode72 current-client click at the same world tick and each deal 10 damage. Current `WorldPlayer.combatState().nextAttackTick` is intentionally retained for separate legacy/auto-combat scheduling; blindly reusing it would couple unrelated engine paths.

## Production fix

Add a dedicated `WorldPlayer.canonicalNpcAttackLock` + monotonic in-memory `canonicalNpcAttackNextAllowedTick` for exact canonical NPC-click cadence. Do **not** edit existing `CombatState`, account storage or packet codecs. `LocalCanonicalNpcAttackHandler.handle` now delegates under `synchronized(player.canonicalNpcAttackLock())` and uses the player-owned next-tick for admission and successful cooldown publication. This serializes both one handler and distinct handler wrappers on the same WorldPlayer. Remove the redundant per-handler monitor to avoid a needless secondary lock order; `nextAllowedAttackTick()` reads the synchronized player-owned value.

Lock scope includes caller-provided pre-resolution, hitmask and finalizer callbacks, so this is NOT a general nonblocking runtime design or complete player-owner epoch fence. Scope is one **WorldPlayer object**, not account name or original SpawnPK session protocol. No original server cadence policy claimed.

## Two deterministic actual two-thread tests
- **G1933**: same world, current generation, same canonical NPC/projection, two separate handler objects created with same WorldPlayer and beforeResolution hook, two independently ISAAC-decoded opcode72 requests. First thread parks inside pre-resolution, second thread is blocked on shared player monitor. Exactly first HIT10/packet65; second `CADENCE_BLOCKED`, no mask, damage, grant. After World.clock advances, second handler can legitimately hit.
- **G1934**: same setup but two distinct canonical NPCs. First damages NPC A only. NPC B remains untouched until cooldown, then second handler damages NPC B by 10.

Reuse and extend the existing `G1931ConcurrentSameTickNpcAttackFenceTest` helper with an opt-in separate-handler flag; original G1931/G1932 same-instance cases are retained. Two new focused wrappers/JavaExec tasks. Gradle manifest **637→639 unique** mechanically checked.

**Requires fresh exact-head 639/639 focused, Java 11 major55/full Gradle and G1931–34 markers.** Main untouched; all PRs draft. Separate WorldPlayers, reentrant callback paths, cross-session/account persistence, terminal loot/witness authority and durable per-consumer outbox are not claimed solved. Original kill-credit provenance [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) and replay-safe consumer [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remain open.
