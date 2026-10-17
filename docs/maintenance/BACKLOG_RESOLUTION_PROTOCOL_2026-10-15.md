# Open backlog resolution protocol — 2026-10-15

## User goal

Work through **every** open pull request and issue in `Trexzo/SpawnPK-RE`, resolve real blockers, consolidate safe source authority, and close only tickets no longer requiring distinct work. End state eventually has no **unresolved** open items; reducing open counts with unreviewed closes is NOT an acceptable substitute for implementation, integration, testing and human acceptance.

## Complete snapshot

- [Open issues by exact GitHub number](OPEN_ISSUES_SNAPSHOT_2026-10-15.md): **149** open issues at snapshot, all indexed with preliminary title-derived workstream (not disposition).
- [Open PRs by exact GitHub number](OPEN_PRS_SNAPSHOT_2026-10-15.md): **344** open PRs at snapshot. This was one greater than the initial 343 count because additional contributors opened new work during the audit. Search is partitioned by creation calendar day to avoid GitHub search truncating at 100 per request. All inventories are snapshots and are NOT live status APIs.

## Important architectural facts

- `main` is NOT the current experimental train. PR #1847 is a **frozen older R25 cumulative 319-file promotion** with unresolved **human HOME Walk-here/minimap visual gates** (#1106) even though its hosted build and selftests have passed. Never auto-merge or represent it as accepted until those gates are actually met.
- Hundreds of new open Draft PRs are stacked source and test increments, not hundreds of independent deliverable features. Some are different Chat1 core and Chat2 gameplay donor branches and must be composed without dropping commits; source HEAD and exact blob evidence matters.
- CI-green combined G21.204/G18.6.5 review train [#2643](https://github.com/Trexzo/SpawnPK-RE/pull/2643) at `304a4da52a56a45364743d5693ebdd4025204c54`, GitHub Actions #38724115499 SUCCESS (511 focused/Java11/full Gradle), is a **certified review candidate**, not shipped main. Later #2645/#2646 are still separate candidates; check current CI/ancestry before promoting them.
- Long-running open issues #2540/#2559 (durable owner epoch/final save), #2587 (post-settlement per-consumer terminal outbox), #2558 (original kill origin authority), #2547 (DMM lifetime), #427 (exact-v308 client UI), #3 (event bus) and #1 (source layout) are substantive or design blockers. Do not mark completed based on test-only negative evidence.
- #2526 coordinates Chat1/Chat2/Chat3; #392 records R25/release path. Keep these standing coordination artifacts visible until a successor with all their functions is in place.

## Disposition decision tree

1. **Retain** canonical PR: if latest exact-head hosted CI passed and its ancestor/donor evidence is complete, designate it in #2526 as the sole selected experimental review train. Never rename selected tests as original server evidence.
2. **Close superseded (UNMERGED)**: only after comparing exact historical HEAD SHA to selected green successor (zero-behind ancestor) OR verifying *each* donor file's content Git blob is reproduced exactly in selected successor. Comment explicit successor, CI and statement that nothing was merged to main. Preserve Git branches and comments. Closing a PR does not automatically close its linked feature issues.
3. **Issue completed**: all acceptance criteria demonstrably pass in a canonical retained implementation **and the policy owner agrees completion does not require main/release integration**, otherwise retain open. Never close major safety/design gap based solely on negative regression evidence.
4. **Issue duplicate/not planned**: only explicit duplicate/subsumed statement with pointer to retained issue that contains the missing requirements, or an intentional scope retirement. Do not use these labels to hide missing user-visible gameplay.
5. **Safe merge**: separately verify cross-module behavior, default-off flags, Gradle/Java11, exact-v308 client authority, real local runtime and human R25 acceptance when required. Avoid importing 300+ stacked Draft commits blindly and never merge frozen #1847 without human authorization.

## Work waves

### Wave A — release-lineage deduplication (started)

- Freeze new promotion assumptions and list all current GitHub issues/PRs (two snapshots above).
- Closed unmerged superseded reviews after explicit ancestry/blob comparison: #2640, #2641, #2642, #2625, #2628, #2630, #2631, #2634, #2636. Historical branches retained. Confirm pending remaining ancestor PRs individually before further closes.
- Keep a green canonical integration PR (currently #2643) open even if newer test candidates are pending; do not replace with a failing future head.

### Wave B — verify and implement still-open core safety gaps

- Audit save owner scope at normal/final/checkpoint/strict/terminal/mailbox, make owner epoch exact and durable only when genuinely provable. No automatic G21.188 ACTIVE retirement or untrusted saved bytes admission.
- Investigate #2540/#2559 end-to-end with real cross-JVM process-kill and filesystem cutpoint tests and operator path. Preserve fail-closed default while unresolved.

### Wave C — gameplay and original/client authority

- Chat2 #2587: independently acknowledged per-consumer replay-safe observer outbox after settlement; no double drop/grant/reward. #2558: recover actual original SpawnPK kill-origin evidence or leave UNKNOWN_SERVER_AUTHORITY. #2547: decide CUSTOM_LOCALLAB objective lifetime, not invented original policy.
- Audit #427 exact-v308 client widget/application gaps with actual source proof. Keep #3 event bus and #1 modular source layout as scoped future architectural epics, not mistaken for completed test milestones.

### Wave D — legacy issues and R25 production acceptance

- For older composition tickets (e.g. #1401), inspect direct accepted original conditions and current source; if embedded in selected reviewed train, link accepted test marker and follow the issue completion policy above.
- Human-test #1106 blocked HOME scoreboard Walk-here and minimap movement on exact R25 runtime; verify release candidate differs from main and preserve rollback. Old #1113 and #1847 branches are not automatically promotion-ready.

### Wave E — release and hygiene

- Collapse review-only descendants when code is already carried in a CI-green canonical branch, retaining donor traceability. Integrate to main only after passing all applicable gates.
- Requery daily creation partitions after each wave and publish fresh open counts. Newly opened PRs/issues change the count while work continues.

## Progress invariant

Every closure must be explained as **merged/completed**, **superseded but unmerged**, **duplicate with retained owner**, or **intentionally not planned**. PR count alone is not the measure of product completeness. Document blockers rather than silently suppressing them.