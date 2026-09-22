# Evidence Package — Exact v308 In-Game Loadout Editor

SYSTEM

SpawnPK in-game item/LMS loadout editor, save/default controls, and legacy C2S103 serialization contract.

STATUS

CLOSED-CLIENT-SAVE-CONTRACT / APPLY-AND-POLICY-PARTIAL

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.al
~~~

## ROOT

~~~
33000
~~~

Exact UI includes:

- Item Loadout Modification Interface;
- LMS Loadout;
- Save loadout;
- Set as default;
- Reset to default.

## EXACT CONTAINERS

~~~
33002 -> 28-slot item/inventory container
33003 -> 15-slot equipment container
~~~

These are client presentation/editing surfaces. They do not prove server acceptance of arbitrary client-supplied items.

## CONFIRMED CONTROLS

~~~
33006 -> Save loadout
33009 -> Set as default
~~~

`33009` behaves as an ordinary actionable widget in the recovered pass.

## SAVE FLOW

When Save loadout is activated, exact v308 client behavior is:

1. emit the ordinary widget action;
2. serialize current equipment;
3. serialize current inventory ids/amounts;
4. stage two legacy compatibility commands;
5. send both through C2S103.

Exact legacy command grammar:

~~~
::cld1 <serialized equipment>
::cld2 <serialized inventory>
~~~

These command strings are compatibility wire vocabulary, not appropriate domain APIs.

## ATOMICITY CONSEQUENCE

The pair represents one logical saved loadout snapshot.

Server architecture should normalize the pair into one semantic operation such as:

~~~
SaveLoadoutSnapshot {
  equipmentSnapshot
  inventorySnapshot
}
~~~

A partial pair must not result in half a persisted loadout.

Recommended behavior:

- stage both halves under session/player ownership;
- validate both snapshots;
- atomically replace one versioned saved loadout;
- reject/expire an unmatched half;
- never expose `cld1` / `cld2` to content/plugins.

This is an architectural correctness requirement inferred from the exact split-wire contract, not a claim about the historical server implementation details.

## SET DEFAULT

~~~
33009 -> Set as default
~~~

No comparable local serialization branch was recovered for this action in the exact audit.

Conservative interpretation:

~~~
C2S185 widget action
 -> semantic SetDefaultLoadout request
~~~

Do not invent a hidden bespoke packet.

## OTHER LOADOUT COMPATIBILITY ROUTES

The exact client family also exposes compatibility command routes including:

~~~
::equip_loadout
::item_loadout
::skills_loadout
::pet_loadout
::spawn_loadout
~~~

These belong in compatibility decoding/presentation adapters rather than the public domain API.

## IMPORTANT DISTINCTION

The in-game server-backed loadout editor is not the same as the desktop/RuneLite-style Loadouts tool under `rs.gui.*`.

Do not merge their semantics merely because both are called loadouts.

## SERVER SEMANTICS PROVEN

The exact client proves:

- an in-game loadout editor exists;
- inventory and equipment snapshots are separately represented;
- Save loadout serializes both snapshots;
- serialization is split into two C2S103 compatibility commands;
- Set as default is an explicit action;
- LMS loadout is a first-class presentation concept.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- number of saved loadout slots;
- eligibility/cost;
- item ownership validation policy;
- whether unavailable items are spawned, rejected or skipped;
- skill-state validation;
- pet validation;
- spell/prayer handling;
- default-loadout persistence semantics;
- reset-to-default policy;
- apply rollback behavior;
- restrictions by area/minigame;
- anti-abuse.

## EXISTING SERVER SUBSTRATE

LocalLab already has LoadoutService-style foundations.

This package should strengthen the adapter/request boundary rather than create another loadout engine.

## READY FOR CHAT 2

yes

Chat 2 / Issue #17 should normalize exact `cld1`/`cld2` compatibility traffic into a typed, ownership-safe request sequence with bounded staging.

## READY FOR CHAT 3

yes

Chat 3 can consume one semantic loadout snapshot/default model. It should not parse command strings or raw packed slot data.