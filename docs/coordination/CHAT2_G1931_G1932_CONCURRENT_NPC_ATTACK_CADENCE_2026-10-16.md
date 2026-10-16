# G19.3.1–32 — same-session concurrent NPC Attack cadence fence

**Parent:** [G1929–30 #2789](https://github.com/Trexzo/SpawnPK-RE/pull/2789), exact `8c68e23e8baa0ba2fcc6c2ee2993c561f1638936`, independently hosted green **635/635**. Source has exact attacker/World/scene/canonical/lifecycle registration authority for admitted immediate PvM damage. Original server kill-credit question remains [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558).

## Source race
`LocalCanonicalNpcAttackHandler.handle` previously read `attackTick=world.clock().tick()` and compared it against mutable per-handler `nextAllowedAttackTick` **without synchronizing the whole same-session call**. The HP mutation then occurs in a separately protected ownership chain. Two overlapping client opcode72 requests for the same player and the same handler could therefore both pass the old cooldown and each mutate current canonical HP before either published the new cooldown. The fact that the lifecycle write itself is serialized does not serialize the preceding *per-handler* cadence admission.

## Narrow source fix
Make `LocalCanonicalNpcAttackHandler.handle` `synchronized` and synchronize its diagnostic `nextAllowedAttackTick()` getter. This serializes the complete **one-handler** semantic NPC click, cooldown admission, immediate damage and presentation. Existing World/player/canonical/lifecycle locks, hit-range formula, packet codecs, exact attacker generation, combat timing and hitmask semantics remain unchanged. This intentionally does **NOT** introduce cross-handler or cross-session global cadence ownership, prove the production router never creates more than one handler for a session, or establish an atomic settlement/event outbox. No original SpawnPK server cadence claim.

## Deterministic actual two-thread tests
- **G1931**: two separately ISAAC-decoded current-client opcode72 requests for **the same registered NPC**, same `LocalCanonicalNpcAttackHandler` and same tick. First worker is parked in the real pre-resolution hook before HP mutation. Second worker is started and observed `BLOCKED` at handler monitor. Release first; exactly one HIT/10HP and packet65, second returns `CADENCE_BLOCKED` with no HP/mask/reward. After advancing world ticks, third legitimate click lands normally against the same NPC.
- **G1932**: same setup with two **distinct canonical NPC** scene views. First NPC takes exactly 10; second stays at 20 through simultaneous tick because cooldown is per handler, not per target. After clock advance, the other NPC receives its legitimate hit10 and packet65.

Both tests directly assert real current player/canonical registrations, decoded client opcode provenance, no event grant, hook call count, World/handler cadence and output packet mask. Java `CountDownLatch` plus two real Threads orchestrate the overlap. `Thread.State.BLOCKED` is a test-only witness of the new monitor.

Gradle manifest **635→637 unique focused classes**, two dedicated JavaExec tasks. Must pass own exact-head hosted Java11 major55/full Gradle **637/637**, G1931/32 marker pair, and inherited G1925–30 / G21261 gates. Draft/unmerged, main untouched. Cross-handler double hits, terminal credit attribution and durable per-consumer progression ACK remain open.
