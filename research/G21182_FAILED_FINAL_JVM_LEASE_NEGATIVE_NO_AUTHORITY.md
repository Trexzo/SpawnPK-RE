# Chat 1 G21.182 — failed-final JVM session lease negative (opt-in only)

## Source frontier

Parent G21.181 [Draft PR #2560](https://github.com/Trexzo/SpawnPK-RE/pull/2560), SHA `b6b51e77f22ce9a1eff0f0b76a2e77511cb28bed`, introduces **experimental DEFAULT-OFF** `-Dspk.local.experimentalJvmAccountSessionLease=true` with exact resolved file path keyed Java-process memory session lease. Parent builds a lease before file-backed hydration and releases only after a confirmed final `SaveTicket` success. G21.180 [Draft #2557](https://github.com/Trexzo/SpawnPK-RE/pull/2557) was **CERTIFIED** as negative evidence 480/480 at `016656c948fd19a1e4311188a91df56e425b3671`, [Actions #38536674449](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38536674449).

## G21.182 narrow scenario

One deterministic additional test using real localhost `LocalSession` synthetic revision317 socket, two **separately instantiated** Worlds and a named profile:

1. First account file is absent. Login succeeds under opt-in JVM token; genuine asynchronous BOOTSTRAP persistence creates a healthy account snapshot before arming fault.
2. Perform `World.submit(owner,generation,...)` mutation `extension.core_jvm_failure.phase=UNSAVED`; use the existing package-test `FilePlayerRepository.beforeWorldReplace` seam to fault **only** the actual final `saveForWorld`.
3. Verify the failure is counted by the persistence worker, bootstrap account bytes remain byte-identical, same-World G21.178 memory-negative exists, and G21.181 token was **not released**.
4. Fully close World A. Instantiate an entirely new World B in the **same JVM**, against a healthy file repository for that profile. Its actual socket login is refused before account hydration and membership while the negative opt-in token remains. Verify account bytes unchanged.
5. No production code change, no release/recovery API. `LocalJvmAccountSessionLease` retains that token until JVM exit (test process end). The test deliberately does not claim zero **account-session** token retention, which is the safety condition. Check instead zero unrelated publication leases/watches.

This improves coverage of the experiment and distinguishes G21.179's DEFAULT-OFF ability to reload BOOTSTRAP bytes in a fresh World. It does **not** prove cross-**process** restart safety: the in-memory token vanishes on a restarted JVM, and no durable failure marker exists. The opt-in system property must be held fixed during actual service lifetime; toggling it during sessions is outside this experiment's contract. Legacy/raw writers remain outside this check.

Dedicated `g21182CoreFailedFinalLeaseRetentionRegression`, focused count **481→482**, Java11 major 55 and exact-head full Gradle required. Until hosted CI passes this is a test target, not a certified result.

Keep Chat 2 G21.179 gameplay integration [#2555](https://github.com/Trexzo/SpawnPK-RE/pull/2555) untouched; coordination [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526). Design issues [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain OPEN. No atomic multi-file crash epoch, raw-writer exclusion, signer custody, historical settlement, grant, replay, cleanup, rollover, pruning, unlink, release, or ACK permission. Frozen R25 #1847, G21.87/G21.88 untouched. PR remains Draft/unmerged.
