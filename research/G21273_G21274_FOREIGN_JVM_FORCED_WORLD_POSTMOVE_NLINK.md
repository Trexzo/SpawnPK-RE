# G21.273 / G21.274 — foreign-JVM new-inode alias during forced World postmove

**Source base:** Draft [#2800](https://github.com/Trexzo/SpawnPK-RE/pull/2800) exact `f92363fda9421b75b4f6095441a18ebe7a4f04da`, 651-test candidate extending independently green [#2798](https://github.com/Trexzo/SpawnPK-RE/pull/2798) 649/649 on `79221990fe8f18f2a44c937237dbc33533f1b0e2`. Existing Chat2 G1935/36 NPC reentrancy, RAW G21.264–70 negative and nlink protections, actual original SpawnPK gameplay provenance are untouched.

## Concrete World-owned publication gap

The World path with experimental `spk.local.experimentalForceWorldAccountPostimage=true` checks G21.230 optional hardlink-count before its account `ATOMIC_MOVE`, then G21.271 postmove negative marker/verified postimage checks. However, once the new account temp inode replaces the old account, a cooperating or uncooperative process can create a hardlink alias to the **new published inode**. Old inode nlink observations and byte-for-byte postimage revalidation do not detect this new alias; the World SaveTicket would complete normally when no review marker appears.

**Production change:** During the existing G21.271 opt-in forced-World postmove validation, repeat the **already default-OFF** G21.230 `LocalJvmAccountSessionLease.requireNonHardlinkedAccount(file)` against the new exact account leaf. Observed nlink !=1 or unsupported filesystem under explicit opt-in turns the real SaveTicket outcome into `G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW`. This is AFTER the account replacement, not a rollback and not a post-crash authenticated final-save ACK. No deletion or mutation of foreign links.

## Real World, real second JVM regressions

- **G21.273:** real World and registered WorldPlayer, `captureAndSave` ticket, real forced on-disk account replacement and directory/postimage verification. Inside a deterministic **after-move** repository hook, an independent Java child creates a hardlink to the exact NEW account inode and validates matching bytes; parent waits for child exit while retaining cooperating publication lock. World SaveTicket must complete exceptionally with postmove uncertainty and hardlink-count cause. Postmove account bytes remain, foreign alias same bytes, unrelated account unchanged, verified World success count zero, no owned World temp leak. Fixture, not writer, deletes the test-created alias.
- **G21.274:** same real World SaveTicket/foreign-JVM seam but child links a completely unrelated account. Selected World postimage must succeed and count one verified; other account and foreign alias unchanged; no global hardlink veto, no owned World temp leak.

Two Java test sources (shared second-process fixture), two Gradle JavaExec tasks, **651→653 unique focused**. Single production file change is `FilePlayerRepository.java`.

## Assurance boundary

This protects only a deterministic observed postmove race window. A malicious foreign process could still create an alias after the final `nlink` probe. Existing opt-in mode still lacks durable cross-process account ownership epochs, immutable final-save intents tied to owner generation, authenticated crash-safe completion ACK, guaranteed power-loss handling, or original SpawnPK server gameplay/loot/reward policy. There is NO authorization to unlink aliases, delete review markers, auto replay/repair/reconnect, or release account ownership based on these observations.

**Gate:** exact-head hosted 653/653 including G21271–74, G21253, G1935/36, Java11 major55 and complete Gradle. Leave draft/unmerged, `main` unchanged.
