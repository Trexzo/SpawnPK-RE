# Chat 1 G21.181 — opt-in JVM-scoped account session lease (NO DURABLE AUTHORITY)

## Authority and baseline

- Parent **certified negative** G21.180 [Draft PR #2557](https://github.com/Trexzo/SpawnPK-RE/pull/2557) exact `016656c948fd19a1e4311188a91df56e425b3671`, [Actions #38536674449](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38536674449) SUCCESS, Java 11 major55, **480/480** focused and full Gradle.
- G21.180 source-derived finding: two independent simultaneously live Worlds admit the same normal named account over separate genuine synthetic revision317/ISAAC sockets and each performs a successful final save; B's independent later snapshot overwrites A, even though both writes succeeded.
- Core problems stay open: [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) for cross-World writer ownership and [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) for durable final-save failure/quarantine. Chat 2 handoff [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526), Chat 2 G21.179 integration [#2555](https://github.com/Trexzo/SpawnPK-RE/pull/2555).
- **Opt-in property:** `-Dspk.local.experimentalJvmAccountSessionLease=true`. DEFAULT OFF, so G21.180 continues to characterize the existing behavior and no existing LocalLab actor/profile policy silently changes.

## Narrow implementation

`LocalJvmAccountSessionLease` owns an in-memory exclusive per-actual-path token, not a file lock, during a persistent file-backed `LocalSession` admission. `LocalSessionPlayerInitializer` obtains it *after canonical profile selection and before repository load/hydration*, still inside its existing per-World initialization lock. The JVM-wide map uses the normalized absolute resolved path and rejects another World with that same path before any player state hydration/registration. Non-file repository adapters and non-persistent profiles remain outside this experiment.

The token transfers exactly once with the initializer's `Result` to `LocalSession`. An initializer exception releases its not-yet-transferred token. Session teardown retains the token **until** the exact final `SaveTicket.completion.get(...)` returns successfully. Only then may a fresh session in another World acquire that path and read the confirmed account postimage.

**Fail-closed uncertainty:** a failed, timed-out, never-published or otherwise unconfirmed final save deliberately retains the token for the remaining JVM lifetime. No automatic reclaim on World close, no crash-durable record, no operator recovery/delete API. This prevents promoting an unconfirmed write into a success claim but can deny further logins until restart. After restart the in-memory token disappears, and G21.179's stale-BOOTSTRAP/recovery risk remains. Do not advertise this as a fix to generic durable final-save safety.

## Hosted acceptance target

`G21181CoreSocketOptInJvmAccountSessionLeaseIntegrationTest` uses two *simultaneously instantiated Worlds*, two genuine loopback `LocalSession` listeners and a single named profile. With opt-in enabled:

1. World A successfully logs in, captures a lease and applies `A_FINAL` through `World.submit(player,generation,action)`.
2. Competing World B's real TCP login is refused before membership/hydration; A remains owner, exact token stays held.
3. A disconnects and confirms final account-file postimage and completed final write. The token is released.
4. With World A **still instantiated**, a new World B TCP session now succeeds, hydrates `A_FINAL`, changes to `B_FINAL`, completes its final save and releases its own token.
5. Verify no `src` profile, zero save failures, no token/lease/watch leaks. The default-off G21.180 regression must still pass in the same current-train manifest.

Dedicated Gradle `g21181CoreSocketJvmSessionLeaseRegression`, focus **480→481**. Exact-head hosted Java11 major55, 481/481 and full Gradle required. No historical original v308/v309 graphical client claim.

## Explicit limits and future design

- Same JVM only, in-memory only. Independent server JVMs/hosts and raw legacy `FilePlayerRepository.save()` remain uncoordinated, as do external file writers; no atomic cross-process login/save epoch.
- File resolver can change outside the token snapshot; this experiment does not establish path stability against all adversarial remapping or symlinks.
- This is **not** a snapshot durability receipt, session identity proof across process restarts, full persistent account fencing, or authorization for mailbox settlement, grant, reward replay, cleanup, rollover, pruning, unlink, session release, or ACK.
- One JVM token for a healthy final-save path **does not** protect mutable state before a crash or coordinate atomic multi-file power-loss epochs. The full protocol still needs cross-process account-generation fencing, durable negative intent, NOFOLLOW/bounded checks and fsync/crash cutpoint tests.
- Keep R25 #1847/G21.87/G21.88 frozen. Do not merge or enable default until these claims are independently reconciled. Chat 2 gameplay/Chat 3 client code untouched.
