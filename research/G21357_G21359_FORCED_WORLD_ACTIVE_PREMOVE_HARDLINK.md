# G21.357–359: no forced World account replacement with aliased exact ACTIVE

Core parent cumulative [#2916](https://github.com/Trexzo/SpawnPK-RE/pull/2916) `6b3b1d367a5191d7c467b3df641b691ee36f5537` 819-test candidate. Earlier [#2912](https://github.com/Trexzo/SpawnPK-RE/pull/2912) 813/813 exact-head certified. Do not infer newer CI certification until successful.

**Source-specific hole:** Opt-in forced World saver already performs pre-ATOMIC_MOVE G21355/56 ACTIVE inode/nonce continuity, but that witness does not compare unix:nlink. An uncooperative process can add a hardlink of selected ACTIVE after capture without altering its pathname fileKey, bytes, size or mtime. Later G21.319 negative-final witness publisher rejects selected marker with multiple links, but that is after account bytes were replaced. Preexisting aliases also should not even allocate World temp.

**Correction:** Reuse the exact existing negative witness `requireSingleObservedLink` NOFOLLOW verification as package-visible helper; only when both experimental forced World postimage and negative witness modes are on. Invoke directly after the early `requireReviewMarker` before temp allocation, and after original pre-move ACTIVE continuity immediately before `replaceSnapshotTemp`. Unsupported unix:nlink is UNCERTAIN and fail-closed, matching witness-side semantics. Retain all late/postmove checks unchanged. No new grant/receipt/epoch.

**Real regressions:** G21357 preexisting selected marker alias fails before temp and retains account bytes+inode. G21358 same canonical selected ACTIVE inode/bytes, new alias introduced at real inside-publication hook, fails before move and retains account bytes+inode. G21359 foreign account ACTIVE can be hardlinked while healthy selected forced World save completes and original selected ACTIVE stays untouched. Tests assert no invented final witness and no unauthorized cleanup.

**CI:** 819→822 focused unique, new critical retention family, individual JavaExec tasks, Java11 major55/full Gradle required. All PRs draft; main unchanged. Durable cross-process owner epoch, authenticated final-save receipt and consumer outbox still unresolved.
