# G21.296/G21.297 — both final-save teardown waits preserve caller boundaries

Parent [#2851](https://github.com/Trexzo/SpawnPK-RE/pull/2851) exact `286f5b084b8544d1e80d62c82b5193d32191c587`, **707 candidate** integrating G21.294/295 worker-completion wait and G19.6.9/70 G1 outcome retention.

## Second independently verified interruption gap
`LocalSession.captureFinalSaveAndUnregister` calls `finalWorldAction.get(5,SECONDS)` before `saveAccountFinal` reaches the later worker SaveTicket wait. Both have independent interruption boundaries and both ultimately enter the existing broad final-save failure catch. G21.294 fixed only worker-completion interruption; a thread interrupted while waiting for **the World-owned capture/unregister/publication command** still lost its interrupted bit under the broad catch. This patch uses the same `LocalFinalSaveCompletionWait.awaitConfirmed(finalWorldAction,5000L)` for the earlier World command. Existing TimeoutException branch still logs PENDING and returns null; the World-owned action still owns its reserved task and may settle after the caller deadline.

## Tests on actual World command futures
- **G21296:** live isolated World (50ms pulse), real registered WorldPlayer, real running World.submit command blocked on deterministic latch; separate waiting thread interrupted while command remains in flight; caller's interrupt bit restored, running World command never canceled, and action later completes.
- **G21297:** same actual World command held pending, bounded caller timeout; command future not canceled/completed prematurely, later independently finishes. No retroactive final-save ACK.
- G21.294/295 worker Future tests still run. All 23 G21.293 critical retention anchors unchanged.
- Manifest **707→709 distinct**, individual Gradle JavaExec gates.

**Boundaries:** These are live World scheduling tests and I/O-thread behavior corrections, not a durable final-save outcome or owner epoch; no automatic marker cleanup, replay, grant authority, or change in saved bytes. The one-shot negative review behavior is preserved. Requires independently hosted exact-head 709/709, G21294–97, Java11 major55 and full Gradle. Draft/unmerged, main unchanged. Issues #2540, #2559 remain open.
