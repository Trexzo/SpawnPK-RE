# G18.8.5–G18.8.6 — read-only World-local terminal diagnostics

## Parent and authority

- **Exact CI-GREEN source parent:** [cumulative Chat1/Chat2 #2697](https://github.com/Trexzo/SpawnPK-RE/pull/2697), exact `2d134ca81a423a52332bf4532ea978cc353abce2`, [Actions #38768318623](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38768318623) **SUCCESS**, 550/550 focused, Java11 major55 and full Gradle.
- **Authority:** `CUSTOM_LOCALLAB_TERMINAL_STAGE_ISOLATION_EXPERIMENT`, **not original SpawnPK kill, combat, rewards, client packet or server persistence policy**.
- **Default OFF:** `-Dspk.locallab.monsterSpawner.isolateTerminalStages=true` is sampled ONCE at `LocalLabMonsterSpawnerProvisioning.create(World)`. No flag change or default promotion here.
- **Two actual production files:** `LocalLabTerminalStageIsolation`, `LocalMonsterSpawnerActivationRuntime` plus one limited constructor-wire modification to `LocalLabMonsterSpawnerProvisioning`. No networking/TCP, gameplay drop/credit callback, original client, G21 account/review marker/OS lock, or `main` modifications.

## Problem resolved in this scope

The earlier G18.7.8–G18.8.4 experiment had a bounded 128-entry in-memory list of post-G1 G4/G14/G15/G16/G18 callback outcomes. However, ordinary property-based `LocalLabMonsterSpawnerProvisioning.create(World)` constructed the `LocalLabTerminalStageIsolation` privately, with **no read-only way for trusted server-package operators or diagnostics to inspect that World-local history**. The explicit test-injection overload could inspect an externally-held dispatcher; production composition could not.

G18.8.5 wires the SAME World-local dispatcher into the actual `LocalMonsterSpawnerActivationRuntime` composition. Its new **package-private** `terminalDiagnosticSnapshot()` returns:

- `null` for a default-OFF runtime, with **no dispatcher constructed**.
- A detached `LocalLabTerminalStageIsolation.DiagnosticSnapshot` when ON: saturated observational `observedCount`, `evictedCount`, and an immutable copy of at most 128 immutable `Report` instances. Each report already records `npcId`, death tick, recipient name, observed generation, spawn-time origin match and immutable per-stage `RETURNED`/`THREW`/`SKIPPED_NO_CURRENT_RECIPIENT` map.
- No dispatcher pointer, callbacks, replay, mutation, receipt grant, account writer, or network operation. Existing **seven-parameter** `LocalMonsterSpawnerActivationRuntime` constructor remains valid and delegates to the new optional eighth argument with `null`, retaining all pre-existing graph ownership checks and UI session behavior.

`DiagnosticSnapshot` copies the list under the dispatcher's synchronized monitor and never holds that monitor during gameplay callbacks. The internal counter increments only **after** a real stage-dispatch callback has concluded and been recorded. It saturates instead of overflowing (counters are observational and cannot be used as event IDs).

## Real installed runtime proof — two new regression classes

| Test | Scope | Marker |
| --- | --- | --- |
| G1885 | Normal `create(World)` factory default OFF returns null; property ON creates a read-only view. Genuine G1+ground terminal under malformed TEST-ONLY G14 with healthy activated G15/16/18; first immutable snapshot has one report; actual delayed respawn and second distinct NPC yields a second report while the old copy remains unchanged. Attempted `clear` on list and mutation of per-stage map must fail. Duplicate old-NPC finalization cannot alter history. Property change after graph composition cannot switch it OFF. | `G1885_REAL_FACTORY_READONLY_TERMINAL_DIAGNOSTICS_PASS` |
| G1886 | Normal property-ON public factory delivers **130 independent genuine canonical NPC deaths** through real G1 scheduled respawn, finalization and ground settlement. Snapshot tracks 130 observed, exactly two evictions, only last 128 retained, 130 independent G1 record receipts and 130 settled coins. Attempted mutation fails; second separate opt-in World has zero reports, proving instance isolation. No duplicate old NPC replay. | `G1886_REAL_FACTORY_BOUNDED_OPERATOR_SNAPSHOT_PASS` |

`server/build.gradle` has exactly **552 unique** focused entries (550→552), both count guards updated, and two dedicated targeted JavaExec tasks.

## Explicit nonclaims / safety

- Snapshots are package-local only; this patch **does not add an HTTP/admin endpoint, TCP/player view, logging endpoint, or export/persistence**. Trusted server-package code can read the history, but remote operators cannot query it through a supported API.
- **Not a durable consumer ledger, ACK, outbox, receipt, or replay path**. `RETURNED` means Java callback returned, not objective progress/commit; `THREW` may follow partial mutation. The 128-history eviction makes it unsuitable for durability.
- **Not original SpawnPK kill attribution**. Present LocalLab terminal-time name-based credit remains unchanged, including its known A-spawn/B-terminal generation ambiguity; a spawn-origin match diagnostic does not prove attacker or contributor authority.
- No cross-JVM account lock/epoch/admission/review marker recovery/clearing; Chat1 separate account-security hardening is not affected. [Original kill authority #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) and [durable progression outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remain OPEN.

## Required exact-head certification

Fresh hosted GitHub Actions for THIS source head: Java11 major55, full Gradle, **552/552** focused, G1885+G1886 markers and all prior G1878–84 plus G21 source gates. Until then draft/unmerged, no `main` promotion. Parent success **does not substitute**.
