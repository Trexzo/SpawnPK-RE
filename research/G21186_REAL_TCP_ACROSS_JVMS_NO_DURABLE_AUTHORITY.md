# Chat 1 G21.186 — actual TCP LocalSession ownership across separate JVMs

## Certified base

Parent [G21.185 Draft #2574](https://github.com/Trexzo/SpawnPK-RE/pull/2574) exact SHA `0321a97be2abd90136a55ba9df3fed57ce760f41`, [Actions #38550704816](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38550704816) SUCCESS: Java11 bytecode major55, **485/485** focused, full Gradle 6m54s. G21.185 verified only a **low-level** `FileChannel.tryLock` contention and post-release reacquire across external Java processes; not actual authenticated player sessions.

The latest cumulative joint Chat 1/2 [Draft #2575](https://github.com/Trexzo/SpawnPK-RE/pull/2575) exact `67fc549a6740b92992309392db33fdab453fd62b`, [Actions #38551420416](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38551420416) SUCCESS: Java11 major55, **492/492**, full Gradle 7m54s. Chat 2's G18.4.7 and G18.4.8 evidence persists without production gameplay modification.

## New G21.186 test-only upgrade in coverage

This milestone adds only `G21186CoreCrossJvmRealSocketAccountSessionTest.java`, dedicated Gradle `g21186CoreCrossJvmRealSocketRegression`, one focused entry (**485→486**) and this research note. **No production Java modifications**. The test is explicitly opt-in inside its private process and inherited feature defaults stay OFF for normal LocalLab runs.

1. Set both experimental flags TRUE **before any player admission** and configure one private account root. In parent JVM instantiate real isolated World with file repository and a real loopback `LocalSession` socket using synthetic revision317/ISAAC185 login. The admitted named account captures an OS-backed session token and writes an owned `A_FINAL` snapshot mutation.
2. While parent socket remains connected, launch a **different Java process** sharing the exact account-root path and both flags. It runs its own World and `LocalSession`, performs the actual login handshake and must be refused before player membership. No child token is retained, and the parent player retains its name, World membership, OS lock and mutation.
3. Close parent socket and wait for the real session to unregister and finish its final World-persistence `SaveTicket`. The file repository must contain `A_FINAL`, with zero JVM token holders, before any new process is admitted.
4. Launch a **second independent Java process**, again creating real World and loopback `LocalSession` with same canonical account. This time login must succeed and hydrate exact `A_FINAL` account snapshot, mutate `B_FINAL`, complete the actual final save and release both the JVM and OS token.
5. Parent inspects real disk postimage `B_FINAL`, retained stable sibling `.session.lck` file, absent unrelated `src` profile, zero failed parent persistence tasks and zero residual JVM publication/watch resources. Both subprocesses must exit zero with expected distinct markers. Bounded child waits, stdout redirected to fixture files; no background work or network outside localhost.

This is **narrow positive cooperation evidence across JVMs using actual accepted/denied sessions**, not a full production cluster test. In particular, neither child process is externally hosted; both run on one OS and filesystem. The independent processes share the same synthetic LocalLab login protocol and exact path; no authentic recovered v308/v309 GUI client or original server policy is proven.

## Explicit nonclaims

- G21.186 does NOT prove that account files or pending writes survive process kill or power loss, and OS `FileLock` is automatically released on JVM termination with NO durable failure-negative marker.
- Noncooperating/default-OFF/raw `FilePlayerRepository.save()` writers do not obey session OS locks. An attacker can unlink/replace a lockfile or remap a path unless stronger path lifetime protection is implemented.
- Configuration changes after admission remain unsafe (see G21.184); named accounts admitted before lock enablement are not retroactively protected. Both flags MUST be fixed before any admission for the narrow experiment to apply.
- Filesystem backends/network volumes with divergent or unsupported OS lock semantics are outside scope. No daemonized server fork lifecycle or actual client reconnect contract is established.
- No generic durable account epoch, exact crash ordering/negative intent, automatic account recovery, original kill-credit attribution, settlement, grant, reward replay, cleanup, rollover, pruning, unlink, session release as durable authority or ACK.
- Frozen R25 #1847 and G21.87/G21.88 unchanged, Chat 2 gameplay and Chat 3 SpawnPK-Client untouched. Issues [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) OPEN; shared handoff [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526).

## Required exact-head hosted certification

Run Java11 major55, dedicated external-process real TCP regression marker `G21186_CROSS_JVM_SOCKET_PASS`, all **486/486** unique current-train focused and full Gradle SUCCESS. The test is NOT certified until its own exact-head Actions execution finishes green. Keep PR Draft/unmerged; never promote the experimental flags by default.
