# G21.277 / G21.278 — independent JVM ACTIVE marker insertion during actual World postmove

**Exact source parent:** [#2807](https://github.com/Trexzo/SpawnPK-RE/pull/2807), cumulative `1575c5dd7833e35db205227858ed2e745478d868`, 663 candidate. Its own hosted exact-head CI remains independent. Parent includes **certified** core [#2805](https://github.com/Trexzo/SpawnPK-RE/pull/2805) at `c657c14879467ee06c0b001aefdb22b0017921f8`, 659/659, Java11 major55 and full Gradle, as well as Chat2 G19.4.1–44 checked/unchecked canonical NPC terminalization and current LocalLab MonsterSpawner settlement tests.

## Source-derived additional evidence

Previous G21.271 tests inserted negative sidecars from a deterministic in-process postmove hook; G21.273/74 ran independent JVMs but inserted HARDLINK aliases, not valid negative account-review markers. G21.275/76 proved the postmove **content-continuity** witness for an old valid ACTIVE marker, including same-inode, size-preserved, restored-mtime nonce edits. This tranche checks an actual **uncooperative JVM** creating a syntactically valid, durably written G21.188 ACTIVE review marker after forced World account `ATOMIC_MOVE`, while the parent still holds its cooperating account-local G21.39 publication lock.

- **G21.277:** real registered WorldPlayer/World `captureAndSave` ticket, actual opt-in forced account replacement, and a forked independent Java process that calls `LocalDurableAccountReviewIntentPrimitive.writeActiveReview` on the selected NEW account inode without joining the G21.39 lock. The child validates the marker, forces its file and parent directory, exits, and parent observes it. Result MUST be a postmove UNCERTAIN SaveTicket with cause `G21.275 WORLD_ACTIVE_REVIEW_MARKER_CHANGED_NO_ACK`; actual account postimage and marker persist; zero false verified success and no World temp leak.
- **G21.278:** the same independent process publishes a valid ACTIVE marker for a DIFFERENT named account in the same exact selected World postmove seam. Selected World SaveTicket must still confirm postimage, foreign account bytes unchanged, valid foreign marker preserved, one verified count; no global account-marker lockout.

**Scope:** two Java test classes (shared JVM fork fixture) and two JavaExec Gradle tasks. Mechanically **663→665 unique focused**. **No production source changes** to `FilePlayerRepository.java`, `LocalOptInRawTemp.java`, Chat2 handler, WorldPlayer, or settlement logic; preserve exact parent code and defaults. No cleanup or replay authority.

## Validation and limits

Require hosted exact-head **665/665** with G21277/78, G21275/76, G21193, G1941–44, Java11 major55, and full Gradle. The actual second JVM proves the expected observation happens despite a cooperating lock, **not** atomic linearizability: a foreign process may still publish immediately after the last probe. It does not establish a durable authenticated owner/generation session epoch, atomic multi-file crash recovery, original SpawnPK server attribution, persistent grant/outbox ACK, positive login rights, or marker release/settlement. Remain draft/unmerged; `main` untouched.
