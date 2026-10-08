# G21.43 — reject stale, whole-account PREPARED snapshots at file-backed strict barrier admission

**Certified parent:** G21.42 exact commit `bd2440e58e1aeb3dae713c26a32197d4f643a9d3`, hosted [#37840023485](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37840023485) SUCCESS, 341 focused Java11. Frozen R25 promotion PR #1847 untouched.

## Concrete admission defect

Before G21.43, `WorldPlayerPersistence.submitPreparedStrictBarrier(owner,generation,snapshot,strictWriter)` checked the submitting owner/generation, account identity and that a caller-provided snapshot had `PREPARED_NO_GRANT` in the G21.22 extension, but **not that this complete caller-provided snapshot still matched the currently owned WorldPlayer**. Even with G21.42's file-backed strict publication lock and marker veto, a PREPARED snapshot captured *before* later movement, pet, cosmetic or extension changes could be submitted to the strict queue and replace newer live state with outdated account bytes. Locking the file at publish time does not itself certify the freshness of its input snapshot.

## G21.43 bounded fix

For a **concrete `FilePlayerRepository`**, while holding the existing `owner.mutationLock()` and after the exact owner/generation/account identity gate, but **before** synchronizing on persistence I/O, assigning a barrier sequence or installing a `strictPreparedAccountCutoffs` entry:

1. Normalize/validate the full submitted snapshot using `PlayerSnapshotCodec.validateAndNormalize`; require the version, full sorted field map and `MailboxPreparedRestartAdmission.inspect(...).state==VALID_PREPARED_UNCLAIMED` to match the canonical G21.22 PREPARED journal and unclaimed Mailbox envelope. A merely superficial PREPARED state is insufficient.
2. Capture a **fresh complete snapshot** from the currently owned WorldPlayer under the same mutation lock: `PlayerSnapshotCodec.capture(account,owner,PlayerSnapshotCodec.accessoryItem(snapshot))`. This includes gameplay state, all extension namespaces, mailbox and inventory state, not just Mailbox intent keys.
3. Compare exact G21.30 canonical SHA-256 snapshots. Reject any mismatch with `G21.43 STRICT_PREPARED_STALE_LIVE_OWNER_SNAPSHOT` before enqueuing a worker task or touching disk. Invalid canonical/journal state fails with an explicit G21.43 denial. No fallback or automatic replacement with the caller's stale snapshot.
4. A newly recaptured, still valid PREPARED snapshot from the actual player may pass and use the unchanged G21.42 strict file-backed write, lock, and marker veto.

Historical strict barriers backed by **custom/non-file PlayerRepository adapters** retain their older semantics, including the G21.24 intentionally custom blocked-repository regression. This is a narrow *concrete file-backed admission guarantee*, not a new general custom repository contract, and preserves all historical test authorities.

## Deterministic verification

`G2143MailboxStrictCurrentOwnerAdmissionIntegrationTest` creates three real WorldPlayers with staged G21.22 PREPARED journals and file-backed accounts:
- Initial strict proposal matches the current World-owned complete account.
- Mutate movement after snapshot planning, without changing the journal; the stale strict snapshot must fail **synchronously before queue admission**, leave the previously stored account bytes untouched, and not add queued worker tasks.
- Re-capture the current PREPARED snapshot and verify the G21.42 strict writer accepts it, returns a matching G21.30 receipt, persists the new full state and does not create a review marker. Resubmitting the older snapshot must remain denied.
- Keep the superficial PREPARED state but corrupt its G21.22 intent key; no forged strict snapshot may be admitted or modify disk.
- Unchanged independent PREPARED account passes; other accounts' files remain intact; live Mailbox rows stay UNCLAIMED with zero inventory credit, no temporary files, no marker release/grants and no JVM publication lease leaks.

Focused Java11 manifest **341→342**, new task `g2143MailboxStrictCurrentOwnerAdmissionRegression`. Hosted **exact-head CI SUCCESS** required before certification.

## Explicit residual limitations

This check proves exact-state congruence **at admission**, under the current owner mutation lock. It does not hold that lock across the asynchronous persistence worker lifetime; game state could still change after enqueue before the strict checkpoint completes, nor does it atomically coordinate external processes' memory mutations. G21.42's final file-publication lock/marker veto continues to apply. A future owner epoch/snapshot pin or quiescence protocol would be needed to strengthen the lifetime guarantee without blocking World ticks on disk I/O.

The implementation does not call native C2S185 widget32181, deliver items, acknowledge Mailbox claims, replay journals, automatically roll back, or release review markers. **NO_GRANT** remains the only authority; hardware crash/power-loss positive settlement remains unproven. Frozen R25 #1847 untouched.
