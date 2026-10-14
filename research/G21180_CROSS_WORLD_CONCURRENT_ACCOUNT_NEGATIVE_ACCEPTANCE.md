# Chat 1 G21.180 — simultaneous Worlds / one named account (negative acceptance)

- **Base:** certified G21.179 [Draft PR #2553](https://github.com/Trexzo/SpawnPK-RE/pull/2553), SHA `439345dcf0092d345495e329b0968fbdbd94d570`, [Actions #38533045368](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38533045368) SUCCESS Java 11 major 55, 479/479 focused, full Gradle.
- **Owner:** Chat 1 (World/session/persistence). Chat 2 gameplay coordination [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526); Chat 3 remains exclusively client-source recovery.
- **Unresolved design:** [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), generic durable fail-closed final-save/account admission. **Do not close on this characterization.**
- **Scope:** one new integration class, one Gradle focused manifest entry (479→480), one dedicated Gradle regression, this research note. No production code, no main merge.

## Why this distinct test matters

G21.176 [PR #2538] proves a simultaneous duplicate *named login to the SAME World* is refused by its `PlayerRegistry`. G21.178 [PR #2549] adds a negative admission set for final SaveTask failures retained **only for the lifetime of its own World**. G21.179 [PR #2553] shows a new World can hydrate an earlier BOOTSTRAP snapshot after failed final save. None establishes account ownership across two simultaneously running, separately isolated `World` objects, even within one JVM, or a durable single account-writer session epoch.

`FilePlayerRepository.saveForWorld` has a cooperating publication lock during the account replacement; that serializes some writes, but neither a single file replacement nor a shared `FilePlayerRepository` object proves that *the older session's pending snapshot is still the authoritative account state*. A snapshot taken independently in another World can succeed later and overwrite an earlier World final save. This test does not assert what a distributed design should eventually do; it records the present behavior.

## G21.180 deterministic real-socket scenario

1. Allocate a private temp profile root and absent ordinary named account `g21180-shared`. Create two **simultaneously live** isolated Worlds sharing a real file-backed repository.
2. Authenticate real loopback `LocalSession` connections in World A, then World B, both using the existing synthetic revision 317 / ISAAC185 / widget 912 harness. Wait for both actual BOOTSTRAP saves before changing player state. Each World may have one player with the same canonical name, but distinct `WorldPlayer` objects and independent registries.
3. With **both sockets still live**, use separate `World.submit(player, generation, ...) ` calls to write `extension.core_cross_world.phase=WORLD_A_FINAL` and `WORLD_B_FINAL`. Verify each World retains its different local value.
4. Disconnect A first; wait for actual session final write and inspect the real disk account for `WORLD_A_FINAL` while B remains registered. Then disconnect B; verify the final disk account contains `WORLD_B_FINAL`, not A's previously committed snapshot, **despite both final writes completing normally**.
5. Close both Worlds, check no incidental `src` alias, zero failed persistence counts, zero publication JVM leases/watches and remove fixture files.

## Expected negative evidence, not a certification claim

`crossWorldConcurrentAccountCurrentlyAllowed=true`, `sequentialLastWriterWins=true`, `crossWorldSessionOwnershipFence=false`, and `durableCrossWorldIntent=false` are expected **test findings**, not improvements. A green test demonstrates the hazard. It does not show cross-process races (both Worlds run in one JVM), power-loss behavior, a malicious raw writer, real graphical client login, or original SpawnPK rules.

**No production permission** is granted for settlement, grants, replay, cleanup, rollover, pruning, unlink, session release, ACK, or history repair; frozen R25 #1847 and G21.87/G21.88 remain untouched.

## Design implications for #2540 (not implemented)

Any future account-level protocol needs an explicit ownership epoch and writer-participation contract, not only a final write lock. A safe admission design must distinguish:

- One World / two sockets: registry-level duplicate ownership, currently covered by G21.176.
- Two Worlds / shared account: simultaneously live owners must either be rejected or mediated under an explicit policy across server processes and generations.
- Failed final save: negative account intent must be durable before the relevant state publication and checked before hydration after World restart. The BOOTSTRAP account may already exist in a first session.
- Successful but stale write: even without I/O exceptions, another World may publish an independently captured snapshot afterward; final-save failure quarantine alone does **not** solve that class.
- Legacy/raw writers: the existing direct `save()` path bypasses the cooperative publication contract; no complete exclusion can be claimed without enforcement and migration.

A future design should specify exact account path/epoch encoding, NOFOLLOW validation, bounded reads, checksum/digest, immutable generation ownership, crash cutpoints around intent+account writes, file and directory fsync, all participating writers, cross-process lock/admission sequencing, and a **manual** audited resolution path for ambiguous disk states. Merely creating a best-effort marker or accepting the latest timestamp is not a safe general recovery mechanism.

**Required certification:** exact-head hosted Java11 bytecode major55, dedicated regression, **480/480** unique current-train focused tests, full Gradle SUCCESS. Keep Draft/unmerged until those pass. Chat 2 integration PR #2555 is based on G21.179 and is not to be overwritten by this Chat 1 core-only branch.
