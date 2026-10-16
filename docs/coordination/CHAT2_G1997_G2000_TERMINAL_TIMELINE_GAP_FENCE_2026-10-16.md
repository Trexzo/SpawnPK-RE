# Chat 2 G19.9.7–G20.0.0 — terminal timeline integrity guard

## Exact parent and provenance
- Parent: [#2884](https://github.com/Trexzo/SpawnPK-RE/pull/2884) exact `9c8c143110a665b15f01d0dd5569c915db0e72ee`, hosted **757/757** success, Java11 major55, full Gradle. 77 critical anchors inherited.
- Existing G1993–96 retained-report integrity audit verifies report IDs, failure-class consistency, fail-fast after THREW, and detail subset/exact counts. Its existing `LocalLabTerminalFailureBoundaryTally.classify` admitted a distinct untested contradiction: `G4=NOT_REACHED, G14=RETURNED` or `G14=NOT_REACHED, G16=THREW`. The classifier failed to model a non-throwing gap as a permanent suffix in a sequential callback.
- Scope: **CUSTOM_LOCALLAB OBSERVATION_ONLY**. No SpawnPK original credit policy recovered or modified.

## Production correction
`LocalLabTerminalFailureBoundaryTally.classify` now marks `stoppedBeforeStage` at the first `NOT_REACHED` derived consumer (when no previous exception) and rejects any later `RETURNED`, `THREW`, or `SKIPPED_NO_CURRENT_RECIPIENT` outcome with exact `TERMINAL_FAILURE_BOUNDARY_REACHED_AFTER_GAP_<STAGE>`. A valid partial prefix of zero, one or multiple returned stages followed by all NOT_REACHED remains accepted; a test collector may genuinely not observe G1 and this is not automatically an error. G1 THREW and previous-stage THREW retain their existing specialized `POST_THROW_STAGE_<STAGE>` check.

This check runs when `LocalLabSequentialTerminalStageTrace.record` constructs and classifies a detached report **before** marking the capture as consumed, before incrementing any G1/origin/consumer/joint/first-boundary count, and before touching its 128-report ring. Bad evidence is rejected rather than poisoning later retained-detail audits. The production callback code, NPC settlement, grant logic and player/account state are unchanged.

## Genuine tests
- G1997: one real registered player, true spawned NPC lethal lifecycle/finalization/ground drop, installed terminal observer, then separate test-only diagnostic capture; `G14=RETURNED` while `G4=NOT_REACHED` must be rejected atomically.
- G1998: same genuine terminal fixture, test-only `G4=RETURNED`, `G16=THREW` with unattempted G14/15; reject with specific G16 gap error without leaving partial trace state.
- G1999: real settled event with valid **test-only** G4/G14 callback prefix, then G15/G16/G18 NOT_REACHED; accept it as partial observational evidence, never grant reward.
- G2000: real settled event with separate completely-unobserved diagnostic capture (G1 NOT_OBSERVED and all stages NOT_REACHED); remain mathematically consistent, no false claim of credit.
- All four confirm the already-settled NPC cannot be finalized a second time, exactly one ground item, exactly one existing G1 receipt, and no reward replay. Test-only callbacks are no-ops and never invoke real progression consumers.
- Manifest **757→761 distinct classes**, four dedicated JavaExec gates, mandatory anchors **77→81**.

## Release gate and strict limits
Require hosted exact-head **761/761** including G1997–G2000 PASS, Java11 major55/full Gradle; parent certified independently. Draft and `main` untouched. No persistent event outbox, atomic per-consumer grants/ACK, safe restart replay, or authenticated original SpawnPK attribution. Never infer delivery from RETURNED.
