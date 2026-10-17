# G21.374–376 actual locked-session/World pre-ACTIVE admission

Certified baseline [#2938](https://github.com/Trexzo/SpawnPK-RE/pull/2938) exact `f85c3e29d2ac4d9df45a732ddfd3dcfa57b3ba96` **857/857**, both Linux full and Windows source-only, Java11 major55. No regression from Chat 2 donor edits, #2940 independent cross-JVM journal scope untouched.

**Why real path matters:** G21371–73 test `requirePreAdmissionLinkCountCapability` in isolation. This new independent batch constructs the REAL `World.isolatedForTest` persistence, named JVM account token, cooperating cross-JVM OS lock, entire strict negative-witness opt-in configuration, admission review veto, and calls production `LocalOneShotActiveReviewOnAdmission.forceBeforeWorldRegistration`, followed (only after a supported positive) by actual `World.registerPlayer` and token owner binding. This proves check ordering and token continuity, not merely helper semantics.

- G21374 first-time selected account with supported provider => forced ACTIVE exists BEFORE World membership, then actual member; unsupported Windows `unix:nlink` => cause G21371, NO ACTIVE/member/account.
- G21375 existing selected account with exact same-inode hardlink => on supported Unix, strict G21372 denial (generic optional G21230 gate OFF); on unsupported Windows, explicit G21371 denial BEFORE ACTIVE. No World admission.
- G21376 separate foreign account hardlink => healthy supported selected admission and untouched foreign alias; unsupported Windows still refuses before ACTIVE.

All tests release the actual lease without claiming a FINAL save, keep any forced ACTIVE as operator-negative and perform NO marker retirement. Real account generation + binding use the same production interface seen in G21220. Manifest 857→860 focused, critical retention and three JavaExec tasks; Windows source-only workflow runs all three in addition to G21371–73 (it still does NOT run the entire 860).

**Trust boundary:** Windows source smoke remains nlink unsupported and fail-closed; no final-save ACK, globally durable owner epoch, power-loss safe directory transaction, original pinned v308 client 179 parity, or GUI result is asserted. Exact-head Linux **860/860** full build and Windows targeted proof required before certification; `main` unchanged.
