# G21.34 — exclusive negative Mailbox review-fence publication

**Certified parent:** G21.33 commit `c2a6bb081ad719338db5b455b6bbaf535d8a645a`, hosted [workflow 37826228249](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37826228249) SUCCESS / 332 focused Java11. Frozen R25 PR #1847 remains untouched.

## Unsafe assumption closed

G21.32's `MailboxDurableReviewFence.arm` was opt-in, non-granting and synchronised on one Java object, but its final `Files.move(temp,marker,ATOMIC_MOVE)` did not guarantee **no replacement if the destination already exists**. The Java NIO `ATOMIC_MOVE` contract explicitly leaves replacement behavior implementation-specific. Separate writer instances or JVM processes can both see no marker and race during publication. Replacement is forbidden for a durable review obligation; the second write could overwrite the first marker's canonical G21.30 before/after snapshot fingerprints and intended G21.22 Mailbox transaction identity.

## G21.34 exact change

`MailboxDurableReviewFence.arm` now serialises and **forces** its private same-directory temporary file exactly as before. At the existing G21.32 `BEFORE_ATOMIC_REPLACE` fault point it performs a **single exclusive hard-link publication**:
```java
Files.createLink(finalMarker, temp);
```
On file systems supporting native hard links, creation of the new final filename is a no-clobber operation: an existing marker, whether valid, damaged or a symlink, returns a `FileAlreadyExistsException` instead of replacement. After successful publication, the temporary alias is deleted **before** forcing the parent directory, covering both final link creation and temporary link removal in the returned filesystem force boundary.

`ATOMIC_MOVE` and `REPLACE_EXISTING` are **not** used, and no fallback to nonexclusive move/copy is permitted if the filesystem does not support hard links. That provider fails **closed** with no success receipt. A failed/uncertain postpublication delete/force is reported as `UnconfirmedFenceException`; any surviving final marker still blocks login on G21.32's real file-backed account path. Prior mark/no-grant checks, marker payload/digest, G21.31 login refusal and G21.33 read-only forensics are unchanged.

This protects against competing uses of the G21.34 writer on a supported filesystem, **not arbitrary external deletion/editing**, filesystem metadata durability beyond returned API calls, or coordination with the game's account/reward transaction writer. It is not a proof of hardware power-loss recovery. Marker arming remains **opt-in**, not attached to native C2S185 widget32181. There is still no marker release API and no positive reward grant.

## Deterministic integration

`G2134MailboxReviewFenceNoClobberIntegrationTest` creates two **different** writer instances for the same account, each blocked at the exact old publication fault point, so both *already completed* the optimistic marker absence check. It then releases them simultaneously. Only **one** may return the negative-only receipt; the other must encounter `FileAlreadyExistsException`. The surviving original marker record must parse and match the canonical proposal, reject re-arming, retain identical raw bytes after collision and continue to deny the session's real `WorldPlayerPersistence.load` even after fresh World startup.

Other deterministic cases: injected prepublication fault leaves no marker, postpublication force failure yields unconfirmed outcome while marker blocks account, pre-existing poisoned marker cannot be clobbered, unaffected unfenced account remains loadable, no orphan temp links under ordinary outcomes, G21.33 forensic classification remains non-granting, and live inventory/Mailbox remain unchanged.

Focused manifest **332→333** / Java11; new task `g2134MailboxReviewFenceNoClobberRegression`. Hosted full exact-head CI SUCCESS is mandatory before certification. Existing 332 regressions must remain intact.

## Not proven

A returned negative-fence receipt is **not** an item delivery grant, a committed transaction, or a restart-safe automatic resolution decision. Cross-process account-write arbitration, power-loss hardware guarantees, key-authenticated evidence, original SpawnPK server policy, live recipient migration and exactly-once inventory+Mailbox semantics are still unresolved. Native claim widget32181 stays NO_GRANT; bank widget32178 remains outside this milestone. No existing G21.32/G21.33 tests or owner authority were weakened.
