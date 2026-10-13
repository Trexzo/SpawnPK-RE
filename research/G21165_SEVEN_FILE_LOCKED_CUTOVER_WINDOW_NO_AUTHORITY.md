# G21.165 — deny seven-file raw mutation during optional cutover (NO AUTHORITY)

**Certified parent:** G21.164 [Draft PR #2508](https://github.com/Trexzo/SpawnPK-RE/pull/2508), exact `2d496f7d2fe35ebcd17a9a823425c6964613e8ac`, [Actions #38390603298](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38390603298) SUCCESS Java 11 464/464 focused, full Gradle. Issue #2509.

## Source-confirmed observational window

The G21.164 opt-in fresh cutover method holds the same cooperating G21.39 account publication lock during its original G21.71 account/marker inspection and a final denial. But a RAW/default/legacy/uncooperative writer can mutate files **without acquiring that advisory lock**. A retained `RestartRecoveryEvidence` enum is insufficient to bind same-state account-byte rewrites, marker-content mutations or optional PREPARED/COMMIT sidecars that move between observation and the cutover refusal.

G21.165 composes existing G21.87 physical evidence methods **inside that one already-held, three-second-acquisition-bounded lock**, without any nested lock: bounded, NOFOLLOW physical seven-file SHA-256 witness (account + four review/strict marker leaves + PREPARED and COMMIT journals); fileKey/creationTime/optional ctime census and directory-ancestry check. It samples before and after the in-window G21.163 deterministic test seam; any drift or failed second evidence read becomes a checked `G21.165 CUTOVER_WINDOW_UNSTABLE_UNSAFE_NO_AUTHORITY` veto. The unchanged case still passes to G21.162's unconditional negative disposition/signer-root check; **there is no positive return**.

A new `G21165MailboxSevenFileCutoverWindowIntegrationTest` performs five real uncooperative raw mutations while the cooperating publication lock is held: (1) valid same-state account comment rewrite, (2) new negative review marker, (3) same-state marker byte rewrite, (4) PREPARED sidecar insertion and (5) COMMIT sidecar insertion. Each must veto without deleting or rewriting the external evidence, and test-only resets restore the healthy legacy state. Original account bytes and zero JVM leases are confirmed. No production writer or world reward flow is invoked.

**Acceptance:** Java11 major55, dedicated regression, 465/465 focused and exact-head hosted full Gradle SUCCESS. Keep Draft/unmerged until verified.

## Explicit non-claims

A bounded before/after observation detects changes **inside** its sampled window. It does not forbid RAW, default, historical or future uncooperative writers from writing **after** the final sample; undetectable ABA may exist on platforms without reliable change timestamps. Neither independent signer custody/provenance nor atomic cross-file crash/power-loss durability has been proved. No production World/restart/settlement call path is connected to this opt-in method; no rollover, cleanup, pruning, unlink, grant, replay, session, release or ACK authorization. Original G21.129 scanner/writers and frozen R25 #1847/G21.87/88 untouched.
