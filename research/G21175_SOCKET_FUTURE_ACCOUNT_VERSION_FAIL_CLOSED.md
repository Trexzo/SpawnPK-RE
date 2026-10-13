# Chat 1 / G21.175 — real socket unsupported account version fails closed without overwrite

**Certified parent:** G21.174 [Draft PR #2532](https://github.com/Trexzo/SpawnPK-RE/pull/2532), exact `d8b4f5a662859ca9920870f3f6b61e9dd21b6932`, hosted [Actions #38455863661](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38455863661) SUCCESS, Java 11 major55, focused 474/474, full Gradle. Chat 2's standing handoff is [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526). New issue #2534.

## Source-bound server behavior

`PlayerSnapshotMigrations.migrate` rejects a persisted snapshot where `format.version > PlayerSnapshot.CURRENT_VERSION` (current is 2). `FilePlayerRepository.loadExactFile` surfaces this as checked `IOException`. Normal `LocalAccountLifecycle.load` catches load failures and reports `FAILED`, and `LocalSessionPlayerInitializer` refuses to register the player. Existing real-wire tests G21.173/174 prove successful logins and reconnects with valid saved accounts; they never tested a **corrupt/future-version account** at the actual LocalSession TCP boundary. A silent fallback to starter defaults or write-over of an unsupported future version would destroy state.

## Genuine loopback acceptance

New `G21175CoreSocketUnsupportedAccountVersionIntegrationTest` first uses a real TCP synthetic revision317 `LocalSession` login and `World.submit` mutation of real gameplay field `movement.runEnergy=37`. After real disconnect/final save, it checks that actual file-backed snapshot preserved the exact field. Close original World. A TEST-ONLY uncooperative fixture edit changes only `format.version` to `999`; record every byte. New World, new real socket sends exactly the same syntactically valid login frame: no successful login response `2`, no World membership, no secondary `src` profile, no account rewrite. Test-only operator restores exact original bytes; a fresh socket login succeeds in the same new World, rehydrates **movement.runEnergy=37**, logs out and saves cleanly. Check zero leaks and two complete World lifecycles. Source production code, snapshot format and any historical recovery/grant code remain **unchanged**.

The test's manual byte restoration is **NOT** an automated recovery capability or authorization of settlement/cleanup/replay. Protocol fixture is **synthetic**, not proof of original v308/v309 GUI. This test complements G21.174's opaque extension roundtrip by verifying a real schema/gameplay value and future-version rejection.

Acceptance: dedicated Gradle regression, Java11 major55, focused 475/475, hosted full Gradle exact SHA. Draft/unmerged. No change to Chat 2 gameplay or Chat 3 client. No RAW/default/historical/future writer exclusion, atomic multi-file crash recovery, trusted historical signer custody or settlement/replay/cleanup/rollover/pruning/unlink/release/ACK authority.
