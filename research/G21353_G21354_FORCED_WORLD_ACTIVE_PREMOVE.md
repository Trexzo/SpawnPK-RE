# G21.353-354 genuine forced World save ACTIVE pre-move refusal

Certified cumulative ancestor PR #2906 exact 1eae3b4bd8c4de59a47df36d581cc8f33f903d77 passed 803/803 Java11 major55 and full Gradle. Parent PR #2907 37ce019a08d5af183097e1424c869d6193019b8e adds G21349–52 late alias controls; its independent CI must pass.

Actual source gap: experimental G21.298 WorldPlayerPersistence.write calls FilePlayerRepository.saveForWorld BEFORE LocalFinalSaveNegativeWitness.publishOnce. The guarded forced World postimage path treated a missing ACTIVE review record as null, so uncooperative/manual removal before publication could allow account replacement, with only later witness creation throwing (postimage already altered).

New default-OFF production behavior: only when BOTH opt-in strict forced World postimage and experimental negative final-review witness are enabled, require existing canonical G21.188 ACTIVE on the pinned exact account path before allocating temp; again under cooperating account publication lock; and immediately after the existing deterministic insideWorldPublication seam, before account ATOMIC_MOVE. No new marker creation, positive release, override, replay, or legacy RAW behavior change.

G21353 real file-backed guarded repository with missing ACTIVE must refuse before serialized World temp or replacement; original account postimage unchanged. G21354 valid ACTIVE deleted during the actual under-lock publisher test seam must refuse before move and preserve original bytes. Existing real TCP G21298 protects healthy normal path. Focused 807→809 distinct, 2 new G21.293 mandatory anchors (123→125) and dedicated JavaExec tasks.

Gate: independent exact-head 809/809, both new PASS markers, G21298, G21349–52, Java11 major55/full Gradle. Keep draft/unmerged, main untouched. This narrows negative-only safety but cannot prove full atomicity against external writers, authenticated cross-JVM epoch/final receipt, automatic ACTIVE retirement or idempotent rewards. Issues #2540 #2559 #2587 remain unresolved.
