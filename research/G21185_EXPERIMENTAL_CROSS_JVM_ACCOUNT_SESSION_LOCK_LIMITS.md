# Chat 1 G21.185 — experimental cooperating cross-JVM account session OS lock

## Base and observed gaps

- **Exact certified base:** G21.184 [Draft #2568](https://github.com/Trexzo/SpawnPK-RE/pull/2568), SHA `e8c7f47d4d49aa887ba6697b2f014883c49d7033`, [Actions #38545037068](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38545037068) SUCCESS Java11 major55, 484/484 focused, full Gradle 7m36s.
- Existing `LocalJvmAccountSessionLease` token map is **JVM-local**, and its opt-in feature remains default OFF; it does not prevent two different server processes from independently claiming the same account file. The G21.184 inverse-toggle regression also proves late enabling cannot retroactively enroll an unleased World.
- Long-term global account ownership and crash-state design stays at [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) and [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540). Nothing here authorizes mailbox transaction/grant recovery.

## G21.185 opt-in feature

The second opt-in property `-Dspk.local.experimentalCrossJvmAccountSessionLock=true` (DEFAULT OFF) requires the existing `-Dspk.local.experimentalJvmAccountSessionLease=true`. Otherwise admission fails rather than advertising a nonexistent lock.

In the exact real FilePlayerRepository named-profile path, after checking no already-held local account token, `LocalJvmAccountSessionLease.acquire` obtains an exclusive **OS FileChannel.tryLock** on a persistent `account-file-name.session.lck` sibling. This lock remains held by the exact admitted session's token through the existing final `SaveTicket.completion.get(...)` success; it is released on proven successful final write, or if initializer failed before it admitted a session. A failed/unconfirmed final save deliberately retains the JVM token and OS lock for that process lifetime. The lockfile is NEVER unlinked as part of normal release; following symlinks at the lockfile itself is prohibited by `LinkOption.NOFOLLOW_LINKS`.

This is **cooperative** enforcement: both JVM processes must use both properties before accepting sessions and target the exact same normalized underlying account-file path. It does not secure the unguarded default, raw/legacy save paths, unrelated processes, changing account-root resolvers, hostile directory replacement/unlink, or a process crash. OS locks are released when a process terminates, **without any durable failure intent**. Use of this lock alone cannot justify reconnect after an unconfirmed final save or promise crash-safe account state.

## G21.185 test and nonclaims

`G21185CoreCrossJvmSessionFileLockTest` proves the low-level cooperative lock, **not real network login**:

1. Build a named-account temp fixture and opt into both flags. Parent JVM acquires the real World persistence path token before any actual player registration, confirming stable regular sibling lockfile and absent account snapshot.
2. Launch a separate child JVM running the *same test class in child mode* with equivalent classpath and exact settings path. It attempts the same account token; it must receive `G21.185 CROSS_JVM_ACCOUNT_SESSION_DENIED`. Parent token and lockfile remain.
3. Release the unused parent token through initialization-failure release path; another fresh child JVM must now acquire and release the same account lock cleanly, without unlinking the file.
4. Check no JVM token or publication lease/watch leak. Use bounded child process waits and a transcript file, not pipe-buffer-dependent reads; no account snapshot is written.

The test is intentionally restricted to the coordinator seam, because actual multi-process login/session/final-save/crash races and raw writer exclusion need **separate** end-to-end tests. It must NOT be described as fully safe cross-process account persistence. It cannot establish OS-level lock semantics on unsupported network filesystems, prevent an attacker from unlinking/replacing the lockfile, or prevent a healthy legacy writer from bypassing the protocol.

## Certification

One new helper, one opt-in implementation change, one regression class and one dedicated Gradle task; focused count **484→485**. Hosted exact-head Java11 major55, `G21185_CROSS_JVM_LOCK_PASS`, 485/485 current-train focused and full Gradle required. No result is certified before that run; Draft/unmerged.

Do not merge to `main`, enable default, automatically replay/delete/repair account data, issue grants/rewards, settle native mailbox transactions, cleanup, rollover, prune, unlink, release sessions as durable authority or ACK. Frozen R25 #1847, G21.87/G21.88 untouched. Chat 2 gameplay and Chat 3 client repos are untouched. Shared handoff [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526).
