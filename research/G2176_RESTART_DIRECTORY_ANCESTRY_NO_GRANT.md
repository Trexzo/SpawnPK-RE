# G21.76 — NOFOLLOW directory-ancestry identity fence for read-only restart witnesses

**Parent:** certified G21.75 `84d0d8aacb9d026546d49f3a00cb4ec26fc2670f`, [Actions #37977510453](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37977510453) SUCCESS, 375 focused Java11. Issue [#2328](https://github.com/Trexzo/SpawnPK-RE/issues/2328).

## Risk

G21.71–75 use `LinkOption.NOFOLLOW_LINKS` for each observed account/sidecar **leaf** object. A symlink or junction in the account path's **directory ancestry** can nevertheless redirect those leaf observations. An uncooperative process can rename an account directory while a G21.73 cooperating FileLock is held and replace the original directory name with a symlink to the moved **same original directory**. Every account and negative sidecar still resolves to the exact same files, bytes, inode keys and POSIX ctime. The G21.72 witness, G21.74 leaf object census and G21.75 change-time comparisons alone can miss the fact that the pathname has become an alias.

## Change

G21.76 introduces `RestartDirectoryAncestry`, a **read-only in-memory ancestry census** performed while the G21.73 bounded account publication lock is held. Walk from the normalized pinned account file's parent up through the filesystem root, inspecting **every component with NOFOLLOW** and requiring a real directory with non-null `BasicFileAttributes.fileKey`. Any symbolic link, missing/not-directory component, inaccessible attribute, or absent fileKey refuses the forensic witness. The census binds each directory's `fileKey` and `creationTime`; it is rechecked after the first full G21.72 witness pass, after the deterministic G21.74 test seam, after the second full pass, and after final classification, before returning a portable token.

Directory **mtime and ctime are not pinned**, intentionally: unrelated account saves in the same directory should not invalidate an otherwise consistent account observation. This is an identity/alias safeguard, not a general file-system modification history. An uncooperative actor who alters directory contents while retaining the same directory inode, a raced alias swap between checks, fileKey reuse, mount or network filesystem identity anomalies, and changes **after** the witness returns remain outside its guarantee. No tamper-proof cryptographic provenance, live transaction COMMIT or physical power-loss durability is implied.

The G21.72 portable token stays **byte-for-byte compatible** across new processes, and does NOT include transient directory inode keys. Direct G21.71 diagnostic and existing regular account load, normal World saves, negative review-marker publication remain unchanged. The existing G21.73 1500ms **lock acquisition** bound and G21.74/75 leaf identity/optional POSIX ctime checks still apply.

## New real-file regression

`G2176MailboxRestartAncestorIdentityIntegrationTest` builds a genuine G21.64 terminal snapshot, a G21.48 stranded intent sidecar and fresh portable tokens. It injects a raw **rename of the account directory and symlink back to that exact same directory** between the witness passes while G21.39 lock is held. Account and marker identities, bytes and ctime are unmodified. The witness must return `G21.76 RECOVERY_DIRECTORY_ANCESTOR_UNSAFE_NO_GRANT`, not a portable token, and an initially symlinked ancestor must also be rejected. After restoring the original directory, the old G2172 portable tokens again compare unchanged. Also tests nested symlink ancestry, missing/unrelated accounts, existing terminal and review-marker restart quarantines, unchanged original live inventory/Mailbox UNCLAIMED, no temp/JVM lock leaks. Test makes the symlink feature requirement explicit; hosted Ubuntu should support it.

**Focused count:** 375→376 Java11; exact-head hosted full Gradle green required for certification. PR stays Draft/unmerged, native C2S185 widget32181 NO_GRANT and frozen R25 #1847 untouched.
