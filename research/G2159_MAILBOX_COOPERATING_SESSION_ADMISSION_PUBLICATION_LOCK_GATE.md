# G21.59 — serialize admitted World reads with cooperating account and marker publishers

**Parent:** certified G21.58 exact `95a41775c5f5f5f28592934618e5acaf8e87d197`, [CI #37933811187](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37933811187) SUCCESS (358 focused Java11). [Issue #2294](https://github.com/Trexzo/SpawnPK-RE/issues/2294). Frozen R25 PR #1847 unchanged.

## Concrete concurrency gap

G21.58 pins negative marker checks and account snapshot decoding to the same file path and rechecks the PathResolver before returning. Nevertheless, `FilePlayerRepository.loadForWorldSession` did not coordinate with `MailboxAccountPublicationCoordinator.withExclusivePublication`, the existing G21.38/39 account-scoped JVM monitor and advisory OS file lock. Cooperating World saves, strict account checkpoints and negative marker publishers can therefore run while the admitted snapshot/sidecar evidence is being observed. Correct file *path* alone cannot establish consistent ordering between a cooperating account replacement and the accompanying negative marker.

## Narrow live admission correction

The G21.58 admitted file-backed World loader now wraps its complete first negative-marker veto, exact-file snapshot read, deterministic after-read test seam, second negative-marker veto, and last account-path identity check in `MailboxAccountPublicationCoordinator.withExclusivePublication(file,...)`. The selected `file` and account-scoped marker resolver are unchanged. A cooperating save or review publication at the same account location necessarily happens **before or after** the full admitted observation, never inside it. Independent accounts retain separate locks. Existing socket-thread review vetoes, G21.35 last-minute session-registration check, G21.31 prepared-journal quarantine, and G21.56/57 writer authority remain intact.

Raw `FilePlayerRepository.load` stays nonadmitting and unlocked for read-only forensics. Under this changed lock contract, inherited G21.58's post-read marker test seam deliberately places an **uncooperative** malformed sidecar directly at the pinned path rather than re-entering the same FileLock. Its after-read presence must still fail closed. This does not suggest uncooperative writers are generally serialized.

## Deterministic regression

`G2159MailboxCooperatingSessionReadPublicationIntegrationTest` uses a real file-backed World and a bounded five-thread executor:
- **Read first, account save second:** hold the admitted reader inside the lock after old account bytes are decoded. A queued normal World save reaches its pre-lock frontier but cannot replace the file until read releases. Reader returns old snapshot; subsequent session sees the saved replacement.
- **Writer first, admitted reader second:** hold normal World save inside publication, then initiate session load. The reader cannot finish while writer holds the lock and subsequently sees the new account snapshot.
- **Marker first, admitted reader second:** publish a genuine G21.47-B permanent marker inside the account lock and pause before release; the reader must wait and deny hydration once publication completes.
- **Reader first, marker publisher second:** hold session read, queue a genuine cooperating permanent review publication; marker is not visible until reader finishes, and the following World load is quarantined.
- **Unrelated account:** complete an independent admitted repository read while another account's read holds its own lock.
- Confirm distinct negative marker persistence across a fresh World, no automatic marker clearing, and no temporary-file or JVM publication lease leaks.

Focused train **358 → 359** distinct Java11 tests, dedicated `g2159MailboxCooperatingSessionReadPublicationRegression`. Require exact-head hosted CI `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=359 bytecodeMajor=55` plus full Gradle `BUILD SUCCESSFUL` before certification.

**Boundary:** account-local lock serializes *cooperating* in-process and advisory lock-aware cross-JVM publishers only. It is not an atomic multi-file filesystem snapshot, an admission/registration lock held forever, protection from uncooperative direct writes or malicious same-file replacement, a hardware power-loss receipt, replay, rollback, positive inventory COMMIT, or exactly-once reward delivery. Native C2S185 widget32181 remains **NO_GRANT**; frozen R25 PR #1847 untouched. Keep Draft/unmerged.
