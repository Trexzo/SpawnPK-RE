# R47 — explicitly opt-in Wind Strike rune costs on one practice NPC

## Why this milestone

R45 proves the R6 mortal practice NPC can be killed by a rune-free,
custom LocalLab Air Surge using the **real shared World, NPC65, death,
owner-scoped loot44, inventory Take156 and bank53**. R46 proves opt-in
custom Magic XP for confirmed Air Surge hits survives two JVM starts.
Neither proves that a spell with **client-visible resource requirements**
consumes actual items on a successful cast.

The pinned client-known spell repository defines **modern Wind Strike,
widget 1152**, requiring **1 air rune (556) and 1 mind rune (558)**,
with an equipped air-staff alternative in the *client visibility*
requirements. Those requirements do **not** prove the original SpawnPK
server's consumption or damage behavior.

R47 implements a **CUSTOM_LOCALLAB** experiment, only when all flags
are present:

```
-Dspk.local.r6MortalFixture=true
-Dspk.local.r45MortalMagic=true
-Dspk.local.r47WindStrikeRunes=true
```

No default SpawnPK NPC, original client, account or actual game server
is modified. Other spells and normal NPCs retain the explicitly
unimplemented original-server authority.

## Implementation

- New `LocalLabMortalWindStrikeRunes` matches the **exact**
  widget1152/modern/2-requirement spell schema. Unlike the UI
  validation's equipped-provider alternatives, this test-only branch
  insists on **actual items 556 and 558 in separate inventory slots**
  before constructing an exact-slot spending plan. One of each is
  consumed with existing `BankState.consumeInventoryAmountSemantic`
  after validating both plans; a single native inventory S2C53
  publishes the resulting two-slot change.
- World-owned `LocalWorldTickCoordinator.castLocalLabMortalWindStrike`
  denies stale/dead/unregistered/nonfixture NPCs, non-HOME/plane0 or
  out-of-range positions, negative world ticks, and rapid repeated
  casts **before consuming anything**. The same shared custom-spell
  cooldown is used for Air Surge and Wind Strike, preventing a
  between-widget cooldown bypass.
- An accepted cast applies **10 fixed CUSTOM_LOCALLAB damage** to
  the R6 10-HP practice actor, via the *existing*
  `LocalLabMortalNpcFixture.applyPublishedDamage` and the coordinator's
  **same** `LocalLabPvmKillOutcomeProducer` (NPC_KILL), with NPC65
  hit, owner ground item44 and canonical Take/bank handling.
  **10 is not a recovered original Wind Strike damage value**.
  Wind Strike does **not** grant new Magic XP; R46 stays
  separately opt-in for the Air Surge path only.
- Existing `MagicState` resource validation, original-v308-shaped
  C2S131 packet decode and unrelated spell handling stay intact.
- The embedded item catalog contains client-known **Air rune**
  and **Mind rune** rows with **unspecified** stackability; the
  original LocalLab default interpreted them as nonstackable. R47
  adds a narrow fallback **only** for IDs **556/558**, **after**
  respecting any explicitly inherited current configuration value.
  Other unknown items and even nearby rune ID557 remain unchanged.
  Java tests pin this boundary.

## Real Java11 integrated acceptance

The Windows source checkout of the R47 branch compiled to **2,165
Java11 class-major55 classes**, BUILD SUCCESSFUL.

`R47LocalLabMortalWindStrikeRunesTest` passed:
default off, missing-flag rejection, exact client-known spell
requirements and two-stack preflight, no spend before plan, exact
one-of-each rune cost, native inventory53 emission and stale-plan
fail-closed without partial spend.

`tools/verification/synthetic_v308_wind_strike_runes.py` passed:
`R47_REAL_JVM_CUSTOM_WIND_STRIKE_RUNES_LOOT_BANK_RECONNECT_PASS`.

The real test uses two separate full Main JVM processes, one disposable
`opensrc` profile and synthetic v308-shaped TCP/ISAAC traffic:

1. Login to fresh HOME, R6 practice mortal appears. Client sends
   C2S103 `item 556 2` as an isolated **local-dev** command. A
   C2S131 Wind Strike without a mind rune is rejected by original
   client-visible resource validation; no NPC mutation.
2. C2S103 `item 558 2`; native inventory53 now shows **two air and
   two mind runes** in separate canonical stack slots.
3. A genuine C2S131 widget1152 applies exact one-of-each debit,
   publishes inventory53 (**one of each remains**), NPC65 hit,
   authoritative HP10→0, exactly one R9 NPC_KILL World domain fact
   and one earned owner ground item44 for one coin.
4. A second stale C2S131 against the removed NPC is rejected, with
   no second death or rune cost. Walk to loot, C2S Take→S2C156 and
   inventory53 with earned coin in slot2, then use actual object bank
   overlay248 and native bank/inventory53 to deposit **that coin**.
5. Logout saves canonical inventory slots 0/1 with 556x1, 558x1,
   empty slot2, bank slot0 with **100001 coins**, and **unchanged
   Magic XP 13,034,431**.
6. A completely fresh Main JVM restores the two rune stacks and
   updated bank, verifies native S2C53 and file-backed snapshot
   fields, and exits cleanly. Both test ports become free.

Linux/Windows GitHub Actions run the Java negative controls and real
two-JVM test, followed by inherited regressions.

## Precise limitations

- **CUSTOM_LOCALLAB** fixed 10-damage and exact inventory-only rune
  costs are explicit test rules, not recovered original SpawnPK
  combat mechanics, rune costs, staff-substitute consumption rules,
  visuals or animations.
- Original graphical v308 has **not** been launched; testing remains
  v308-*shaped synthetic protocol* against a real Main JVM.
- The World execution context serializes live hits, but a network
  failure, process crash or lost account write during a combined
  debit/hit/drop sequence is **not** proven transactionally atomic.
  Do not claim crash durability or anti-duplication certification.
- The R44 offline guest package is still review-only because a
  Windows Sandbox guest is unavailable on the owner desktop.
- Tests use only disposable directories, localhost and individually
  owned Java11 test child processes; never touch existing owner Java
  processes, host firewall, OS features or live accounts.

R47 remains **DRAFT, stacked on R46, unmerged** pending exact-final-head
Linux/Windows CI and future original-client graphical acceptance.
