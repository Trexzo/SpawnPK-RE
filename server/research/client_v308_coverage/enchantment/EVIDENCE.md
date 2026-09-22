# Evidence Package — Item Enchantment Chest (Exact v308)

SYSTEM

SpawnPK Item Enchantment Chest: main/category/item-selection surfaces, exact
button transport, item selection transport, success-chance presentation and
native preparation/success/failure animation control.

STATUS

STRONG-PARTIAL / CLIENT INPUT + RESULT-PRESENTATION CONTRACT CLOSED

## AUTHORITY

Primary exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact classes:

```
rs.n.c.G
rs.n.e
rs.Client
rs.x.e
```

Authority:

`EXACT_CURRENT_CLIENT`

## ROOTS / INTERNAL SURFACES

Main Enchantment root:

```
31244
```

Category-selection root:

```
31243
```

Internal scroll/content roots used by the builder include:

```
49994  ingredient/detail scroll
49992  enchantment/category-list scroll
50244  selectable-item scroll
50241  detail/item-style surface
```

The class also keeps exact internal root/state constants:

```
50247
50244
50241
```

as a three-element client-side surface set. The static builder does not by
itself prove a separate production server open action for every one of those
internal ids.

Exact S2C97 open bodies:

```
31244 -> 7A 0C
31243 -> 7A 0B
```

## MAIN ROOT — EXACT CLIENT PRESENTATION

Main root 31244 installs exactly 27 direct children.

Static labels/presentation include:

```
49998  "@or1@Item Enchantment Chest"
49997  "Enchantments"
49996  "Item name"
49995  "Categories"

49988  "Attempt Enchantment"

50245  "Category"
50248  blank text
50249  "Success chance: @gre@N/A"
50250  "Select a category"
50251  blank text
50252  "Ingredients @yel@(cost to attempt)"
```

These are exact client presentation strings.

The client proves a dedicated visible success-chance field. It does **not**
prove how production calculates the percentage.

## ATTEMPT ENCHANTMENT ACTION

Exact action widget:

```
49991
```

tooltip:

```
Enchant
```

visible adjacent label:

```
Attempt Enchantment
```

The button uses the ordinary action-enabled sprite helper:

```
aI = 5
M  = 1
Q  = "Enchant"
```

Exact menu routing:

```
action-enabled widget
 -> menu action 315
 -> C2S185
 -> rs.x.e.d(widgetId)
```

Exact body:

```
49991 -> C3 47
```

No recipe/cost/result semantics are encoded in that packet.

## ENCHANTMENT SELECTION ROWS

Scroll root:

```
49992
```

contains exactly 16 action rows:

```
49970..49985
```

Every row has tooltip:

```
Select enchantment
```

The exact invokedynamic concat recipe for the static row label is:

```
Category #<1..16>
```

This is client placeholder/presentation content, not proof of a production
enchantment catalog.

All 16 rows use the action-enabled text helper:

```
aI = 4
M  = 1
```

and therefore menu action315 -> C2S185.

Exact range:

```
49970 -> C3 32
...
49985 -> C3 41
```

Semantic catalog identity must be supplied above this presentation index.

## ITEM-SELECTION SURFACE

Selectable-item scroll root:

```
50244
```

contains:

```
50253  inventory/item-style selectable widget
50254..50313  60 item-grid/background entries
```

Widget 50253 installs its first item action:

```
W[0] = "Select item"
```

For inventory-style widget actions, exact client menu construction maps option
index 0 to menu action:

```
632
```

Exact action632 handling emits:

```
C2S145
widget_item_option_1
fixed length 6
```

The exact body order is:

```
widgetId
slot
itemId
```

and all three fields are written through `rs.x.e.o(int)`.

Exact `o(int)` encoding is:

```
u16 BE-A
high byte raw
low byte = value + 128
```

Therefore selecting an enchantable item is **not** C2S185. It uses the already
known exact widget-item-option-1 transport C2S145.

For widget 50253 specifically, its encoded widget component is:

```
50253 = 0xC44D
BE-A bytes = C4 CD
```

The complete packet also requires the clicked slot and item definition id.

This is a useful domain boundary:

```
C2S145(widget=50253, slot, itemId)
 -> semantic SELECT_ENCHANTMENT_ITEM(itemId)
```

after normal container/slot validation.

## SEARCH / BACK ACTIONS

Exact buttons:

### Search by name

```
50314
tooltip "Search by name"
C2S185 body C4 8A
```

Visible search text:

```
50317  "Search item"
```

### Back to categories

```
50319
tooltip "Back"
C2S185 body C4 8F
```

Visible adjacent text:

```
50322  "Back @yel@(Categories)"
```

The generic client also supports S2C187 name-input and C2S60 name-entry response,
but this static builder/callsite pass does **not** prove that production wires
50314 directly to that generic prompt sequence.

Do not assume the search-input round-trip until separately traced.

## CATEGORY ROOT / ACTIONS

Exact category root:

```
31243
```

It installs exactly 39 children.

Exact category actions:

| Widget | Visible category | C2S185 body |
|---:|---|---|
| 50327 | Armor | C4 97 |
| 50329 | Weapons | C4 99 |
| 50331 | Capes | C4 9B |
| 50333 | Trinkets & Tools | C4 9D |
| 50335 | Pets/Accessories | C4 9F |
| 50337 | Cosmetics | C4 A1 |
| 50339 | Miscellaneous | C4 A3 |

Every category button has tooltip:

```
Select category
```

and uses the ordinary action315 -> C2S185 path.

These seven category identities are exact client authority.

The item/enchantment catalogs within them are not.

## DYNAMIC PRESENTATION CHANNELS

Exact ordinary text widgets include:

```
50245  current category label
50248  dynamic/blank detail row
50249  success-chance text
50250  category-selection prompt
50251  dynamic/blank detail row
50252  ingredient/cost heading
```

They are compatible with exact S2C126 text publication.

Item-style widgets such as:

```
49993
50241
50253
```

are structurally compatible with normal client item-container update machinery
where their array dimensions permit it.

This establishes client capability, not original production publisher policy.

## EXACT ENCHANTMENT RESULT CONTROL

The exact S2C126 handler reads:

```
payload string
targetKey via rs.x.e.T()
```

When:

```
targetKey == 38
```

the client executes:

```
state = Integer.parseInt(payload)
rs.n.c.G.m(state)
```

Therefore Enchantment animation/result state is controlled through the exact
S2C126 target:

```
38
```

with a decimal integer text payload.

This is an exact target key, **not** an operation number embedded in the payload.

### State behavior

`G.m(state)` and `G.h()` prove:

#### State 0

```
idle / no enchantment overlay
```

`G.h()` returns immediately when the state is zero.

#### State 1

```
preparation path
```

It resets the progress value to zero.

While progress is below the terminal threshold, the client renders:

```
@bla@Preparing enchantment..
```

If state 1 remains unchanged until the progress reaches the terminal threshold,
the renderer resolves to the non-success/failure branch.

#### State 2

Explicit success path.

The state setter jumps progress to the terminal phase and initializes the
success animation countdown.

The client renders the success presentation, including:

```
Congratulations!
```

and:

```
<img=24> @dgr@Success! Congratulations! <img=24>
```

depending on the animation phase.

#### State >= 3

Immediate non-success/failure presentation:

```
@bla@Enchantment failed!
```

The exact client proves these renderer branches.

It does **not** prove the original server's RNG/probability decision that chooses
which state to publish.

### Current LocalLab transport capability

Current main already has a generic exact S2C126 publisher capable of sending:

```
targetKey = 38
payload   = decimal state string
```

The typed `ApplicationControl126Command.Target` enum does not currently expose
an Enchantment target, but the lower-level exact publisher supports it.

That is an API/composition gap, not missing packet framing.

## LOCAL CLIENT ANIMATION BOUNDARY

The renderer `G.h()` only draws its special overlay while:

```
Client.cH == 31244
```

so the animation is context-bound to the Enchantment main root.

Client-side animation timing/colour interpolation is presentation authority.

The production server only needs to publish semantic result state; it should not
reimplement the local animation frame-by-frame.

## SERVER SEMANTICS PROVEN

Exact client proves:

- main root 31244;
- category root 31243;
- seven exact category labels/actions;
- 16 exact selection row action ids;
- item-selection transport through C2S145;
- Search and Back actions through C2S185;
- Attempt Enchantment action through C2S185;
- visible success-chance text channel;
- visible ingredients/cost presentation;
- exact S2C126 target 38 result-state control;
- exact preparation/success/failure client animation branches.

## SERVER SEMANTICS UNKNOWN

`UNKNOWN_SERVER_AUTHORITY`:

- enchantment catalog;
- row -> enchantment mapping;
- category -> item mapping;
- eligible items;
- ingredient requirements;
- cost;
- actual success chance;
- RNG algorithm;
- pity/protection mechanics;
- failure consumption behavior;
- success output/transformation behavior;
- item binding/ownership;
- cooldowns;
- persistence;
- anti-abuse;
- production search flow after 50314;
- whether all static internal roots/states are directly server-opened.

The displayed success percentage is presentation data. A server-supplied
percentage does not prove the underlying RNG implementation.

## LOCAL LAB MAPPING

Existing `ConversionService` / recipe infrastructure is the correct generic
substrate.

Do not create another independent conversion engine for Enchantment.

Suggested semantic adapter boundary:

```
C2S185(category widget)
 -> SELECT_ENCHANTMENT_CATEGORY

C2S185(49970..49985)
 -> SELECT_ENCHANTMENT_ROW(index)

C2S145(widget=50253,slot,item)
 -> SELECT_ENCHANTMENT_ITEM(item)

C2S185(49991)
 -> ATTEMPT_ENCHANTMENT

semantic result
 -> S2C126 target 38 state
```

The names above are architectural suggestions, not recovered server class names.

## TEST VECTORS

### C2S185

```
Attempt Enchantment 49991 -> C3 47

Selection rows:
49970 -> C3 32
...
49985 -> C3 41

Search 50314 -> C4 8A
Back   50319 -> C4 8F

Armor             50327 -> C4 97
Weapons           50329 -> C4 99
Capes             50331 -> C4 9B
Trinkets & Tools  50333 -> C4 9D
Pets/Accessories  50335 -> C4 9F
Cosmetics         50337 -> C4 A1
Miscellaneous     50339 -> C4 A3
```

### C2S145 item selection

```
widget = 50253
widget bytes (BE-A) = C4 CD
then slot BE-A
then itemId BE-A
```

### S2C126 result state

```
targetKey = 38
payload = "0" | "1" | "2" | "<integer >=3>"
```

## READY FOR CHAT 2

**yes**

No new packet family is required.

Existing exact C2S185, C2S145 and S2C126 framing are sufficient.

A typed semantic S2C126 Enchantment result target could be added later without
changing wire authority.

## READY FOR CHAT 3

**yes for client contract**

Chat 3 can compose Item Enchantment onto the generic Conversion/Recipe substrate
without any further packet archaeology. Recipe/RNG/economy behavior remains
explicitly evidence-gated.
