# G21.69 — fenced World-tick terminal freshness attestation (NO GRANT)

**Base:** exact certified G21.68 `5fa1eea14fed2503ed9dbf02fb526ecce0ac52cb`, hosted [CI #37956039572](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37956039572), 368 focused Java11. [Issue #2314](https://github.com/Trexzo/SpawnPK-RE/issues/2314).

## Problem

G21.67 checks the on-disk terminal snapshot and G21.68 issues one World-owned candidate from that past observation. A cooperating publisher could change the account *between* those events. Neither a strict file-op receipt nor a prior read-only observation is a positive transaction commit.

## Implementation

New `WorldPlayerPersistence.attestReservedTerminalWorldFreshness` accepts **only the same object** actually issued by G21.68 on a held G21.27 account reservation, with the same current G21.67 evidence object. A different/fabricated candidate, superseded G21.67 observation, stale World generation, changed live PREPARED account, pending I/O, or duplicate issuance fails closed. An admitted attempt permanently consumes the one-shot attestation even when its disk/tick read fails.

The task runs on the **existing single bounded World persistence I/O FIFO**. Under G21.39's **cooperating account-local JVM + OS FileLock** it reads the pinned raw file, rejects permanent/transient review markers, requires NOFOLLOW regular file / stable metadata, exact G21.64 full terminal bytes and canonical terminal identity; then queues a read-only World command and waits at most five seconds while retaining that file lock. On the real World tick, under the actual owner generation and mutation lock, it requires the same reservation, candidate and original complete PREPARED account, without any filesystem I/O. After the World command finishes, the worker rechecks exact disk bytes and markers *still holding the same lock*, releases the lock, then reports a **non-authorizing** attestation.

On interruption, timeout, shutdown, queued task rejection or uncertain status, futures fail closed and the held reservation remains fenced. The in-flight task marks itself canceled before releasing its publication lock; a late World task cannot claim validity. The account lock coordinates only cooperating publishers; raw/forensic writers and actual power-loss durability are NOT proven. Since the lock is released before the evidence is consumed, even success is point-in-time and cannot be interpreted as perpetual reward authority.

**Every result remains**: durabilityConfirmed=false, transactionCommitted=false, liveApplied=false, grantAuthorized=false, replayAuthorized=false, rollbackAuthorized=false, releaseAuthorized=false, clientAckAuthorized=false. Live inventory and Mailbox remain PREPARED/UNCLAIMED. G21.64 terminal restart quarantine remains active. No client ACK, native C2S185 widget32181 grant, automatic replay, marker release or R25 #1847 modification.

Regression includes real file-backed strict terminal publication, exact G21.67 evidence and G21.68 World candidate, competing account-local lock waiter, single successful World-tick freshness attestation, one-use enforcement, unchanged terminal bytes/live owner, distinct-account save isolation, forged candidate, permanent marker, prepared raw-disk replacement, superseded evidence, changed live owner state, stale generation, restart quarantine and zero temp/lock leaks. Focused manifest 368→369 and hosted exact-head Java11/full Gradle build required before certification. Draft/unmerged.
