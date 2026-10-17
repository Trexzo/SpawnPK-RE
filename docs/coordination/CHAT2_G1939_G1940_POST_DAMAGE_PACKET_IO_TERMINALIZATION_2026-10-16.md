# G19.3.9/40 — completion of canonical NPC terminal callback after packet I/O failure

**Exact parent:** [G19.3.7/38 PR #2799](https://github.com/Trexzo/SpawnPK-RE/pull/2799) source merge head `f6f3ac982ac6825528f3ac469f97f20d591ed058` (651 focused candidate). This includes the independently certified 649-test Chat 1 + Chat 2 baseline and G21.270 foreign-JVM handshake repair.

## Observed ordering gap

The canonical click pipeline mutates exact authoritative NPC lifecycle HP, updates player-owned cooldown and then sends viewer-local hitmark/HP packet 65. Prior G1937 preflight rejects a **null** writer before damage. But an otherwise valid `ServerPacketWriter` can reject `varShort` with a checked `IOException` (e.g. the writer became terminal). With newly lethal damage, the old method would exit before `finalizeLethal`: the NPC was dead and cooldown committed but there was **no immediate terminal callback**. A later dead-click/WorldPulse retry could repair this, but no such follow-up is guaranteed after a broken transport.

## Narrow correction

- Attempt packet 65 as before, with the same body, timing, source, accepted-hit witness and authoritative HP.
- Catch **only checked IOException from packet emission**. For newly lethal damage call `finalizeLethal(target)` even if packet I/O failed, then rethrow the original presentation exception if terminal callback returned.
- If the terminal callback also fails with `IOException`, `RuntimeException` or `Error`, rethrow that terminal failure and attach the packet failure using `addSuppressed` so neither failure disappears. Success path unchanged.
- No fabricated packet, remote retry, rollback of already committed HP, extra reward delivery or inferred server kill-credit policy.

## Tests

**G1939:** two actual exact opcode72 targets using a genuinely `markTerminal()` packet writer (not a null or mock writer). Nonlethal 20→10 HP applies despite IOException; same-tick replay is cadence blocked; later valid click may complete. Lethal 10→0 HP applies, mask fails with original IOException, and terminal callback runs exactly once without a retry click. No anonymous reward is inferred.

**G1940:** repeat lethal target with a deliberately failing terminal callback. The terminal exception remains primary and includes the original packet IOException exactly once as suppressed. No second same-tick hit or fake progression receipt.

Focused manifest **651→653** mechanically unique; Java 11 bytecode major55 and exact-head **653/653 hosted CI plus full Gradle** required. Scope excludes runtime crashes before callbacks, original SpawnPK kill credit, durability/idempotency of per-consumer terminal settlement, and external disconnect recovery. Remains a draft source contribution, not a live deployment.
