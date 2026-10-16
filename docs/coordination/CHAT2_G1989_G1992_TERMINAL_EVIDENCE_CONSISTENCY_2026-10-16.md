# Chat 2 G19.8.9–G19.9.2 — process-local terminal evidence consistency

## Source and precise scope
- Parent: [Chat1 cumulative #2878](https://github.com/Trexzo/SpawnPK-RE/pull/2878) exact `cc27e04de9af25842bc6171c6c5ba81ae44f9e76`, 745 candidate with G21.317/318 **65 must-retain** critical regressions, actual TCP final-save account/ACTIVE inode-drift controls and Chat2 G1985–88 first-blocker production.
- Latest independently certified cumulative: [#2877](https://github.com/Trexzo/SpawnPK-RE/pull/2877), 743/743 Java11 major55/full Gradle. Gameplay donor [#2875](https://github.com/Trexzo/SpawnPK-RE/pull/2875), 741/741 certified.
- Authority: **CUSTOM_LOCALLAB_OBSERVATION_ONLY**, never an original SpawnPK server grant policy, persisted death event, transaction, positive final-save ACK or safe reward replay.

## New implemented production module
`LocalLabTerminalEvidenceConsistencyAudit` examines ONE detached World-local sequential-terminal snapshot, producing an immutable bounded (`MAX_FINDINGS=64`) operator result with `CONSISTENT`, `DRIFT` or `INDETERMINATE_SATURATED`.
- It reconciles observed terminal count against origin totals, six consumer totals, first-blocker boundaries, origin × consumer × evidence cross-tab, bounded detail-ring retained+evicted totals, and the first failure category for each throwing consumer.
- It separately checks the G4 skipped count equals the NO_CURRENT_RECIPIENT first-boundary count, ensuring a genuine disconnection isn't reported as an exception.
- It uses overflow-safe addition and treats any saturated Long.MAX_VALUE counter as **indeterminate**, not as a falsely proven exact match. It prioritizes demonstrable non-saturated contradictions as DRIFT.
- `inspect(source)` derives all components from one snapshot, while `inspectComponents(source,summary,joint)` is intentionally read-only and allows test-only stale/mixed-time combination detection. Snapshot objects are detached and immutable; negative findings cannot alter live state.
- `hasDurableGrantAcknowledgement()` and `safeToAutomaticallyReplay()` always return false. Even CONSISTENT means *only* that already-recorded in-process aggregate observations agree mathematically; it cannot prove an objective was credited or persisted.

## Real-runtime and negative tests
- G1989 (strengthened G1966): 131 genuinely spawned and settled NPC deaths, 128 retained detail reports, 3 evictions, exact consistent source-derived joint/marginal/first-boundary counts and immutable result.
- G1990 (strengthened G1967/G1884): real postground G1 exception and real G14 malformed persistence callback with distinct healthy control, all reconciled as consistent without reward replay.
- G1991 (strengthened G1966): mix one-death snapshot with 131-death consumer summary, and separately mix 131-death summary with stale one-death origin × consumer cross-tab. Both return DRIFT with bounded immutable findings; this is not simulated NPC settlement.
- G1992 (strengthened G1959/G1965): actual disconnected recipient, foreign World and same-World foreign collector; impossible G1-threw→G4-returned observation from a genuinely settled terminal is rejected BEFORE any tally is changed, leaving zero-observation diagnostic audit CONSISTENT.
- Four focused JavaExec regression entrypoints. Manifest **745→749 unique**; current Gradle critical coverage expanded **65→69 named MUST-RETAIN regressions**, preserving all Chat1 account/TCP and previous Chat2 gameplay anchors.

## Release gate
Hosted exact-head **749/749**, G1989–92, Java11 major55, full Gradle, and independent certification of parent #2878. All PRs draft/unmerged, main untouched. No durable per-consumer outbox, event identity, atomic grant/ACK or restart-safe replay yet; #2587 remains open.
