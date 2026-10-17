# Chat 2 G18.6.3 — VALID older ACTIVE G4 namespace cannot rehydrate over a completed World task

Date: 2026-10-15.

**Exact certified gameplay parent:** [Chat 2 G18.6.2 PR #2629](https://github.com/Trexzo/SpawnPK-RE/pull/2629) SHA `45c7942350db8db0b87944c068bdfd97b4d651d6`, hosted [Actions #38631001904](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38631001904) **SUCCESS**, **507 unique focused**, Java11 major55 and full Gradle. Latest combined [G21.201/G18.6.2 PR #2631](https://github.com/Trexzo/SpawnPK-RE/pull/2631) is **NOT GREEN**, due to the unrelated G21.184 late-enable test failing under the new G21.201 save-worker guard (Actions #38631400806). This new G18.6.3 branch therefore deliberately stacks on the last green *gameplay* donor, not failing core.

## Source-backed lifecycle hypothesis

G18.6.2 proved that same-name replacement B can complete an existing World-scoped G4 task originally started by detached A, leaving an **ACTIVE, progress0** valid G4 namespace on A but **COMPLETED, progress1** on B. G18.6.3 follows the consequences when the exact older A namespace reappears on another same-name `WorldPlayer` C while the original completed World task remains:

- `LocalLabSlayerRuntime.ensureRestoredOwned` reads C's valid `blood-slayer-g4` snapshot extension through `LocalLabSlayerPersistence.decode` (no codec exception). It then checks `slayer.active(name)!=null || latestTask(name)!=null`. The name-based `latestTask` is still present after B completed A's task, so hydration records **`PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME`** in `invalidPersistence` against the exact C object and refuses to install or replay that old state. This is a semantic collision, not a malformed codec blob or original-server policy.
- The unchanged installed Monster Spawner terminal observer invokes G1 and G4 before later G18. G4 returns a **nonthrowing** invalid-persistence refusal on C, whereas G1 credits C's separate `WorldPlayer` extension and G18 continues the existing World/name-scoped DMM objective.
- A's original G4 namespace stays ACTIVE; B's completed G4 namespace stays COMPLETED; C holds a validated but refused ACTIVE namespace. No automatic repair, task transfer, durable event recovery or conflict resolution exists.

## G18.6.3 test acceptance

1. Register A, start a real legitimate Monster Hunter G4 task and capture its exact **valid** ACTIVE persisted namespace. Activate DMM EASY and spawn ONE canonical NPC with A as immutable `RecipientOrigin`.
2. Unregister A before NPC death; register B with same name and empty extensions. The genuine installed observer finalizes that A-origin NPC while B is current: one ground settlement, G4 task COMPLETED/persisted on B, G1 B kills1, DMM1/3. A's saved namespace remains ACTIVE.
3. Unregister B; register C with same name, and **test-only inject exactly A's valid ACTIVE namespace into C's in-memory snapshot extension before first G4 status/hydration**. This is a deterministic fixture, *not* a real account file load, socket login or malicious client input. Verify codec still parses ACTIVE but `slayer.status(name).persistenceError` is exactly `PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME`, with no active status task on C.
4. Deliver the REAL G1 delayed World respawn ticket from B's original terminal to C; verify the resulting new NPC captures C as `RecipientOrigin`. Kill/finalize the second DISTINCT NPC through the original installed observer. G4 remains rejected/nonthrowing; G1 receipts2 with B kills1/C kills1 (A kills0), G18 DMM2/3, two distinct one-coin ground settlement receipts and aggregate owner-scoped coin quantity2 (item stacks may coalesce). A and C retained ACTIVE namespaces; B retained COMPLETED.
5. No old NPC event is manually replayed. No direct reward grant, production callback injection or actual file persistence occurs.

Expected marker: `G1863_REHYDRATED_ACTIVE_G4_COLLIDES_WITH_COMPLETED_WORLD_TASK_PASS`.

## Authority boundaries

All behavior is **CUSTOM_LOCALLAB**, source/test characterization only. This does not establish how original SpawnPK handled Slayer, whether the public client can produce malformed or stale account snapshots, safe G4 task transfer, crash recovery, actual logged-in account relog, or acceptable policy for automatic repair. It strengthens [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587): future per-consumer outbox and lifecycle recovery must specify authoritative World generation/recipient, task ownership, persisted-state reconciliation, idempotent ACK and error isolation. Original reward attribution [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) remains **UNKNOWN_SERVER_AUTHORITY**.

**Diff:** exactly two files, existing G1847 terminal characterization test plus this note. Zero production Java, Gradle/manifest, G21.201 account saver, client or main changes. Parent focused list remains **507 unique**. Fresh exact-head GitHub Actions required to certify G1863/G1862/G1861/G21200, Java11 major55, 507/507 focused and full Gradle. Draft/unmerged. Chat 1 owns integration after core G21.201 contract is fixed and certified; no stacking on red #2631.
