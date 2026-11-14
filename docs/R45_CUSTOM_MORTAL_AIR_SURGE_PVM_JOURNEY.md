# R45 — opt-in Air Surge practice-NPC PvM through the real World death pipeline

R45 closes one concrete user-facing domain gap from [#2968](https://github.com/Trexzo/SpawnPK-RE/issues/2968): a client C2S131 spell targeted at an ordinary visible NPC previously passed visible requirement checks but deliberately had no server-owned damage outcome.

**This is a deliberately narrow first slice, NOT a general/original SpawnPK magic implementation.** Only an explicitly enabled CUSTOM_LOCALLAB mortal practice NPC can be damaged by one rune-free widget19100 Air Surge. Every ordinary HOME NPC continues to return `effect=UNIMPLEMENTED_SERVER_AUTHORITY`; other targeted spells and unknown original rune, spell damage, XP, animation and projectile semantics are not invented.

## Production code

- `LocalSpellTargetHandler` retains existing spellbook, magic level, visible spell requirements, target-kind mask and dummy behavior. The custom mortal-only branch is admitted exclusively when **both** opt-in flags are present: `-Dspk.local.r6MortalFixture=true` and `-Dspk.local.r45MortalMagic=true`. No production/default behavior changes.
- The effect callback is attached **after LocalSession creates its LocalWorldTickCoordinator**. C2S131 remains decoded and routed by the existing `LocalPendingRequestDispatcher` inside its already documented World execution context; the coordinator receives the authoritative session tick, not an independent timer.
- The coordinator denies an absent/stale/dead/nonfixture NPC, unregistered player, non-HOME/plane-zero/out-of-range caster, wrong spellbook/widget/resources, negative tick and too-fast repeat action **before mutating any state**. Only widget19100 Air Surge has zero client-visible rune requirements; this opt-in path therefore consumes none and makes no claim about original rune costs.
- The accepted action has a fixed **10 damage CUSTOM_LOCALLAB** hit, sends the existing NPC65 mask through NpcRegistry, uses the already established `LocalLabMortalNpcFixture.applyPublishedDamage` to mutate HP, remove the NPC on death, publish owner-only ground loot44, and uses the **same `LocalLabPvmKillOutcomeProducer` instance** owned by the coordinator to publish exactly one NPC_KILL World domain fact. It does not create a parallel drop/kill handler.
- The live socket fixture revealed that valid requests can be admitted before the first 600ms pulse, when the session World tick is **zero**. The first revision incorrectly required positive ticks; R45 now permits zero, matching canonical mortal death/kill event preconditions (negative ticks still denied).

## Real Java11 localhost acceptance, 2026-11-14

Exact build of commit `c7b33d20cb0f98944e82381dcdf4b1d8d10e61cf` passed under JDK21/Gradle8.10.2 with Java11 class-major55, 2,160 classes.

New `tools/verification/synthetic_v308_custom_mortal_magic.py` completed:

`R45_REAL_JVM_CUSTOM_AIR_SURGE_PVM_LOOT_BANK_RECONNECT_PASS`

- Two separate full Main JVM server processes; each owns temporary file-backed `opensrc`, binds only 127.0.0.1:43594 and 43595, uses existing authenticated test-child shutdown and releases ports.
- A first C2S131 Air Surge at ordinary HOME definition7605 remains `UNIMPLEMENTED_SERVER_AUTHORITY`, without mutating the mortal fixture.
- A separate C2S131 at the opt-in mortal NPC produces real NPC65 mask, canonical HP10→0, NPC removal, exactly one R9 semantic NPC_KILL, and one S2C44 owner-scoped earned loot995x1.
- A stale second cast at that killed scene is rejected, without a second death or drop.
- C2S236 Take yields S2C156 removal and correct inventory53; the player walks to the bank, deposits via real in-game bank controls and receives bank53. After a **new Java process**, account load restores **100001 bank coins**, original 100000 + earned coin.
- The run confirms the gameplay route and private-account durability, **not** an original graphical v308 client, original SpawnPK damage/XP/drop policy, production authentication or power-loss fsync. The test-only damage is labelled `CUSTOM_LOCALLAB_OPT_IN_AIR_SURGE_V1`.

The four separate `test_r45_custom_mortal_magic.py` negative controls pin v308 opcode131 byte encoding, mandatory flag pairing, preservation of unimplemented default NPC effects, and all authoritative mutation preflight guards. Linux/Windows GitHub Actions are required before certification. The full inherited 175 focused test suite remains required.

## Remaining work

For the [#2968](https://github.com/Trexzo/SpawnPK-RE/issues/2968) complete magic objective, still **not implemented or certified**: Wind Strike rune consumption and spell-casting costs, reusable cooldown/persistence semantics, arbitrary non-dummy canonical WorldNpc targeting and ownership, multi-hit accuracy and damage formula, visuals/projectiles, Magic XP, opponent presentation, complete boss/quest integrations, and original graphical v308 manual acceptance.

The v308 graphical guest remains blocked by unavailable Windows Sandbox. R44 stages the review-only isolated package; no proprietary graphical client was launched in R45. Keep **DRAFT, STACKED ON R44, UNMERGED** pending independent CI and review.