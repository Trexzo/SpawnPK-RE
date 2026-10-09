# G21.70 — admission-to-World monotonic deadline and late-task cancellation (NO GRANT)

**Stacked base:** certified G21.69 `c62322cb88b3041de3b2d1ac7ae56091905564b0`; [hosted Actions #37962470641](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37962470641) SUCCESS, 369 focused Java 11. [Issue #2316](https://github.com/Trexzo/SpawnPK-RE/issues/2316).

## Finding

The G21.69 read-only fenced terminal freshness check uses a five-second wait only **after** the World command is submitted. The single World persistence FIFO or the cooperating account publication lock could delay task execution arbitrarily long before that wait begins. Its resulting freshness observation would not be bounded from admission. A World command already queued when timeout/shutdown occurs could also drain much later. Despite being read-only, that stale continuation must not be considered positive evidence.

## Implementation

G21.70 starts a **single monotonic five-second budget at task admission**, covering queue time, acquisition of the G21.39 account-local JVM and OS FileLock, the initial raw terminal-file and negative marker read, the actual WorldPulse command and the final disk read. Every phase checks the remaining deadline. `tick.get` waits only the remaining nanoseconds rather than starting a second five-second clock. The World command itself rechecks the age and task-active flag at entry and after its complete PREPARED owner comparison.

Each task tracks the exact World command `CompletableFuture`. Timeout, interruption, forced persistence shutdown, dropped task or uncertain outcome marks the task inactive and cancels the queued future. This is **logical cancellation, not guaranteed removal or interruption** of a World command already executing. If it drains later, the in-command active/deadline checks veto it; the command contains no account-write, inventory or Mailbox mutation. Even after failure the G21.27 reservation remains held and a G21.69 attestation cannot be attempted twice.

The task performs no World-tick disk I/O. It keeps the G21.69 pinned-path NOFOLLOW account checks and cross-process cooperating account FileLock across the World command. An uncooperative writer, late mutation **after the lock is released**, or hardware power-loss remain outside its guarantees.

Successful result remains **point-in-time, non-authorizing**: `durabilityConfirmed=false`, `liveApplied=false`, `transactionCommitted=false`, `grantAuthorized=false`, `replayAuthorized=false`, `rollbackAuthorized=false`, `releaseAuthorized=false`, `clientAckAuthorized=false`. Native C2S185/widget32181 grants, login/restart hydration, automatic replay, and R25 #1847 remain untouched.

## Focused evidence

New real World/file regression G2170 exercises (1) normal exact one-shot NO_GRANT success, (2) account publication lock intentionally held beyond the admission deadline, (3) unrelated player's World command intentionally stalls the entire tick executor beyond the deadline and later drains, (4) owner generation retirement, (5) forced persistence shutdown while the account publication lock remains blocked, (6) independent account saving and (7) terminal bytes/live PREPARED unchanged with restart quarantine and no temporary files/lock leases. Focused Java 11 test count **369→370**; exact-head hosted full build required before certification. Draft/unmerged; no live reward transaction.
