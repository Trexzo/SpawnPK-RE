# G21.77 — forensic lock-path preflight (NO GRANT)

Base G21.76 exact-head certified SHA `24da2d1e76fdf4d854f812c4c8d3b8a5b77a3414`, 376 focused Java11 tests. Issue #2332.

## Gap closed
G21.76 inspects NOFOLLOW directory ancestry **after** G21.73 bounded advisory FileLock acquisition. Yet that bounded acquisition can create/open a lock file through a parent-directory symlink, or follow a symbolic link used as the lock-file leaf itself. Even if the forensic capture eventually refuses, opening `CREATE,WRITE` at a redirected path is an unwanted side effect.

G21.77 validates each existing lock path ancestor using NOFOLLOW attributes, requiring a genuine directory and nonnull fileKey **before** attempting to create or open the lock file. The bounded forensic path additionally refuses existing nonregular/symlink lock leaves, repeats ancestry and lock-leaf checks after acquiring the per-account JVM mutex, and opens the advisory lock using `FileChannel.open(..., CREATE, WRITE, NOFOLLOW_LINKS)` to reject races on the final component. The ordinary blocking `withExclusivePublication` used by World saves, publication and session gates is **unchanged**. Bounded 1500ms forensic lock acquisition and G21.76 post-lock ancestry checks remain.

The new test `G2177MailboxForensicLockPreflightIntegrationTest` verifies that a redirected symlink parent is rejected before creation of any lock file inside the symlink target, that a symlink lock leaf pointing to a sentinel file is refused without touching the sentinel, that normal missing-account forensic capture/compare still succeeds and that the original blocking writer path remains functional. No review marker, grant, replay, admission or ACK is produced. Test #377 Java11. Requires hosted exact-head full build before certification.

Scope caveat: ancestry checks alone are not an atomic directory-handle traversal on arbitrary filesystems; an uncooperative actor may race ancestor swaps between the preflight and open. This milestone prevents the demonstrated stable symlink and final-component symlink attacks, not every possible namespace race. Read-only witness remains a point-in-time negative forensic tool and never authorizes reward settlement. R25 PR #1847 remains untouched.
