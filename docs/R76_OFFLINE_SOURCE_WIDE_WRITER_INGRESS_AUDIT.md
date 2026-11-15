# R76 — Offline source-wide writer-ingress inventory and negative-only admission review

## Goal

R75 proved that an R74 matching final-save ACK, canonical account, R63 ledger and R64 RELEASED owner **cannot justify retiring R73's durable write-intent marker** while other writers are not fenced. A separate JVM can write via `FilePlayerRepository` even while R60 is locked, and the direct R64/R63 offline helpers can bypass R73.

R76 introduces a repeatable **source-wide, fail-closed inventory of persistence and filesystem call sites**, checked against an explicitly reviewed manifest in GitHub. New or moved write-capable entrypoints now cause test failures and require manual source review. The source inventory is deliberately **negative-only**: a matched baseline is *not* a proof that all writers use R73 and never authorizes retirement, login, recovery, replay or marker cleanup.

## Scope and counted observations

The scanner `R76OfflineWriterIngressInventory` examines Java sources under `server/src/spk` (excluding `*Test.java` fixtures) and records selected executable-looking calls after masking line comments, block comments and string/character literal contents. It recognizes:

- `AtomicAccountPublisher.publish`, the common low-level atomic publisher used for canonical accounts, generations, sessions, journal, evidence and auxiliary storage
- `Files.write/writeString/move/copy/newOutputStream/newBufferedWriter/newByteChannel/delete/deleteIfExists/createTempFile/createFile`
- Receiver `.save`, `.saveQuiet`, `.saveIfCurrent`, `.saveIfCurrentUnderLock`, `.saveOwned`, `.saveOwnedUnderLock`, `.replaceOwner`, `.replaceOwnerUnderLock`, `.releaseOwner`, `.releaseOwnerUnderLock`
- `new FilePlayerRepository`, `FileOutputStream`, `FileWriter`, `RandomAccessFile` and `PrintWriter`

An ordinary application or UI `.publish` is **not** treated as a file write just because of its method name. The inventory tracks specific `AtomicAccountPublisher.publish` instead.

The snapshot in `docs/R76_WRITER_INGRESS_BASELINE.tsv` contains **80 source call occurrences**, including **13 classified `UNFENCED_ACCOUNT`**. Each row pins its source path, line, syntactic call and manually reviewed scope classification. Two different `Files.move` occurrences can appear on one line; these are intentionally counted as a multiset and NOT silently deduplicated.

Risk categories:

- **UNFENCED_ACCOUNT**: real/default or historical account-writing routes and the shared atomic file publisher, including `WorldPlayerPersistence`, `LocalAccountLifecycle`, `FilePlayerRepository`, `AccountStore`, `LocalAccountProfiles`, `PetAccessoryPersistence` and default `World` repository construction
- **COOPERATIVE_NO_R73**: R57/R59 journal and trade files, R61/R62 trade replay/completion, R63 account generation, R64 owner save/transfer; some acquire R60, but that does **not** imply R73 intent coverage
- **TEST_EVIDENCE_ONLY**: R65/R69/R70/R71/R72/R73 offline-only authorization/evidence seams, which are not installed by the default runtime
- **NONACCOUNT_REVIEWED**: ancillary resource extraction, local developer/config tools, OS lock-file creation and non-account marketplace/UI operations. This category documents their present usage, NOT an assertion that future changes are safe
- **UNREVIEWED**: any newly discovered sink in an unclassified source file. This category automatically blocks the exact-baseline acceptance even if someone adds its row to the manifest

The inventory is not an exhaustive transitive dataflow or Java semantic analysis. It recognizes selected simple receiver-method patterns and constructors; **reflection, JNI, dynamically dispatched aliases, nested receivers, method references, non-Java code, arbitrary external programs and unexpected custom persistence APIs can bypass this lexical scan**. The tests do not prove that these absent classes of writer are impossible. A human-maintained classification is not a cryptographic guarantee of behavior.

## Enforcement

`R76OfflineWriterIngressInventoryTest` is run from `server` and checks the full source tree against `../docs/R76_WRITER_INGRESS_BASELINE.tsv`:

1. The emitted source callsite multiset and the checked-in baseline **must match exactly**. A moved line, added writer, removed entry or changed category is reported as `BLOCK_INVENTORY_DRIFT` and fails the regression, requiring explicit review.
2. Baseline row format is validated; duplicate rows are **counted**, not reduced to a set.
3. A new source file with `repository.save` and `Files.write` is recognized as `UNREVIEWED` and blocked. The scanner ignores the same phrases in comments/strings, preventing misleading matches.
4. A new call placed in a previously known account-writer file still invalidates the pinned source baseline; a known filename does not automatically approve new writes.
5. Missing, malformed or forged baseline entries and missing source roots cause denial.
6. Every outcome, including an exact reviewed baseline, returns `retirementAuthorized=false`, `loginAuthorized=false`, `recoveryAuthorized=false`, `replayAuthorized=false`, `allWritersFenced=false`, `sourceInventoryComplete=false` and `modifiesEvidence=false`.

The manifest is **source-review evidence only**, not a deployable writer lease or permission token. The observed source baseline will intentionally drift when future work changes tracked paths; that is an invitation to re-audit, not a reason to automatically regenerate an 'approved' manifest.

## Local verification — November 15, 2026

Isolated Windows worktree: `SpawnPK-R76-Writer-Ingress-Audit-20261115`, with only the R76 GitHub branch checked out; the original Java game processes and live account directories were not accessed by the test.

JDK21 `gradlew.bat jar testClasses verifyJava11Bytecode --offline --no-daemon`: **BUILD SUCCESSFUL**, **2,318 compiled major-55 Java 11 classes**.

`R76_OFFLINE_WRITER_INGRESS_INVENTORY_PASS` confirms the 80-entry source-wide baseline, 13 unfenced account-facing occurrences, new/unreviewed sink rejection, source-line drift, forged/duplicate manifest denial, comment/string negative controls, and non-authorizing outcomes. Inherited R75–R69 Windows regressions are run from the same isolated worktree, with the full R55–R76 chain reproduced independently by dedicated Linux and Windows GitHub Actions.

## Open safety gaps

1. **Live writes remain unfenced.** The default `WorldPlayerPersistence` path still calls `repository.save`, and R76 changes no live session, game logic, Java threads, accounts or network behavior.
2. **The lexical scanner is not exhaustive.** It cannot certify every possible account writer, cross-language API or unknown future library. A matching manifest says that *the tracked patterns* still match their reviewed snapshot.
3. **R73 blocker remains permanent.** No R73/R72 marker cleanup, deletion, retirement, replay, timed expiry, manual override or account admission is implemented.
4. **No storage crash-atomicity guarantee.** The account, R63 generation, R64 owner, R73 intent and R72 phase markers remain separately published. R76 does not solve torn or partial multi-file publication, parent-directory fsync or hardware power loss.
5. **No authentication or all-writer quiescence.** R60 protects cooperative writers only; no durable guarantee covers external or delayed uncooperative filesystem operations.
6. **No automatic trade recovery.** R57/R59 pending paired intents still block account admission.

## R77 priority

Turn the inventory into a **scoped, test-only writer-admission map** that validates distinct canonical-account and auxiliary-file writer families against a single explicit session-owner/commit-epoch contract. First prove negative controls against direct default persistence, legacy account profile writes, accessory merges, trade replay, and owner transitions. Do not enable any real hooks or marker retirement until the default runtime can authenticate session issuance and every canonical account writer participates.

**R76 DRAFT, stacked on R75, UNMERGED.**
