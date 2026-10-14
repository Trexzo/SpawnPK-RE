# Chat 1 G21.189 — optional READ-ONLY existing review-intent login veto

## Exact certified parent

- Chat 1 [G21.188 Draft #2584](https://github.com/Trexzo/SpawnPK-RE/pull/2584), exact parent `3728a32cbe7a54b18407666b64d1e5a46395ce5f`, [Actions #38559459178](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38559459178) SUCCESS Java11 major55, **488/488 focused**, full Gradle 7m24s. G21.188 standalone `LocalDurableAccountReviewIntentPrimitive` writes exclusive NOFOLLOW active review marker, forces file and parent directory, and keeps it through test-only JVM process kill, but has **NO real-login integration**.
- Latest independent Chat 2 certified cumulative [G18.5.1 Draft #2585](https://github.com/Trexzo/SpawnPK-RE/pull/2585), exact `e82910c8743dfd45ffdf69af8c786b084514a0d2`, hosted [Actions #38559892366](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38559892366) SUCCESS 494/494 full Gradle. Canonical joint predecessor [G21.188 + G18.5.0 #2586](https://github.com/Trexzo/SpawnPK-RE/pull/2586) exact `568e4cf27f69e26638c7ef5199e46c208e32463f`, [Actions #38560084894](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38560084894) SUCCESS **495/495** full Gradle. Neither is modified by this core-only branch.

## Exact implementation and scope

Add default-OFF flag `-Dspk.local.experimentalReviewIntentAdmissionVeto=true`, which **requires** both prior opt-ins:
- `-Dspk.local.experimentalJvmAccountSessionLease=true`;
- `-Dspk.local.experimentalCrossJvmAccountSessionLock=true`.

If misconfigured, named persistent account admission fails closed **before hydration**. The code uses the **exact pinned account file path already guarded by the opt-in `LocalJvmAccountSessionLease.Token`**, not a separately re-resolved path, and invokes the G21.188 read-only `requireNoReviewMarker(accountFile)`:
1. Immediately after acquiring the cooperating owner token, BEFORE `LocalAccountLifecycle.load`, starter defaults or any World membership.
2. Again after current `MailboxLateSessionAdmission.requireStillAdmissible` and just BEFORE `world.registerPlayerAndStart`, to veto a review sidecar that appeared during initialization. A failed initializer releases only its NEVER-ADMITTED owner token; it **never** clears or edits the account marker.

Any existing marker is a negative regardless of its content, and IO/NOFOLLOW ancestry errors refuse access. A stable flagged account cannot be admitted through the opt-in path in a fresh JVM merely because its operating-system session lock is free. Feature selection is snapshotted once at the beginning of initialization; **arbitrary system-property changes during sessions are NOT supported**.

The prototype is **READ-ONLY** from admission. It deliberately does NOT create an ACTIVE review marker when a normal session logs in, does NOT retire a marker after healthy logout, does NOT write a durable final-save receipt, does NOT fence nonparticipating/raw/legacy writers, and does NOT make the G21.187 killed-JVM stale BOOTSTRAP scenario safe in general. A marker created between the late admission check and World registration by a noncooperating actor remains a possible race. Thus **the actual G21.187 vulnerability stays unresolved**, except that manually/externally premarked accounts can be denied in this experimental configuration.

## G21.189 actual socket regression

`G21189CoreSocketExistingReviewMarkerLoginVetoTest` exercises a disposable isolated filesystem:
- Forced G21.188 marker on a never-created named profile. Real synthetic 317/ISAAC TCP login must be refused *before* BOOTSTRAP account creation, no World member and no leaked session token.
- A different unmarked named profile must still login via the actual localhost socket, perform a World-owned extension mutation and complete healthy final account save, with no automatic review marker created and zero outstanding token after success.
- After World close, an externally created and fsynced G21.188 marker is placed beside the saved profile. Launch a fresh, distinct JVM and a new actual `LocalSession` socket with both lock flags and the review-veto flag enabled. That login must be denied before hydration and World membership; account and marker bytes remain identical, no token leak, and `src` alias stays absent.

Marker `G21189_REVIEW_ADMISSION_PASS optInReadOnlyMarkerVeto=true defaultOff=true ... crashEpochSafe=false intentCreationOnLogin=false durableFinalSaveReceipt=false`. Dedicated `g21189CoreSocketReviewMarkerLoginVetoRegression`; focused count **488→489 unique**.

## Required hosted certification and nonclaims

Require new **exact-head** GitHub Actions: Java11 bytecode major55, G21.188 forced-marker marker, G21.189 real TCP read-only admission marker, **489/489** unique focused and full Gradle success. Previous parent run is not certification of this branch.

No full failure/recovery durable protocol, no automatic account intent create/retire, no account temp file force + directory force final receipt, adversarial hardlink/symlink/unlink/cross-host guarantee, raw writer exclusion, power-loss cutpoints, graphical SpawnPK client, original server reward/settlement authority. No main merge, grants, replay, cleanup, rollover, pruning, unlink or ACK. Frozen R25 #1847 and G21.87/G21.88; Chat 2 production gameplay and Chat 3 SpawnPK-Client untouched. Core durable design [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), ownership [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559), shared coordination [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526) remain OPEN.
