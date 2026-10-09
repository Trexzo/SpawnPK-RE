# G21.56 — bind live strict World write-ahead and uncertain negative markers to the exact account file

**Parent:** G21.55 exact `39b9e7a5871ec7c05f4903c48d9a91c983cd3591`, [GitHub Actions #37929472994](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37929472994) SUCCESS, 355 focused Java 11; [issue #2288](https://github.com/Trexzo/SpawnPK-RE/issues/2288). Frozen R25 PR #1847 untouched.

## Source-derived safety gap

`StrictDurablePlayerSnapshotWriter.saveInternal` selects the exact account file, checks it against the World repository path, serializes to a same-directory temp, holds `MailboxAccountPublicationCoordinator`'s account lock and ultimately atomically replaces the **pinned** account file. But negative marker operations used independent `resolver.resolve(account)`: initial and under-lock `requireUnfenced`; G21.48 write-ahead intent publication and successful cleanup; G21.47 permanent uncertainty marker publication. If the resolver changes after the initial World path equality check, a marker could be checked or written at a different root than the locked `ATOMIC_MOVE` file. Even though normal FilePlayerRepository path resolution is stable, fail-closed behavior should not depend on that unstated property.

## Bounded correction

For World-backed saves only (`worldFile != null`), derive a narrow `markerResolver` pinned to the previously normalized, verified `file`, rejecting requests for a different account name. Reuse it for all negative review preflight + in-lock veto checks, WAL publication, confirmed-only WAL cleanup and post-move G21.47 uncertain marker publication. The original `file`, World owner-generation checks, file lock and `ATOMIC_MOVE` path remain unchanged. The historical direct `saveStrict` primitive still uses its original resolver without inventing a live-World authorization contract. No extra writers, marker release, reward claim, rollback or repair.

## Deterministic real-World acceptance

`G2156MailboxStrictWorldWalAccountPathIntegrationTest` creates a real World and `FilePlayerRepository` under directory A plus an independent directory B. An adversarial writer resolver supplies the correct A account path on its first lookup but B on any later lookup.

- `AFTER_WRITE_AHEAD_INTENT` injected failure: A transient intent exists **before** any account replacement, original A account bytes unchanged, no B sidecar, new World refuses A account.
- `BEFORE_DIRECTORY_FORCE` injected failure **after** atomic replacement: A account bytes changed and both G21.48 transient intent and permanent G21.47-B review marker exist at A, none at B; fresh World admission vetoes.
- Normal strict PREPARED checkpoint: exact scoped file receipt issued and the same A transient intent is cleared; no B markers created.
- A preexisting G21.47 review fence blocks strict writing to A without creating B markers, even if the writer's subsequent resolver path would have shifted to B.
- Writer resolver consulted once per strict save; unrelated healthy account can load; live player Mailbox remains UNCLAIMED and no inventory credit; no temp file or publication lease leaks.

Current-train focused manifest **355→356** unique Java11, dedicated `g2156MailboxStrictWorldWalAccountPathRegression`; full exact-head hosted `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=356 bytecodeMajor=55` and `BUILD SUCCESSFUL` mandatory before certification.

**Scope:** stronger concrete negative-marker path identity, not an atomic multi-file transaction or positive crash-durable item grant. Uncooperative writers, hardware power loss and exactly-once inventory settlement remain unsupported. Native C2S185 widget32181 **NO_GRANT**. R25 #1847 frozen. Keep Draft/unmerged.
