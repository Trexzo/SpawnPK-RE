# G21.32 — durable negative review fence (opt-in; no reward grant)

**Parent:** G21.31 certified head `a97046c08b631e03311e4cd55305ae665a5d210e`, hosted [37822597696](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37822597696) SUCCESS / 330 focused Java11. Frozen R25 promotion #1847 not touched.

## Restart gap

G21.31 makes actual session hydration fail when an uncommitted G21.22 PREPARED journal accompanies credited inventory / Mailbox CLAIMED. However if the journal is missing while a hypothetical postimage remains, a snapshot-only admission check cannot reconstruct transaction intent. This is not an assertion that somebody deliberately removed the journal; an incomplete/replaced account file, out-of-band operator edit, or uncoordinated write can also lose the state that would otherwise trigger review.

## New bounded opt-in safety primitive

`MailboxDurableReviewFence` writes a **negative, independent, write-once on-disk marker** adjacent to the canonical `FilePlayerRepository` account path: `<account>.properties.g2132-mailbox-review`. The record contains only `REVIEW_REQUIRED_NO_GRANT`, canonical account/message, immutable G21.22 idempotency key, and both **G21.30 canonical complete-account fingerprints**. It is **not** a signed receipt, commit decision or inventory-grant token. It does not contain item-credit instructions.

Before *any disk mutation*, `arm` checks the G21.25 immutable proposal, exact snapshot accounts and version, G21.22 identity, G21.31 valid PREPARED/UNCLAIMED before image and the corresponding G21.25 hypothetical after image. It refuses an existing fence even if that previous write failed after rename; it never overwrites, removes, or clears a marker.

Its filesystem flow is: isolated unique temp in the same directory; canonical bounded ASCII material + SHA-256 integrity checksum (for corruption checks, **not adversarial authenticity**); file `force(true)`; `ATOMIC_MOVE` with **no non-atomic fallback**; directory `force(true)`; successful negative-only receipt. Any post-rename force failure throws `UnconfirmedFenceException`, leaving a possibly visible fence which the actual session loader must still refuse. Pre-rename failures have no successful receipt and clean up temp. The API assumes no concurrent external writers for the same marker and makes no power-loss hardware claim beyond returned filesystem operations.

## Actual production login veto

The existing single-worker `WorldPlayerPersistence.LoadTask` applies an additional G21.32 check **only for a real FilePlayerRepository** and only when `enforceAdmission=true`. This checks marker presence **before and after** `repository.load`, including when the account file does not exist. Any present marker (even malformed or a symlink) blocks session hydration with `IOException G21.32 MAILBOX_DURABLE_REVIEW_FENCE ... action=REJECT_SESSION`. The G21.31 journal-state admission remains enforced separately. Metadata read failures propagate IOException, rather than treating the fence as absent.

G21.26's explicit `observeUntrustedMailboxAccount` forensic reader is unchanged: it can classify untrusted account bytes via the same existing FIFO but can never feed the normal login or grant path. Other accounts without a fence remain unaffected. Repository implementations other than FilePlayerRepository are **outside the G21.32 sidecar contract**, not falsely claimed as protected. This marker is opt-in and is **not automatically created by native widget32181**.

## Test and acceptance

`G2132MailboxDurableReviewFenceIntegrationTest` exercises strict fence metadata, no-grant receipt and SHA checks; valid PREPARED before arming; immediate actual loader + `LocalAccountLifecycle.load` session failure; forensic observation unchanged; stripped journal still blocked; completely missing account still blocked; second account unaffected; fresh World/recreated worker admission veto; malformed marker still blocking login and diagnostic checksum rejection; injected pre-rename failure with no marker/temp; injected post-rename directory-force failure with an existing marker but **no success receipt**; duplicate-arming refusal; zero live Mailbox or inventory credit.

Focused manifest **330→331 Java11**, new task `g2132MailboxDurableReviewFenceRegression`. Hosted exact-head SUCCESS required.

## Still unresolved

G21.32 records only a permanent **negative review obligation**, not a persistently committed reward. It cannot by itself restore accounts, infer settlement success, authorize manual cleanup automatically, or decide how to apply a future reward. There is no supported code path to delete/release a marker. Real WorldPlayerPersistence account-update coordination, crash-durable positive decisions, in-memory live-state reconciliation, item duplication/loss protection, native v308 end-to-end client proof and original SpawnPK policy remain later gates. **C2S185 widget32181 remains NO_GRANT; bank widget32178 excluded.**
