# Chat 1 + Chat 2 G21.191 / G18.5.2 — forced account postimage cutpoints (498)

## Independently certified parent and scope

Exact parent is cumulative Chat 1/2 [G21.190 + G18.5.2 Draft #2594](https://github.com/Trexzo/SpawnPK-RE/pull/2594), SHA `4211f07162c2bb494db23fa625856d0f711fb3f6`, [Actions #38565757908](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38565757908) completed **SUCCESS**: Java11 bytecode major55, G21.190 actual three-JVM process-kill marker PASS, Chat 2 G18.5.2 consumer replay-asymmetry marker PASS, **497/497 unique focused**, full Gradle 7m50s.

The new Chat 1 G21.191 standalone [Draft #2596](https://github.com/Trexzo/SpawnPK-RE/pull/2596), exact new source branch `core/g21191-forced-account-snapshot-publish-primitive`, adds `LocalForcedAccountPostimagePrimitive.java` and external-JVM cutpoint test `G21191CoreForcedPostimagePublicationCutpointTest.java`. Copy those exact source blobs and research from that branch (do NOT reimplement independently).

## Exact composition

- Preserve parent Chat 2 gameplay G18.4.7–G18.5.2 and `LocalSession.java`/World/World persistence/repository/lease code **without change**.
- Add two previously absent Java files for an UNUSED file-publication helper and test. Add existing research and this coordination note.
- Add exactly one test to Gradle manifest and its dedicated task: **497→498 unique current-train focused**.
- Chat 2 G18.5.3 [Draft #2595](https://github.com/Trexzo/SpawnPK-RE/pull/2595) is separate and at integration creation was not certified. Do not import it without an independent green exact-head; it changes an already registered G18.4.7 test class and would **not** increase count.

## G21.191 constrained capabilities

This helper writes bounded raw account postimage bytes to an exclusively allocated unique sibling temp, `FileChannel.force(true)` on the temp, revalidates NOFOLLOW regular-file identity/digest, **REQUIRES ATOMIC_MOVE with NO fallback**, forces containing directory, verifies exact final bytes/digest independently, then returns a detailed receipt.

Actual child-JVM crash cutpoints:
1. Kill writer after forced temp but before rename. Parent and a fresh independent JVM continue to observe OLD account bytes; temp is retained only for private offline evidence, no receipt issued.
2. Kill writer after rename but before directory force. Parent/fresh JVM may see NEW bytes on the running OS, but there is **NO durable receipt**. No power-loss persistence claim.
3. Fully healthy writer forces temp and directory and verifies independent exact account bytes; new JVM reads NEW bytes. A helper receipt describes only these performed operations in that controlled process.

**ABSOLUTELY NO INTEGRATION** into `FilePlayerRepository.saveForWorld`, `WorldPlayerPersistence`, actual account login, or G21.190 forced ACTIVE review sidecar retirement. No protected player/session epoch or raw/legacy writer fencing. The G21.190 one-shot ACTIVE marker remains a permanent refusal even after healthy final save; the default flags remain OFF. The prototype is not a safe general reusable account lifecycle nor proof of disk power-loss safety.

## Required verification

Require brand-new exact-head hosted Java11 major55, G21.190, G21.191 and G18.5.2 markers, **498/498 focused**, full Gradle SUCCESS. Neither separate ancestor's green test result proves this combined source tree. Keep Draft/unmerged.

Core durability [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), owner model [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559), Chat 2 progression receipt [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remain OPEN. No automatic account conflict resolution, marker retirement, original SpawnPK kill reward/settlement, client v308 GUI parity, grants/replay/cleanup/rollover/pruning/unlink/durable ACK. R25 #1847 G21.87/G21.88 frozen and Chat 3 client repository untouched.
