# G21.319 / G21.320 — final-review ACTIVE and witness hardlink continuity

Exact parent [#2880](https://github.com/Trexzo/SpawnPK-RE/pull/2880) commit `092b4dfbc47ac4f3b3166e13e5cac76a14317f20`, independently hosted **749/749** CI-certified, Java11 major55 and full Gradle (G1989–1992 NPC coherence). This branch is Chat1-owned account only.

## Previously exposed gap
The default-OFF, one-shot negative final-save review witness is forced and its bytes/inode rechecked (G21304/05); original account and ACTIVE fileKey are verified (G21313/14), and **account** nlink is required to be 1 (G21313). However the selected ACTIVE marker and final-review witness itself were not required to have only one name. A foreign process can add a hardlink to either inode after the bytes were forced; inode identity and SHA256 remain unchanged while the same storage is writable through another name.

## Production mitigation
`LocalFinalSaveNegativeWitness.requireSingleObservedLink` requires current `unix:nlink == 1` on the exact selected NOFOLLOW regular leaf with known fileKey. Fail closed on unsupported `unix:nlink`; checks apply to ACTIVE marker before and after final witness publication, and to newly CREATE_NEW witness before forcing, after forcing and after bounded exact-byte readback. Late alias changes raise G21.319/320 IOException, leave all negative leaves intact; no cleanup/retry/admission/grant/ACK.

## Real tests
- G21319 creates true hardlink alias to the ACTIVE marker inside after-file-and-directory-force deterministic seam, preserves bytes and inode, verifies refusal and retained ACTIVE/witness/account, next login still negative.
- G21320 creates true hardlink alias to selected forced witness, verifies refusal; separate control creates a foreign account alias instead and confirms exact selected witness can complete despite unrelated nlink2. Both are real filesystem operations, not mocks.
- Focused manifest 749→**751 unique**; 69→**71 exact protected critical tests**, two new JavaExec targets. G1989–92 remains undisturbed.

## Limitations
Observed nlink==1 is never an authenticated ownership identity, unbreakable external race fence, durable owner epoch, final-save receipt or positive recovery permission. NOFOLLOW check cannot prevent a hostile writer manipulating the directory immediately after observation. Unsupported filesystem fails in this opt-in negative experiment. The ACTIVE marker is not retired, and the witness remains operator-only manual-review negative evidence. #2540/#2559/#2587 remain open. Fresh exact-head hosted 751/751, G21319/20, Java11 major55 and full Gradle required; draft/unmerged, main untouched.
