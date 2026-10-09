# G21.60 — admitted account file object identity across World read

**Parent:** hosted-certified G21.59 exact `318511d4f1a80b30393e674b431f25568981592b`, [CI #37935988942](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37935988942) SUCCESS (359 focused Java11). [Issue #2296](https://github.com/Trexzo/SpawnPK-RE/issues/2296). Frozen R25 PR #1847 unchanged.

## Source-derived concurrency gap

G21.58 pins the account *pathname* for World session admission; G21.59 protects the complete read with the G21.38/39 **cooperating** account-local publication lock. An uncooperative writer that ignores this advisory lock can nonetheless atomically replace, create, delete, or mutate the actual file **at the same path** between snapshot decoding and the final veto. A path resolver equality check cannot establish unchanged filesystem-object identity. Likewise, `loadExactFile` historically followed symlink files (for backwards-compatible raw forensic operations), which is unsafe evidence for an admitted session.

## Narrow G21.60 correction

- Within the existing G21.59 account lock, file-backed admitted loads take `BasicFileAttributes` of the chosen account filename **NOFOLLOW_LINKS** before and after decoding, and refuse symlinks and nonregular objects. Missing-before/missing-after is still a legitimate empty-account load.
- Before/after evidence requires consistent absent/present state, file key (when available), size and last-modified time. Changed evidence emits `G21.60 MAILBOX_SESSION_ACCOUNT_FILE_CHANGED ... REJECT_SESSION`. A symlink/nonregular entry emits `G21.60 MAILBOX_SESSION_ACCOUNT_NONREGULAR ... REJECT_SESSION`.
- The admitted decoder opens its chosen account file with `READ + NOFOLLOW_LINKS`; the original `repository.load` still uses its legacy-compatible decoder with normal `newInputStream` semantics, remains **nonadmitting** for raw forensic observations, and is never used to grant settlement.
- The G21.58 four-name negative marker checks, account resolver path check, and G21.59 cooperating publication lock retain their ordering and authority. No code executes automatic settlement, marker deletion, replay, rollback, or profile migration.

## Real World regression

`G2160MailboxAdmittedFileIdentityIntegrationTest` creates actual file-backed World profile directories. It uses the already-existing package-scoped after-decode seam to inject **uncooperative** same-path changes inside the admitted session observation:
- externally atomically replace a valid profile at its original pathname with another file (new file key + size);
- delete the read profile;
- create an absent profile between missing-account decode and final check;
- append in-place bytes to a previously decoded account;
- substitute a same-username symlink to a regular profile stored elsewhere;
- preserve normal stable present and stable missing session loads, and original raw forensic symlink decoding.
- Assert refusal on all injected modifications, no automatic marker release, no temp or publication lease leaks, no reward grant.

Manifest **359→360 unique focused Java11 classes**, dedicated `g2160MailboxAdmittedFileIdentityRegression`. Must pass full exact-head hosted `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=360 bytecodeMajor=55` plus `BUILD SUCCESSFUL` before certification.

**Limitations:** file keys and timestamps are only *bounded evidence*, not cryptographic attestation or durable transaction receipts; not guaranteed on every filesystem, not proof against ABA restoration of matching metadata, hard links or a sophisticated malicious writer, and no power-loss or atomic cross-file guarantee. This does not authorize positive Mailbox COMMIT, inventory delivery, exactly-once reward, replay, rollback, or marker auto-release. Native C2S185 widget32181 remains **NO_GRANT**. Frozen R25 #1847 untouched. Keep Draft/unmerged.
