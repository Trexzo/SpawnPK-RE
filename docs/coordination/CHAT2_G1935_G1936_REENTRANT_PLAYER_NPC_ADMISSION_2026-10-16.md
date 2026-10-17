# Chat 2 — G19.3.5/36: synchronous callback reentry across canonical NPC handlers

**Exact parent:** Chat 1 [G21.264/265 #2794](https://github.com/Trexzo/SpawnPK-RE/pull/2794) head `280add7b7ca48546e23b783de376c149339e0909`, 643 unique focused candidates (not assumed certified). Includes Chat 1 account publication changes and Chat 2 G19.3.4 shared-player cadence.

## Remaining source-level gap

G19.3.3/34 serialize two independent *threads* on `WorldPlayer.canonicalNpcAttackLock`, but Java intrinsic monitors are reentrant. While an outer `LocalCanonicalNpcAttackHandler.handle` is in `beforeResolution.run`, a callback on the **same thread** can invoke another handler bound to the **same WorldPlayer**. At that point neither has published its `canonicalNpcAttackNextAllowedTick`; the nested attack can damage the NPC, then the outer call can damage it again in the same tick. Thread race tests G1931–34 do not cover synchronous recursive entry.

## Narrow production correction

- `WorldPlayer` adds a per-object `canonicalNpcAttackInFlight` boolean, manipulated only beneath the existing `canonicalNpcAttackLock` using `tryEnterCanonicalNpcAttack` and `leaveCanonicalNpcAttack`.
- `LocalCanonicalNpcAttackHandler.handle` acquires the existing player monitor, tries the in-flight admission, and releases it in `finally` regardless of early result, `IOException`, or callback exception.
- If the same thread reenters an *exact semantic NPC ATTACK* while the first has not returned, the inner handler returns `CADENCE_BLOCKED` without HP mutation, packet 65, accepted-hit witness, cooldown write or settlement callback.
- Non-ATTACK routes and legacy combat dummy handling remain on their original path. `CombatState` is untouched; all non-reentrant current-client canonical attack paths proceed through existing lifecycle/scene/world checks.

## Focused executable regressions

- **G1935:** genuine decoded client opcode72 on a real canonical NPC, two distinct handlers bound to one WorldPlayer; pre-resolution hook synchronously invokes the second handler. Nested result is `CADENCE_BLOCKED`; exact HP drops once (30→20) and only outer mask is emitted. After world clock advances, a legitimate second-handler hit drops HP 20→10.
- **G1936:** throw a known runtime exception from the first pre-resolution callback. Confirm no HP mutation/mask/cooldown; without advancing tick a subsequent valid other-handler click succeeds. Confirms exception cleanup cannot strand player admission permanently.

Gradle source manifest increment **643→645** (mechanical uniqueness check) and two focused JavaExec tasks. Require **fresh exact-head GitHub Actions** focused 645/645 + full Gradle build + Java 11 bytecode major55 and both G1935/36 markers; inherited parent CI must also be checked. This document alone is not evidence of a successful build.

## Boundaries not solved

This is a same-WorldPlayer LocalLab source guard, **not** a network-level anti-replay feature, a session ownership epoch, a fully atomic World-close lease, or proof of original SpawnPK cadence/kill-credit rules. The existing monitor still surrounds callbacks and packet emission, so potential lock-order/deadlock risk in unrelated callbacks remains outside this change. It does not establish durable reward delivery or per-consumer acknowledgments. Keep this PR draft, leave main unchanged, and coordinate original attribution under issue #2558.
