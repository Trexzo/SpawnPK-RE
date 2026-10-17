# G21.135 — inside-lease observer exception containment and retry (NO EPOCH)

Certified G21.134 parent [Draft PR #2448](https://github.com/Trexzo/SpawnPK-RE/pull/2448), exact `89c711be56635f94a26f5d823c34d7d1462d40f3`, hosted [Actions #38263525387](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38263525387) SUCCESS Java11 434/434. Issue #2449.

G21.134 introduced an opt-in `MailboxSevenPlaneContinuousPhysicalWitnessV1.InsideLeaseObserver` which executes directly under the one continuous G21.100 lifecycle OS advisory lease. The initial genuine four-bound writer and forged-v3 tests passed, but failure-path callback short-circuiting and lease release needed focused regression coverage.

G21.135 makes no production behavior changes; it adds `G21135MailboxInsideLeaseFailureContainmentIntegrationTest` and advances the focused manifest 434→435. It builds a real four-result-bound World account (without live grants), obtains all original independently supplied seven-plane pins and checks the certified G21.134 baseline. It exercises, on the **original physical observer overload**:
1. Checked IOException thrown by the **first** in-lease observer: callback not run; second observer not visited; original exception identity/message preserved; OS/JVM lease released.
2. Checked IOException thrown by the **second** in-lease observer: interpass callback executed exactly once; witness refused rather than published; held lease released.
3. Unchecked IllegalStateException thrown in the **first** observer: no callback, no silent success or unhandled lease.
4. Checked IOException thrown by the between-captures callback: only first observer executed; second not invoked; held lease released.

Each failure is followed by successful read-only original G21.129 physical **and** G21.134 four-real semantic witness re-entry on the same account; if the OS lease leaked those healthy retries would fail. The test ends with zero active JVM leases/local attempt watches and no reward grant. Java11 focused count 435 and hosted exact-head build required before certification.

These tests exercise existing exception-handling invariants; they are not proof of a process-crash-safe multi-file epoch, complete raw/default/historical/future writer census, signed provenance or hardware persistence. No default writer alterations, archive format changes, archive rollover/pruning/cleanup/unlink or live World grants/replay/session/release/client ACK; G21.87/88 and frozen R25 #1847 remain untouched. PR Draft/unmerged.
