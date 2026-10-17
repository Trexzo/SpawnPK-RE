# Chat 1+2 joint core G21.185 / gameplay G18.4.8 integration — 492 focused

## Exact input trees

**Canonical Chat 2 parent:** [Draft PR #2573](https://github.com/Trexzo/SpawnPK-RE/pull/2573) SHA `c76453f03f415110d8027bfaebd10cd8ad12cb7b`, [Actions #38550369592](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38550369592) **SUCCESS**: Java11 major55 (3932 classes), **491/491 focused**, full Gradle 7m47s. Explicit G18.4.7 and G18.4.8 real LocalLab terminal negative observation markers pass. This parent stacks on canonical joint G21.184 [#2572](https://github.com/Trexzo/SpawnPK-RE/pull/2572) certified SHA `952abf4980e23144dfbf1ad06142c79290efe66a`.

**Chat 1 core input:** experimental G21.185 [Draft PR #2574](https://github.com/Trexzo/SpawnPK-RE/pull/2574) SHA `0321a97be2abd90136a55ba9df3fed57ce760f41`, based on certified core G21.184 #2568. At joint-composition creation its [Actions #38550704816](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38550704816) were **PENDING**; previous independent core success cannot be assumed for the new helper.

## File-exact merge constraints

- Base exactly on Chat 2 G18.4.8 head above.
- Preserve Chat 2 gameplay and `LocalSession.java` EXACTLY. In particular G18.4.8 extends the G18.4.7 test with PvM record observer evidence but changes no production record, DMM or Monster Spawner semantics.
- Copy exact G21.185 `LocalCrossJvmAccountSessionLock.java`, `G21185CoreCrossJvmSessionFileLockTest.java`, source research, and full `LocalJvmAccountSessionLease.java` blob from Chat 1 independent G21.185 head. The prior parent `LocalJvmAccountSessionLease.java` blob must match the certified G21.184 inherited version before replacement.
- Add one core G21.185 focused entry and task to Chat 2's 491 manifest; **492 checked unique**. Existing G21.180–184, G18.4.7/8 and all unrelated Gradle tasks must stay verbatim.

## Strictly experimental behavior

G21.185 adds a SECOND DEFAULT-OFF flag `-Dspk.local.experimentalCrossJvmAccountSessionLock=true`, requiring the prior experimental JVM-local lease flag. Cooperating Java processes use a persistent sibling account lockfile and OS `FileChannel.tryLock` for the token lifetime. A released successful (or never-admitted initializer) token frees the lock; a failed or unconfirmed final-save token remains live-process negative. This does **not** provide a durable negative after JVM death (OS releases locks), complete legacy/raw writer exclusion, adversarial unlink/path remapping safety, filesystem crash consistency, or actual two-process socket-session coverage. G21.184 late enable remains a known hazard; both flags must be selected before any admission and not changed dynamically.

G18.4.7 and G18.4.8 merely characterize present `CUSTOM_LOCALLAB` name-reuse G18 DMM and G1 PvM record routing. Their negative source evidence is NOT recovered original SpawnPK kill-credit policy, an authorized reward mutation, or a remediation.

## Fresh joint certification

Independent heads (even when green) do not certify this new tree. Require **exact-head** hosted GitHub Actions: Java11 major55, G21.185 external JVM token admission/denial marker, G18.4.7/8 terminal markers, **492/492 current-train focused**, full Gradle SUCCESS. Keep Draft/unmerged. Do not import or independently merge superseded broad Chat 2 #2567 or duplicate Chat 1 #2571.

No `main` promotion, settlement/grants/replay/cleanup/rollover/pruning/unlink/account recovery/session release/ACK; frozen R25 #1847/G21.87/G21.88 unchanged. Core design [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) stay OPEN. Chat 3 SpawnPK-Client untouched. Shared coordination [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526).
