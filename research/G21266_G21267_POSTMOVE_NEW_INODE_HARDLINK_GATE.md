# G21.266/267 — newly published RAW account hardlink-count witness

**Exact source parent:** [Draft #2794](https://github.com/Trexzo/SpawnPK-RE/pull/2794) `280add7b7ca48546e23b783de376c149339e0909` (643 focused candidate, exact-head hosted CI required). This source contains [#2793](https://github.com/Trexzo/SpawnPK-RE/pull/2793) cumulative 641 tests and preserves all Chat 2 G19.3.4 NPC cadence/lifecycle/gameplay, packet, World and reward logic without modification.

## Actual window

Opt-in G21.230 observes a named account's old hardlink count, and G21.260 repeats that check just before forced RAW `ATOMIC_MOVE`. Once the new temporary inode replaces the account leaf, however, an uncooperative process may create a hardlink alias to the NEW inode. A prior old-account nlink check cannot observe a new file object. The existing postimage fingerprint can remain valid and the routine may complete normally despite new aliases.

**G21.266 production change:** recheck `LocalJvmAccountSessionLease.requireNonHardlinkedAccount(account)` AFTER successful atomic replacement, directory metadata force, exact postimage identity/digest verification and lock-object continuity, but before normal completion. This uses the *existing* default-OFF G21.230 opt-in gate; no defaults changed, no new API/format. If a new inode alias is observed, the existing postmove exception boundary returns `G21.248 RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW`. **This is NOT a rollback**: bytes have already moved and both names can refer to the same now-published inode. Foreign links must never be deleted or repurposed by the writer.

## Regression evidence targets

- **G21.266:** deterministic post-ATOMIC_MOVE `Files.createLink` for the exact new published account inode, exercised separately through primary AccountStore RAW, named LocalAccountProfiles RAW, and direct FilePlayerRepository RAW. Require an uncertain (not successful) writer result with the expected hardlink-count cause. Both account and alien alias retain identical observed postmove bytes; no owned temp/RAW reservation/publication lease leaks. The test, and ONLY the test, deletes its own alias; subsequent opt-in RAW save succeeds.
- **G21.267:** with the nlink gate on, a postmove hardlink alias to a *different named account* does not globally block the selected account's normal publication. Exact selected postimage changes, unrelated account and foreign link retain bytes, and no leaked resources.
- Manifest **643→645 distinct focused classes**; two JavaExec gates, two test sources. Production scope only `LocalOptInRawTemp.java`.

## Authority boundary

This is an observational late gate, **not an atomic or uncooperative filesystem exclusion protocol**. A foreign alias can be introduced after the last check. Filesystem/OS support for reliable `unix:nlink` is required only when this existing gate is explicitly enabled; unsupported environments fail closed as before. These checks do not establish a cross-JVM durable account-owner epoch, authenticated final-save ACK, durable rollback, global account-lock integrity, gameplay authority, or recovery rights. No automatic unlink, marker release, replay or grant.

**Release gate:** independent hosted exact-head **645/645**, G21266/G21267 markers, Java 11 major 55 and full Gradle. Keep drafts unmerged, `main` unchanged.
