# Chat 2 — G20.0.1–04 terminal failure-metadata integrity (769)

## Source and authority
- **Exact parent**: [combined PR #2888](https://github.com/Trexzo/SpawnPK-RE/pull/2888), commit `3af021869fb1f32853228ec42c8c5e322f1cde93`, independently hosted **765/765** exact-head PASS, Java 11 major55, full Gradle; parallel Chat 1 cumulative [#2887](https://github.com/Trexzo/SpawnPK-RE/pull/2887) also passed.
- All code remains **CUSTOM_LOCALLAB diagnostic observation only**, not recovered original SpawnPK server authority. All account-forensics, core save/review/logout code, drop settlement, G1/G4/G14/G15/G16/G18 reward consumers and packet handlers remain untouched.

## Concrete bug and implementation
`LocalLabTerminalFailureBoundaryTally.classify` previously validated sequential stage chronology, all-or-none no-recipient skipping, G1 and stage fail-fast, but did **not** validate class-name-only failure metadata. The package-local `LocalLabSequentialTerminalStageTrace.Capture.outcomes` and `failureTypes` collections are mutable for internal tests. A test collector could record a report where `G4=THREW` has no failure type, `G4=RETURNED` retains an exception type, or `NOT_REACHED/SKIPPED` spuriously claims an exception. This corrupted World-local marginal/joint/first-boundary counters before the later read-only audit could flag it. Although installed `Capture.run` normally sets both, rejecting malformed evidence **before any collector mutation** is necessary for the experimental source integrity contract.

Production `LocalLabTerminalFailureBoundaryTally.classify` now checks that G1 `THREW` has nonblank class name and non-THREW G1 has no failure type. It checks every derived stage likewise: `THREW` requires a nonnull, nonblank failure type; `RETURNED`, `NOT_REACHED` and `SKIPPED_NO_CURRENT_RECIPIENT` require null type. Failure markers are `TERMINAL_FAILURE_BOUNDARY_MISSING_FAILURE_TYPE_<stage>` or `TERMINAL_FAILURE_BOUNDARY_UNEXPECTED_FAILURE_TYPE_<stage>`. This runs from `LocalLabSequentialTerminalStageTrace.record` **before** joint origins, first-boundary counts, origin and G1 marginals, claim consumption and ring insertion.

## Real-world regression coverage
Each negative fixture spawns one **genuine canonical World-owned NPC**, kills it, finalizes once, verifies its exactly settled ground drop and the real G1 receipt, then constructs a **separate observer** whose callback actions are test-only and never rerun game grants.
- **G2001**: two real NPC fixtures; G4 THREW with missing and blank exception type are both rejected before all counters. Repair the capture metadata and verify it can be accepted **once** afterward, without NPC re-finalization.
- **G2002**: real NPC; G4 RETURNED but carries a stale exception class. Reject, remove it and successfully record once.
- **G2003**: two real NPC fixtures; NOT_REACHED and deliberately test-skipped G4 stages carry illicit failure metadata. Reject both, repair, and preserve established no-credit semantics.
- **G2004**: positive typed-throw controls, including the actual installed G14 invalid state after genuine settlement; a separate test-only G4 throwable is accepted with class type and accurate G4 first-blocker.
- The underlying G1997 real-NPC fixture confirms on every rejection: zero changed World-local G1/origin/joint/first-boundary counters, no report, read-only evidence consistency audit still CONSISTENT. After correcting the capture, re-record succeeds exactly once and a second attempt is rejected as already recorded. No ground/grant callbacks rerun.

## Test and merge gates
- **Focused 765→769 unique**; four new dedicated JavaExec regressions and **81→85 critical MUST-RETAIN** anchor classes. Existing G1997–G2000 and core G21323–G21326 untouched.
- Require GitHub Actions exact-head **769/769**, G2001–G2004 PASS, Java11 major55, complete Gradle. Draft PR only; `main` unchanged. Do not equate a complete diagnostic with durable consumer grant ACK, restart-safe outbox or safe replay. The durability blocker is still [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587).
