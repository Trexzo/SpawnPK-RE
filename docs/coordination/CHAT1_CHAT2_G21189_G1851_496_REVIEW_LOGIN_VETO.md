# Chat 1 / Chat 2 joint G21.189 + G18.5.1 — opt-in read-only review admission (496 focused)

## Exact source base and owners

- **Certified Chat 2 parent:** [Draft #2588](https://github.com/Trexzo/SpawnPK-RE/pull/2588), SHA `cbbebcc8e8135f9de0dae9d6edb74d87ba86e8bd`, hosted [Actions #38561270563](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38561270563) SUCCESS Java11 major55, **495/495** focused and full Gradle. Includes core G21.188 forced review-sidecar primitive (standalone, not wired), core G21.187 forced-JVM-kill stale BOOTSTRAP negative, Chat 2 G18.4.7–G18.5.1 gameplay-only negative observations. No original SpawnPK reward policy is inferred.
- **Core source:** [Draft G21.189 #2589](https://github.com/Trexzo/SpawnPK-RE/pull/2589), stacked on certified G21.188 `3728a32cbe7a54b18407666b64d1e5a46395ce5f`. Initial CI failed due to Java test-local `java` variable shadowing `java.io.File`; this was corrected in both core and joint branches without changing test behavior. [Core Actions #38562265506](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38562265506) pending at this note.

## Composition

Directly on exact Chat 2 head above, transplant the exact G21.189 helper `LocalReviewIntentAdmissionVeto.java`, real-socket test `G21189CoreSocketExistingReviewMarkerLoginVetoTest.java` and research. `LocalSessionPlayerInitializer.java` and `LocalJvmAccountSessionLease.java` are replaced ONLY after confirming their exact inherited certified blobs `ab8419bc5aa904db686ec597508282045a237e85` and `74f9c665a2dbb1ca8eb42cd749e79d65c4dfa8dd`. Do not edit Chat 2 gameplay, `LocalSession.java`, the G21.188 sidecar writer, World persistence, or `FilePlayerRepository`.

The new flag `-Dspk.local.experimentalReviewIntentAdmissionVeto=true` is **DEFAULT OFF** and requires BOTH earlier opt-in JVM and cross-JVM session lock properties TRUE. Before normal account hydration AND again before World registration, the initializer checks preexisting G21.188 review marker on the exact same account-file path pinned in its owner token. Existing or unsafe marker causes fail-closed login denial; failure of a never-admitted initialization releases ONLY its session token, not the marker. No automatic marker creation or retirement, no final-save durability receipt, no safety across arbitrary dynamic flag changes.

**Regression:** real TCP first-time marked account refuses before BOOTSTRAP, another unmarked account authenticates and saves normally, externally forced review marker later blocks a fresh-JVM real socket login to the saved account without altering account or marker bytes. This is an opt-in **READ-ONLY** marker veto, not complete generic durable account recovery.

## Independent acceptance

Add one new test to current-train manifest **495→496 unique**, dedicated `g21189CoreSocketReviewMarkerLoginVetoRegression`. Fresh exact-head hosted GitHub Actions must show Java11 major55, G21.188+G21.189, G21.187 and G18.4.7/8/9/G18.5.0/1 markers, **496/496 focused**, full Gradle success. Neither certified parent proves this combined tree.

No automatic forced intent BEFORE login, atomic final-snapshot temp-file+directory fsync chain, crash-safe marker retirement, uncooperative RAW/legacy writer exclusion, hostile path replacement/link security, power-loss proven epochs, original graphical v308/v309 client, server kill-credit or reward settlement authority. G21.187 killed-session stale BOOTSTRAP gap remains unsolved for ordinary unmarked sessions. Design [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559), Chat 2 reward [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) and terminal observer [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) stay open. Draft/unmerged; no replay, grants, settlement, cleanup, rollover, pruning, unlink, durable session release or ACK. R25 #1847/G21.87/G21.88 frozen. Chat 3 client untouched.
