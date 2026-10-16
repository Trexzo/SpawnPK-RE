# G21.304/305 — final-review witness post-force content and object continuity

Parent [#2860](https://github.com/Trexzo/SpawnPK-RE/pull/2860), exact `b323f1f886cee9cfd127108f641786429b45e52d`, 721 focused candidate. Latest independently certified cumulative [#2859](https://github.com/Trexzo/SpawnPK-RE/pull/2859) `d01d710180ebca88e59fe73fc23b3e5442bf713c` 719/719 Java11/full Gradle. This is a default-OFF witness operator-evidence improvement, **not** a positive final-save receipt.

## Source-backed gap
`LocalFinalSaveNegativeWitness.publishOnce` successfully creates and fsyncs a write-once witness, but afterward verifies only `NOFOLLOW` regular type, nonnull file key and expected length. An uncooperative actor can rewrite the **same inode** with different but equal-length bytes and restore metadata between file force and verification, yet receive a misleadingly successful operator witness. Likewise it can replace the pathname with a distinct same-sized and byte-identical file; checking only nonnull `fileKey` cannot detect object substitution.

## Changes
- Capture `NOFOLLOW` original pathname `fileKey` immediately after exact CREATE_NEW writing, before file forcing or test hooks; **reject** a substituted postforce object even if bytes/length are identical.
- After both file and parent directory forces, independently read the witness through `NOFOLLOW` into an **expected-size+1** bounded buffer and compare its exact bytes to the just-written record. Reject any same-size mutation or abnormal length. Recheck final pathname object and length once more after read.
- Do not trust hashes as authenticated. No write on error, no cleanup of partial/untrusted witness, no marker retirement, no retry, no account membership or grant changes. Existing admitted World owner and persistence generation gates remain.

## Source-real tests
**G21304**: after file+directory force, modify exactly one witness byte through a real FileChannel on the same inode, force and restore original mtime; expect explicit byte-drift failure, ACTIVE/account bytes unchanged, witness permanent NEGATIVE.
**G21305**: after forcing, move original witness to a sibling and create a distinct inode with the exact same bytes and length; expect explicit identity-divergence error while preserving both negative leaves and ACTIVE; no automatic cleanup.

**Release gate:** parent plus 2 = **723 unique** focused tests; G21304/05, earlier G21298–G21303 and Chat2 G1973–76, Java11 major55 + full Gradle; 23 core CI retention anchors unchanged. All PRs draft, main untouched. Durable cross-JVM owner epochs, authenticated final receipt, automatic review retirement and durable consumer outbox remain unresolved #2540/#2559/#2587.
