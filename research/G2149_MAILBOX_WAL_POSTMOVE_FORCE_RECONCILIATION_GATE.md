# G21.49 — Mailbox negative durability branch reconciliation (DRAFT)

## Proven parent and source-of-truth boundary
- Parent: certified G21.48-B exact `28468ad080ca29b958736bfd64e443df3a1aad69`, CI #37908586109 SUCCESS, 347 Java 11 focused tests.
- Reconciliation tracking: [issue #2274](https://github.com/Trexzo/SpawnPK-RE/issues/2274).
- Proposed Draft: [PR #2275](https://github.com/Trexzo/SpawnPK-RE/pull/2275). Its newer exact-head CI must pass before any certification.
- Frozen R25 PR #1847 unchanged. This is not a production reward-settlement promotion.

## Which competing branch behaviors were integrated
**G21.48-C (#2273, `64e14a241995692dc4c6211b063023cd9edbb409`):**
Its actual strict World post-ATOMIC_MOVE directory-force failure scenarios were adapted to the B production writer and B write-ahead intent. The two negative B files (permanent G21.47 sidecar and stranded transient G21.48 intent) must persist on postmove UNCERTAIN; a premove failure must preserve original bytes and create neither marker. The original uncertain exception and no receipt remain authoritative. Real World restart/normal-save gates and independent account recovery are regression checked by `G2149MailboxStrictWriteIntentPostmoveForceIntegrationTest`.

**G21.47–G21.48-A (#2263/#2265, `a12d7a2b5956c54e3e5a517064dcfc87371f5393` → `8aff5d7d1ec8ded62572e0d58754e0021a4a88ef`):**
The old independent permanent marker **pathname** `.g2147-strict-postpublication-review` now also vetoes admission via the existing G21.32 marker presence gates, including malformed/symlink cases. Its exact historical **format, account, snapshot SHA and unkeyed SHA-256 checksum** are read in the operator-only forensic inspector. A valid marker can be classified against the observed account snapshot, but never authorizes grant, replay, rollback, admission or marker release. Importantly, the old A marker publisher has **not** been enabled: B remains the one concrete production strict writer path.

**G21.48-B (certified parent):**
Existing `.g2147-strict-uncertain` permanent negative marker and `.g2148-strict-write-intent` transient pre-move marker remain unchanged. `MailboxFencedRestartForensics` now separately classifies both file types as read-only matches, mismatches or invalid/missing evidence with all seven authorizing flags false. The B markers contain an *unsigned/unkeyed* snapshot digest; matching values alone do not establish authenticity or durable settlement.

## Tests and limitations
- Focused Java 11 manifest: **347 → 349** unique registered tests (two new G21.49 integration classes, existing G21.33 tests preserved).
- `g2149MailboxWalPostmoveForceReconciliationRegression`
- `g2149MailboxStrictNegativeForensicsRegression`
- Fail-closed: corrupted, missing, divergent or legacy sidecar evidence is never grounds for automatic recovery, claim or marker deletion. Two reads are not an atomic account+marker snapshot.
- G21.49 checks and CI must be evaluated on the exact latest PR head, not a previously certified 348-test intermediate commit.
- This does **not** prove atomic cross-file writes, true hardware power-loss durability, exactly-once inventory+Mailbox COMMIT, rollback, authorized marker release, uncooperative writers, or native C2S185 widget32181 item granting. **NO_GRANT** stays enforced.

## Pre-promotion rule
Do not merge or promote this Draft PR based on any earlier independent branch's CI, a stale intermediate run, or a read-only forensic "digest match." Reconcile future independently advanced branches against the exact current head and rerun 349/349 with Java 11 bytecode and full hosted build. Human visual/runtime acceptance remains external.
