# G21.355/356 — fail before forced World account ATOMIC_MOVE on changed ACTIVE

Parent: cumulative PR #2912, exact `aeccf8cc0263b4da09780e6e308fac044e251ee7`, independently GitHub-Actions successful 813-test source.

## Actual gap
`FilePlayerRepository.saveInternalSelected` observed the negative ACTIVE marker before the real `insideWorldPublication` callback, but only checked exact inode, creation time, last-modified time, size and bytes using `requireWorldActiveReviewUnchanged` **after** replacing the selected account in the forced World path. An uncooperative actor may substitute a different inode with the same canonical bytes, or modify the canonical nonce on the original inode and restore metadata. A simple `requireReviewMarker` continues to pass. Post-move uncertainty cannot preserve the previous account.

## Source correction
Reuse existing bounded `G21.275` ACTIVE continuity observation before `replaceSnapshotTemp` under the cooperating `MailboxAccountPublicationCoordinator` lock, when experimental `forceRealWorldPostimage` is enabled. Keep the established postmove check as a separate last-return safeguard. No new authorization or claims about atomic cross-process ownership.

## Actual regression contracts
- **G21355**: replace ACTIVE with a new inode containing exactly the same canonical bytes at the real inside-publication hook. The marker still passes validation; the final World account move must **not happen**.
- **G21356**: change a single nonce hex byte on the original ACTIVE inode, restoring last-modified time while remaining parseable canonical UUID. The final World account move must **not happen**.
- Both require unchanged account fileKey and bytes, retained negative ACTIVE, no final witness, and zero successful forced World postimages.

## Release gate
Manifest 813→815 unique focused tests, preserve existing retention anchors plus explicit G21355/56 anchor, individual Gradle JavaExec tasks, Java 11 bytecode major 55 and full Gradle build. This remains default-off safety hardening; genuine durable cross-JVM writer epochs and authenticated final-save receipt still unresolved in #2540/#2559/#2587. All work remains in a draft PR; main must not be modified.
