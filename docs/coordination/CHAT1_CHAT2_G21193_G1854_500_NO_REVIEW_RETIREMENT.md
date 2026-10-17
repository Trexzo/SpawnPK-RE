# Chat 1 + Chat 2 integration: G21.193 / G18.5.4 (500 focused)

## Exact certified integration origin

Stacked on [Chat 2 G21.192/G18.5.4 #2603](https://github.com/Trexzo/SpawnPK-RE/pull/2603) at **exact** SHA `adb54f86cf50d17fe879f39a47aeb725a3d409bc`, [Actions #38575800957](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38575800957) SUCCESS. That parent combines Chat 1 opt-in forced real World postimage and Chat 2 G18.5.4 test-only PvM receipt file-roundtrip, with **499 unique** focused manifest. This integration is **NOT** a gameplay rule or account recovery fix.

## Additive Chat 1 regression

Copies exact Git blob `6a12f85bebc1eba3d60f214efa82788ef9b8be5f` from [Core G21.193 #2604](https://github.com/Trexzo/SpawnPK-RE/pull/2604) for `G21193CoreForcedHealthySaveStillQuarantinedTest.java` and exact core research note. Adds only dedicated Gradle task and focus manifest **499→500 unique**.

**Test intention**: enable both session ownership locks, optional review marker veto, G21.190 one-shot ACTIVE before actual TCP admission, and G21.192 forced real World save together on disposable account. After initial real TCP connection logs out cleanly, verify the forced postimage and ACTIVE marker both remain. A second actual TCP login in fresh World must be rejected despite the saved account being directly readable for diagnostics. Assert marker/account byte preservation and no owner token leak. **This verifies safe refusal, not account reusability or safe retirement.**

No changes to Chat 2's G18.5.4 test, PvM observer, LocalSession, World, account repository, production Java, runtime config defaults, original SpawnPK authority or client. ACTIVE cannot be unlinked on the strength of healthy World save. No replay/grants/settlement/cleanup/unlink/durable account epoch/power loss claim.

## Certification gate

**Fresh exact-head CI required**: Java 11 bytecode major 55, marker `G21193_FORCED_SAVE_REVIEW_QUARANTINE_PASS`, **500/500** focused, full Gradle SUCCESS. Both #2604 and this joint PR remain Draft until respective commit passes. Ancestor success is not certification of this head.

Issue owners remain: account lifecycle [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) / [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559); LocalLab G1 receipt idempotency [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587). Main and Chat 3's client untouched.
