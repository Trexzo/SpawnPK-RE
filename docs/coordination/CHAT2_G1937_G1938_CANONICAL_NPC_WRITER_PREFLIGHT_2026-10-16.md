# G19.3.7/38 — canonical NPC damage must preflight required outbound writer

## Parent and scope

Exact parent: [Chat 1/2 cumulative PR #2798](https://github.com/Trexzo/SpawnPK-RE/pull/2798) at `b820377383cadf2566bafd1599c339ecdc0fbac7`, 649 distinct focused candidates. It integrates Chat 1 G21.269 account work with Chat 2 G19.3.6 callback-reentry protection. The parent CI needs independent certification.

## Identified source-order bug

In `LocalCanonicalNpcAttackHandler.handleUnderPlayerCadence`, the exact semantic ATTACK path performed these operations in order:

1. Validate scene/target/lifecycle, player generation, distance and timing.
2. Run `beforeResolution` and apply **authoritative NPC lifecycle HP damage**.
3. Publish `WorldPlayer.canonicalNpcAttackNextAllowedTick` cooldown.
4. Invoke `NpcRegistry.sendMaskLocal` with `Objects.requireNonNull(writer, "writer")`.
5. Fire lethal-finalization callback on newly dead target.

A null packet writer is a deterministic local caller error. Previously, it throws only at step 4 — **after HP and cadence change** and before finalization. This is particularly dangerous for lethal damage: the NPC can be marked dead without the corresponding immediate terminal callback, requiring later WorldPulse repair.

## Narrow fix

Move `Objects.requireNonNull(writer, "writer")` to immediately after all no-damage/rejected semantic request gates (including `CADENCE_BLOCKED`) but **before** `beforeResolution` and `resolution.resolveImmediateOwnedAdmitted`. Remove the duplicate late null check. No new fallback packet writer, no suppressing exceptions, no change to original client opcode/packet representation, no change to local damage formula, hitmask or callbacks.

This only preflights **null writer**. A real writer may still throw during actual encoding/network I/O after HP mutation. That separate failure-atomicity and terminal-outbox question remains unresolved. Do not imply it is fixed.

## Regressions

- **G1937:** full exact decoded opcode72 on a world-registered canonical NPC with 20 HP and a local scene identity. Null writer throws `NullPointerException("writer")` before hooks, damage or cooldown; a valid writer at the **same tick** then produces exactly one 10 HP hit and a packet65 mask, with no terminal callback or progression receipt.
- **G1938:** same setup at 10 HP. Null writer cannot kill or terminalize the NPC; the same-tick valid writer lands one lethal 10 HP hit, creates an accepted attacker witness and calls the specified terminal finalizer exactly once.

Two JavaExec focused test tasks and manifest **649→651**, mechanically 651 unique entries (including 3 entries outside `spk.local`). Require exact-head hosted **651/651**, bytecode major 55 and successful full Gradle build. Do not claim CI success based only on source or parent branch. All PRs draft; main unchanged.

## Authority limits

LocalLab server-side protocol bridge only: this does not recover original SpawnPK kill-credit policy, solve packet writer IOException atomicity, reserve network delivery, implement durable per-consumer progression ACK, or establish player/world close as an end-to-end atomic lease. Preserve Chat 1 account source unchanged.
