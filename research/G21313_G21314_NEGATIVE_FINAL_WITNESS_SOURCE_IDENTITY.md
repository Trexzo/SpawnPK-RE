# G21.313/314 — selected account and ACTIVE inode continuity during negative final-review witness

Parent [#2872](https://github.com/Trexzo/SpawnPK-RE/pull/2872) exact `4802322b02b25a11c1ec79e033c0dbbe851655cd`: **737/737 hosted certified**, Java11 major55 and complete Gradle. Includes Chat2 G1981–84 origin × six-consumer cross-tab and guarded account G21309–12.

## Source-backed gap
`LocalFinalSaveNegativeWitness.publishOnce` already creates/forces a default-OFF untrusted operator review witness and checks its exact NOFOLLOW bytes/filekey (G21.304/305). It also runs read-only account forensic SHA and ACTIVE marker SHA before/after. But selected account file or ACTIVE marker could be replaced with **byte-identical different inodes** after witness file/directory force; the original code compared only SHA256, and could report a stable operator witness. A new extra hardlink on selected account could also appear after initial single-link observation while leaving its SHA unchanged.

## Production correction
- Capture NOFOLLOW regular/non-symlink/non-null fileKey for exact selected account and G21.188 ACTIVE pathname immediately after initial read-only inspection, before creating witness.
- After witness file/parent directory have been forced and witness bytes/identity checked, re-inspect original account and ACTIVE sha plus exact account nlink. Reject selected account fileKey replacement or selected nlink drift from one with G21.313; reject ACTIVE fileKey replacement with G21.314. Revalidate ACTIVE canonical marker after observations. All unexpected/errors leave the already-created witness as NEGATIVE; no cleanup/retirement/retry/positive grant.
- This remains filesystem observation, NOT atomic over multiple pathname checks and NOT a malicious-writer fence, secure cryptographic owner epoch, or final-save ACK.

## Real tests
- **G21313:** deterministic actual post-force test seam replaces selected account with a byte-identical NEW inode (old preserved), then separately adds a real second hardlink with same inode/content. Both must reject; exact account/marker bytes unchanged; retained witness still vetoes admission.
- **G21314:** deterministic post-force seam replaces ACTIVE marker with byte-identical NEW inode (old preserved). Original code's SHA-only check would miss it, but revised code rejects; both originals and account remain intact.
- Gradle focused train **737→739 distinct**, two individual JavaExec regressions, G21.308 49 must-retain exact class names retained.

**Release criteria:** exact GitHub-hosted head 739/739, G21313/314 markers, Java11 major55/full Gradle; full parent 737 independently verified. Keep draft/unmerged and main unchanged. Durable account epoch/reusable login/positive receipt #2559/#2540 and consumer outbox #2587 still open.
