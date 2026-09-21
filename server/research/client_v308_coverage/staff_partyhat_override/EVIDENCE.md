# Evidence Package — Staff Partyhat Override / Extra Appearance Item / Worn Rank Gate

SYSTEM
Staff/rank partyhat `Override` action, the exact-current extra player
appearance item channel (`rs.a.k.bs`), and the native worn-HEAD staff-rank
overhead badge gate.

STATUS
CLOSED-EXACT-CLIENT-CONTRACT / UNKNOWN-PRODUCTION-RANK-MAPPING

## AUTHORITY

- EXACT_CURRENT_CLIENT
- EXACT_CURRENT_CACHE
- LOCAL_LAB_POLICY (clearly separated below)

Exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
revision 308
```

## EXACT CURRENT CACHE — STAFF PARTYHATS

Current item metadata exposes:

```
22130 Tevins partyhat      actions=[null, Wear, Override, null, Drop] clone=1038
22131 Admin partyhat       actions=[null, Wear, Override, null, Drop] clone=1038
22132 Owner partyhat       actions=[null, Wear, Override, null, Drop] clone=1038
23480 Support partyhat     actions=[null, Wear, Override, null, Drop] clone=1038
23481 Mod partyhat         actions=[null, Wear, Override, null, Drop] clone=1038
23482 Super mod partyhat   actions=[null, Wear, Override, null, Drop] clone=1038
23483 Grand mod partyhat   actions=[null, Wear, Override, null, Drop] clone=1038
```

The partyhats are ordinary partyhat/headwear-family item definitions. Their
metadata does not directly point to the native icon-family roots.


## CLIENT CONTRACT — WORN STAFF PARTYHAT OVERHEAD BADGE

Revalidation against the exact-current v308 client proves the older staff-rank
rendering finding directly on SHA-256 `854f26ff...`.

Exact renderer:

```
rs.Client.o()
```

The native branch reads the ordinary HEAD appearance component:

```
headItem = player.br[0] - 512
```

and contains these exact gates:

```
23481 Mod partyhat
  -> draw fE[ rs.l.h.b(0) ]

22131 Admin partyhat
22132 Owner partyhat
23480 Support partyhat
23482 Super mod partyhat
23483 Grand mod partyhat
  -> icon = rs.l.h.b(player.aC)
  -> if icon > 0, draw fE[icon]

22133 Wealthy partyhat
  -> draw fE[ rs.l.h.b(31) ]

22130 Tevins partyhat
  -> draw fE[ rs.l.h.b(19) ]
```

So the Admin/Owner/Support/Super-mod/Grand-mod family uses the **worn partyhat
as the gate**, while `player.aC` selects the native badge index.

`rs.l.h.b(int)` is an exact client-side remap/animation lookup: if the key
exists in `rs.l.j.b`, it returns that entry's current icon/frame index;
otherwise it returns the input unchanged. The v308 static table contains mapped
keys including 21, 32, 45, 291, 333, 340, 344, 348, 352 and 356. Constants
0, 19 and 31 therefore pass through unchanged in that static table.

### Exact packet-81 field feeding `aC`

The exact `rs.a.k.a(rs.x.e)` appearance parser reads, before `br[12]`:

```
aY = y()   // u8
bd = y()   // u8
bf = y()   // u8
bg = y()   // u8
bh = y()   // u8
aC = B()   // signed BE16
```

`rs.x.e.B()` is signed big-endian 16-bit. The client also updates `aC`
from its player public-chat mask path, reinforcing that this is client-visible
privilege/rank-like player state rather than an item id.

### Current LocalLab publication gap

Current `BootstrapPackets.appearanceBlock(...)` already occupies the exact
wire field, but writes:

```
putU16(b, 0); // signed-short role aC; zero is safe in the parser
```

So the remaining LocalLab gap is now precise: there is no semantic rank/
privilege state feeding `aC`. Chat 4 does **not** invent the missing original
SpawnPK named-rank -> numeric-`aC` table.

### Worn HEAD vs cosmetic `bs`

This native overhead branch reads `br[0]` and `aC`. It does **not** read
`bs`.

Therefore exact v308 proves these are independent presentation paths:

```
Wear staff partyhat
 -> br[0] HEAD gate
 -> native fE[] overhead badge branch may activate

Override staff partyhat
 -> C2S16
 -> server may publish separate bs wearable-model channel
 -> bs alone does not satisfy the HEAD badge gate
```

This directly corrects any implementation that would treat cosmetic Override as
equivalent to wearing the staff hat for the native overhead badge.

## CLIENT CONTRACT — INVENTORY ACTION

Exact `rs.Client` item-menu construction reads item action array
`rs.d.k.L[]`.

For item-definition action index 2 (third action), exact v308 stores menu action:

```
539
```

Exact menu dispatch method:

```
private void a(int, int, int, int, int, java.lang.String)
```

For menu action 539 it writes:

```
C2S16
itemId   -> rs.x.e.o(int)
slot     -> rs.x.e.p(int)
widgetId -> rs.x.e.p(int)
```

Exact writer transforms:

```
o(int): u16_be_low_add128
p(int): u16_le_low_add128
```

Therefore a staff partyhat's third action, `Override`, is transported by exact
current v308 as C2S16.

This proves that C2S16 is a generic item-option transport. Its business semantic
comes from the selected item's action string.

## CLIENT CONTRACT — EXTRA APPEARANCE ITEM (`bs`)

Exact player appearance parser:

```
rs.a.k.a(rs.x.e)
```

After the normal 12 appearance entries (`br[12]`), the parser reads:

```
presence = y()  // u8
if presence == 1:
    bs = A()    // unsigned big-endian u16 item id
else:
    bs = -1
```

The exact class-reference census found no independent client-side partyhat/rank
setter for `bs`. In `rs.a.k`, the parser above is the authoritative write path;
other observed external use copies/reads the value for appearance cloning.

## CLIENT CONTRACT — MODEL COMPOSITION

Exact player model method:

```
rs.a.k.n()
```

Behavior:

1. validates all 12 ordinary appearance entries;
2. when `bs > 0`, validates the extra item's wearable model through
   `rs.d.k.f(bs).a(gender)`;
3. allocates 13 model components instead of 12;
4. iterates the normal appearance entries;
5. immediately after ordinary appearance index 1, if `bs > 0`, loads
   `rs.d.k.f(bs).b(gender)` and inserts it as the additional component;
6. merges the resulting components into the final player model.

So `bs` is not:

- a normal equipment slot;
- an ammo slot;
- a rank integer;
- a 2D overhead-sprite index.

It is an **additional wearable item-model channel** merged into player
appearance.

The item's own wearable-model geometry determines where that visual appears.
An item authored as a floating/icon model can therefore render above/around the
player while remaining separate from ordinary equipment.

## CACHE — NATIVE ICON FAMILY

Current item metadata contains a native icon lineage rooted at:

```
10556 Attacker icon
10557 Collector icon
10558 Defender icon
10559 Healder icon
```

Many custom icon items inherit through clone/equipClone/fullClone ancestry from
those roots, including Collection, Challenge, Blood Slayer, Ghoulish and other
icon families.

This is a genuine cache-level icon family and is compatible with the exact
`bs` item-model channel.

The staff partyhat definitions themselves do **not** inherit from those four icon
roots.

## SERVER SEMANTICS PROVEN

The exact current client/cache proves:

- these staff/rank partyhats expose an `Override` action;
- `Override` occupies their third item-action slot;
- third item-action slot routes through exact C2S16;
- a server can publish a separate extra appearance item through packet 81
  `presence + bs:itemId`;
- that `bs` item is merged as a thirteenth wearable-model component;
- the ordinary partyhat/head equipment and the `bs` channel are structurally
  independent;
- exact v308 `rs.Client.o()` gates native staff overhead badges from
  `br[0]-512`, not `bs`;
- Admin/Owner/Support/Super-mod/Grand-mod hats use `rs.l.h.b(aC)` when positive;
- Mod, Wealthy and Tevins hats use fixed native mappings 0, 31 and 19 respectively;
- packet-81 appearance exposes `aC` as a signed BE16 field before `br[12]`.

## SERVER SEMANTICS UNKNOWN

- UNKNOWN_SERVER_AUTHORITY: what original SpawnPK did after receiving
  `Override` for each staff partyhat.
- UNKNOWN_SERVER_AUTHORITY: whether production set `bs` to the selected
  partyhat's own item id.
- UNKNOWN_SERVER_AUTHORITY: whether production mapped each staff rank/partyhat to
  a different icon-family item id.
- UNKNOWN_SERVER_AUTHORITY: the original named SpawnPK rank -> numeric `aC`
  assignment table.
- UNKNOWN_SERVER_AUTHORITY: permission/persistence rules for semantic rank state.
- UNKNOWN_SERVER_AUTHORITY: whether permission/rank checks gated the Override action.
- UNKNOWN_SERVER_AUTHORITY: persistence/replacement/refund/consumption rules for
  the original Override behavior.

No exact-current client/cache mechanism maps:

```
Support / Mod / Super Mod / Grand Mod / Admin / Owner
```

to a specific `bs` item id automatically.

## LOCAL_LAB_POLICY — NOT ORIGINAL SERVER AUTHORITY

Current LocalLab's `CosmeticOverrideService` implements a reasonable local
policy:

```
selected item has exact action "Override"
    -> consume selected inventory item
    -> set CosmeticState.itemId = selected itemId
    -> publish that same itemId through player appearance bs
    -> keep ordinary equipment independent
```

The implementation itself labels its authority:

```
EXACT_CLIENT_ACTION_PLUS_EXISTING_BS_CHANNEL
```

That is a LocalLab composition of two exact client facts. It is **not** proof
that production SpawnPK used the same server mutation.

## TEST VECTORS

### Owner partyhat Override request

Example:

```
itemId   = 22132 = 0x5674
slot     = 4
widgetId = 3214  = 0x0C8E
```

Exact C2S16 body:

```
56 F4 84 00 0E 0C
```

Decode:

```
56 F4 -> itemId 22132 via u16_be_low_add128
84 00 -> slot 4 via u16_le_low_add128
0E 0C -> widget 3214 via u16_le_low_add128
```

The opcode byte itself is ISAAC-obfuscated on the live wire; the six-byte body is
the stable schema.

### Packet-81 extra item fragment

Owner partyhat carried directly as `bs`:

```
01 56 74
```

Absent extra item:

```
00
```

These bytes occur after the normal 12 appearance entries.

## FILES / METHODS

Exact current v308:
- `rs.Client` item-menu builder: action index 2 -> menu action 539
- `rs.Client.a(int,int,int,int,int,String)`: menu action 539 -> C2S16
- `rs.x.e.o(int)`: BE short with low byte +128
- `rs.x.e.p(int)`: LE short with low byte +128
- `rs.a.k.a(rs.x.e)`: appearance parser / `bs` assignment
- `rs.a.k.n()`: 12->13 model-component composition
- `rs.Client.o()`: worn-HEAD staff-partyhat overhead badge gates
- `rs.a.k.a(rs.x.e)`: `aC` signed-short appearance publication
- `rs.x.e.B()`: signed BE16 decoder used for `aC`
- `rs.l.h.b(int)`: native icon-index remap/animation lookup
- `rs.l.j.b`: exact static remap/animation table
- `rs.d.k.f(itemId).a(gender)`: wearable-model readiness
- `rs.d.k.f(itemId).b(gender)`: wearable-model load

Current cache/item corpus:
- `server/data/items.tsv`

Existing LocalLab policy/evidence:
- `ItemCatalog.isNativePlayerIcon(...)`
- `InventoryActionRouter`
- `CosmeticOverrideService`
- `PlayerState.nativeIconItemId`
- `BootstrapPackets.appearanceBlock(...)`
- `CosmeticState`
- cosmetic equipment widget `27701`

## READY FOR CHAT 2

**yes — transport evidence only**

Chat 2 may treat C2S16 as typed inventory-option-3 transport and packet-81
`bs` as an internal appearance field. Raw opcode/widget identity should remain
inside transport/runtime boundaries.

## READY FOR CHAT 3

**yes — exact presentation primitives; no for original semantic rank mapping**

Chat 3 can model:
- a semantic extra/cosmetic appearance item independently of ordinary equipment;
- a semantic rank/privilege field that an internal presentation adapter can
  eventually project to `aC`;
- worn staff-partyhat HEAD gating separately from cosmetic `bs`.

Chat 3 must not invent the original named-rank -> numeric-`aC` table. That
mapping remains `UNKNOWN_SERVER_AUTHORITY` until separate production evidence
is recovered.
