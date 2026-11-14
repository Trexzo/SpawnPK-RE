# R46 — opt-in practice-spell Magic XP, native v308 packet134 and two-JVM persistence

## Purpose

R45 establishes **only** an explicitly opt-in, rune-free `Air surge` widget19100
against a disposable R6 mortal practice NPC using the actual World-owned
damage, death, owner-scoped ground loot and semantic NPC_KILL pipeline.
The original SpawnPK spell experience formula remains unknown. R46 adds a
separate **CUSTOM_LOCALLAB** Magic XP experiment to that already certified
path without changing the default behavior.

The new independent feature flag is
`-Dspk.local.r46MortalMagicXp=true`. The private JVM admission harness
rejects it unless BOTH `r6MortalFixture=true` and
`r45MortalMagic=true` also accompany it. Ordinary world NPCs, other
spells, ordinary accounts and the default server stay unchanged.

## Production design

`LocalLabMortalMagicExperience` is a World-owned per-confirmed-damage
XP adapter. For an actually accepted R45 Air Surge, the coordinator:

1. Verifies all existing spellbook, resource, target visibility, world
   registration, HOME range, plane, tick and cooldown guards.
2. Before writing an NPC hit/death/loot event, calls
   `LocalLabMortalMagicExperience.preflight()` to reject XP overflow via
   `Math.addExact` while holding the canonical player mutation lock.
3. Emits the existing NPC65 hit and canonical R6 death/loot44 and R9
   semantic NPC_KILL as R45 already does.
4. Only **after** the authoritative hit has been applied, grants
   **1 Magic XP per actual published damage point** (10 XP for the
   10-HP R6 test NPC) under the canonical WorldPlayer mutation lock,
   publishing the existing native fixed-6 `S2C134` skill update
   for skill index 6 outside the lock.

Magic *current level* remains 99, and the new XP survives the canonical
`PlayerSnapshotSchemaV1` account `skill.6.xp` save/restore route.
The feature **does not** reconstruct original SpawnPK XP multipliers,
the normal RuneScape level curve, runes consumed, magic graphics,
projectile travel or an unrestricted NPC attack system.

The existing R45 test is strengthened to prove baseline `skill.6.xp`
does **not** change when the R46 flag is absent; its original success
result is unchanged.

## Exact test acceptance

`R46LocalLabMortalMagicExperienceTest`:

- Default off, and missing R6/R45 flag pairing stays off.
- Ordinary HOME NPC cannot earn custom Magic XP or emit skill134.
- One confirmed positive 10-damage hit increments canonical MAGIC XP
  from **13,034,431 → 13,034,441** with current level still 99.
- ISAAC-decoded opcode **134** carries the precise, proven v308
  `BootstrapPackets.skill134(6, 13034441, 99)` six-byte payload.
- Overflow at `Integer.MAX_VALUE` is refused before state mutation
  and does not emit any further packet.

`synthetic_v308_custom_mortal_magic_xp.py` exercises
**two different, cleanly owned Java11 Main server JVMs** against a
single disposable file-backed `opensrc` profile. It is built on the
successful R45 synthetic v308-shaped C2S131/NPC65/loot44/Take156/
bank53 journey and additionally proves:

- First JVM S2C134 Magic XP bootstrap is 13,034,431.
- Exactly one post-hit Magic XP134 is 13,034,441 (level 99).
- Account `skill.6.xp` appears **exactly once** with 13,034,441
  after the first JVM's logout/save.
- A **fresh, second server process** sends a native bootstrap S2C134
  with the restored Magic XP and saves the same value on logout.
- Original owner-only loot/Bank account remains **100,001 coins**.
  A stale second cast at the dead NPC does not generate extra XP
  or a second drop.

The independent Windows desktop test passed `R46_CUSTOM_MORTAL_MAGIC_XP_TEST_PASS`
and `R46_REAL_JVM_CUSTOM_AIR_SURGE_MAGIC_XP_RECONNECT_PASS`
with the exact code compiled to Java11 bytecode. Linux and Windows
GitHub Actions run dedicated R46 steps and all inherited regressions.

## Limitations and safety

This tests **synthetic v308-shaped socket traffic**, **CUSTOM_LOCALLAB**
gameplay authority and Java11 persistence, not the original graphical
v308 client. It is not a claim of true SpawnPK damage, spell animation,
rune costs, XP multipliers, Magic level progression, cross-player PvP,
or power-loss transactional persistence.

Every connected-PC test uses detached temporary sources, private test
profiles, localhost-only sockets and exclusively owned server child
processes. It does not start owner Java clients, enable a firewall
rule, change Windows optional features or launch an untrusted JAR GUI.

R46 remains **DRAFT / STACKED ON R45 / UNMERGED** until complete
independent GitHub CI and further original-client acceptance.
