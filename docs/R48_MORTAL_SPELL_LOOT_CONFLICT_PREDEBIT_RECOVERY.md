# R48 — prevent rejected practice spells from consuming runes when lethal loot is blocked

## Scope

R47's opt-in CUSTOM_LOCALLAB Wind Strike consumed the exact air (556) and
mind (558) rune stacks **before** the R6 canonical mortal NPC death helper
checked whether the dead NPC's owner already had a coin ground item at the
same tile. The existing ground-item registry properly rejects that occupied
owner-loot tile to prevent a duplicate or accidental merge, but doing so
**after** rune debit and NPC65 hit publication could leave an inconsistent
attempt.

R48 moves all **predictable, read-only lethal-death preconditions** ahead of
both the rune debit and the NPC hit packet for Wind Strike and rune-free
Air Surge. This is a defensive server behavior improvement, not an inferred
original SpawnPK combat or transaction model.

## World-owned code

- `NpcRegistry.canRemoveDevOwnedNpc(npc)` now checks that the exact
  **same** practice actor instance is still visible at its scene index and
  has explicit dev-owned removal authority. This is a read-only query.
- `NpcRegistry.canScheduleLocalLabMortalDeath(worldTick)` checks whether
  the opt-in R7 respawn scheduler can accept another death without an
  existing scheduled respawn or tick overflow. Also read-only.
- `LocalLabMortalNpcFixture.preflightConfirmedDamage(...)` denies
  missing/not-marked/stale/dead NPCs, unregistered players, negative world
  ticks, and—only for lethal damage—missing username, missing dev-owned
  scene removal, an owner-scoped ground coin already occupying the
  practice NPC's exact (x,y,plane0), or an unschedulable R7 respawn.
  Nonlethal hits do not require an available loot tile.
- Both `castLocalLabMortalWindStrike` and
  `castLocalLabMortalAirSurge` call this preflight **before any
  `cost.consume`, NPC65 publication or optional Magic XP award**.
  A rejection is returned as a specific `R48_REJECTED_...` result.
- The canonical `applyPublishedDamage` helper repeats the same
  preflight **before the first HP mutation**, defending existing melee
  callers too, rather than leaving NPC HP=0 if the owner-loot conflict
  is detected during death publication.

These checks execute in the same serial World action context as the
actual hit and debit. They do not open a separate global lock or alter
the actual R47 rune-cost implementation.

## Real two-Main-JVM acceptance

New
`tools/verification/synthetic_v308_mortal_loot_conflict_recovery.py`
uses *only* a disposable localhost-only R6/R45/R46/R47 opt-in profile,
actual separate Java11 `spk.local.Main` server processes, and
synthetic ISAAC-protected v308-shaped C2S packets:

1. First JVM logs in with empty inventory; isolated C2S103 dev-item
   setup creates **two air runes, two mind runes and one coin**.
2. Genuine C2S164 moves onto the still-living practice NPC's tile.
   C2S87 from inventory slot2 drops an actual owner-scoped coin there,
   and its native S2C44 plus inventory53 are verified.
3. C2S131 Wind Strike (widget1152), then C2S131 Air Surge
   (widget19100), are both rejected as
   `R48_REJECTED_OWNER_LOOT_TILE_OCCUPIED` **before damage, debit,
   death, XP or duplicate ground reward**. Native inventory53
   still shows 556×2 and 558×2. R46 Magic XP remains unchanged.
4. Native ground Take removes the original blocking coin from the tile
   (S2C156 and inventory53). The **same living NPC** can then be
   attacked with a valid Wind Strike, consuming exactly **one
   air rune plus one mind rune**, producing NPC65 and exactly one
   confirmed R9 NPC_KILL plus one newly earned ground coin.
5. A stale follow-up cast is rejected; the earned reward is Taken,
   making 2 inventory coins in slot2 (the recovered blocker + new
   reward).
6. Two **separate authentic C2S145 Deposit-1** actions (not a
   non-existent Deposit-All) update the bank first to 100001 and then
   to **100002**, with both bank and inventory S2C53 checked after
   each action.
7. The first JVM saves 556×1,558×1, empty slot2, bank 100002,
   baseline Magic XP **13034431**. A fresh, second Main JVM reloads
   that exact account, emits the restored native inventories, and
   persists the same state upon logout.

Result:
`R48_REAL_JVM_LOOT_CONFLICT_ZERO_RUNE_DEBIT_AND_RECOVERY_PASS`.

Four separate source-bound tests in
`test_r48_mortal_predebit_policy.py` guard the exact ordering:
preflight before Wind Strike rune debit, Air Surge hit/XP,
canonical NPC HP mutation, and purely read-only preflight ownership
and loot checks.

## Limitations

This is **predictable failure prevalidation**, not a two-phase
durable transaction. If a network writer fails during a successful
multi-packet action, or the JVM crashes between rune debit, NPC death
and persistent account save, no all-or-nothing crash recovery has been
proven. Server retransmission, a transaction journal, replay fencing
and filesystem sync would be separate milestones.

This also does **not** certify original SpawnPK damage, runes,
spell appearance, NPC drops or Magic XP. All accepted practice
mechanics are explicit `CUSTOM_LOCALLAB` fixtures. The actual
original graphical v308 client and Windows Sandbox have **not**
been launched. Only isolated synthetic v308-shaped socket messages
and a true Java11 server were tested.

Linux and Windows Actions rerun R48's preflight policy and real two-JVM
journey, plus every inherited R47 and earlier regression.
Keep **DRAFT / STACKED ON R47 / UNMERGED** until independent CI
and separately authorized graphical guest acceptance.
