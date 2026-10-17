# G21.298–299 — forced one-shot NEGATIVE final-save operator witness

**Parent:** [Chat1+Chat2 integrated #2854](https://github.com/Trexzo/SpawnPK-RE/pull/2854) exact `c6ad4b2abf8a74d9197e8579e392a50c854d4d5d`, 711 candidate. Independently hosted certified earlier #2851 707/707 and #2852 709/709 (Java 11 major55, full Gradle).

## Why
`LocalOneShotActiveReviewOnAdmission` (G21.190) can force an ACTIVE negative intent before registration, and `FilePlayerRepository.saveForWorld` (G21.192) can force and verify a World postimage, but no separately forced *post-final* observation of the exact account/ACTIVE pair existed. A Future's successful completion cannot be used across a JVM crash as a known bound owner/receipt. This PR adds one strictly opt-in, **untrusted, negative-only** operator record; it does NOT solve a durable authenticated final-save protocol or make second login safe.

## Default-off admission conditions
`spk.local.experimentalNegativeFinalSaveReviewWitness=true` requires all flags before account hydration: G21.181 JVM lease, cooperating RAW-write gate, cross-JVM OS lock and file-object continuity, G21.189 read-only review admission veto, G21.190 one-shot forced ACTIVE, and G21.192 strict forced World postimage. Missing requirements DENY before World admission. Enabling at runtime after admission is unsupported.

## Actual production integration
The real `WorldPlayerPersistence.write` final SaveTask uses the exact captured owner/generation witness; only after `FilePlayerRepository.saveForWorld` returns from forced account temp+atomic move+directory/postimage verification, it calls `LocalFinalSaveNegativeWitness.publishOnce` in the same persistence FIFO before completing `SaveTicket.completion`. The new routine uses the G21.39 cooperating account publication lock to re-read exact selected account SHA and existing G21.188 ACTIVE bytes, requires one observed account hardlink, and forces a new exclusively-created sibling `.g21298-final-review-witness` with account name, captured generation, save sequence, account digest, exact ACTIVE digest and all `*Authorized=false` claims. Force both file and parent directory, recheck physical bytes and marker. Existing, malformed, truncated or after-move uncertain leaves are **permanent negative**; there is NO cleanup, repair, retry, grant, receipt-based admission or automatic release. The existing ACTIVE is NEVER retired.

## Regressions
- **G21298**: genuine TCP session and close; actual World forced account final postimage; final worker publishes durable-fsynced exact-account review witness; verifies marker and account fingerprints; ACTIVE persists and next admission remains refused.
- **G21299**: missing required gates fail admission preflight; occupied untrusted witness leaf refused without overwrite, account/ACTIVE bytes unchanged; zero generation rejected.
- Manifest 711→713 unique focused; individual JavaExec gates; 23 G21.293 critical anchors remain.

**Explicit limit:** this is an untrusted *operator observation* even when file+directory forces succeed. No authenticated owner epoch, crash-safe final ACK, automatic post-restart admission, replay, or grant. Do not call it complete crash recovery. See #2540, #2559, #2587.

**Release gate:** hosted exact-head 713/713, real G21298/99 PASS, Java 11 major55, complete Gradle and independent parent/donor CI. Draft/unmerged, `main` untouched.
