# Evidence Package — Exact-current SpawnPK Item Policy / i.bin Authority

SYSTEM

Exact-current SpawnPK item override policy metadata and client-loader enforcement boundary.

STATUS

STRONG-PARTIAL / POLICY-CENSUS-CLOSED / FULL-DEFINITION-FIELD-CENSUS-STILL-OPEN

## AUTHORITY

Matched current cache/config:

~~~
.spawnpk/configs/i.bin
~~~

Exact audit establishes that i.bin is MessagePack with exactly:

~~~
5,207 item override records
~~~

The top-level MessagePack map size also encodes 5,207 entries.

Exact-current client loader involved:

~~~
rs.t.a.d
~~~

## EXACT POLICY KEY COUNTS

| Field | Present | true | false |
|---|---:|---:|---:|
| tradeable | 1,479 | 36 | 1,443 |
| bankable | 6 | 0 | 6 |
| autoloss | 7 | 7 | 0 |
| autolost | 9 | 9 | 0 |
| autobankable | 3 | 0 | 3 |
| autokeep | 56 | 53 | 3 |
| droppable | 5 | 0 | 5 |
| destroy | 2 | 2 | 0 |
| broken | 113 | 113 | 0 |

Important exact spelling/provenance detail:

~~~
autoloss
autolost
~~~

are two distinct historical source keys in the exact current cache. They do not overlap in the current 5,207 records.

A normalized server schema may map both to one semantic axis only if it preserves which source key supplied the value.

## CLIENT-LOADER ENFORCEMENT BOUNDARY

The exact current client loader recognizes these keys:

~~~
tradeable
bankable
autoloss
autolost
autobankable
autokeep
droppable
destroy
~~~

but their parser switch cases intentionally route to a no-op branch.

Therefore these fields are:

~~~
EXACT_CURRENT_CACHE metadata
not exact client-enforced gameplay policy
~~~

This is an important architecture boundary.

The server/domain may consume this metadata as evidence, but the client cannot be treated as the authoritative enforcer of trade/death/bank/drop policy.

## BROKEN IS DIFFERENT

`broken=true` is not a no-op in the exact client loader.

The loader:

1. sets a dedicated item-definition boolean;
2. replaces the inventory-action array with a five-slot array;
3. sets the final action to `Destroy`.

Therefore:

~~~
broken = EXACT_CURRENT_CACHE metadata
       + EXACT_CURRENT_CLIENT presentation/interaction state
~~~

This still does NOT prove which source item transforms into a broken item on death.

That transition remains server authority unless separately evidenced.

## DO NOT MISUSE beginnerGear

The exact cache contains `beginnerGear=true` records.

The exact client parser does not map this flag to hit-count degradation.

Some item hover text independently states mechanics such as degrading after a number of hits, but that text evidence must remain separate from the `beginnerGear` flag.

Do not normalize `beginnerGear` into `degrades=true`.

## POLICY DIMENSIONS ARE ORTHOGONAL

Exact-current records include combinations such as:

- tradeable + autokept;
- tradeable + always-lost;
- autokeep=false + autolost=true;
- cache droppable=false while an action label may still exist;
- autokept kits/dyes associated with base/equipped items having different death behavior.

Therefore one enum such as:

~~~
TRADEABLE | UNTRADEABLE | KEPT | LOST
~~~

is insufficient.

Recommended semantic shape:

~~~
ItemPolicy {
  tradeable?
  bankable?
  droppable?
  destroyInsteadOfDrop?
  autoKeep?
  autoLoss?
  autoBankable?
}

ItemConditionDefinition {
  brokenVariant?
  transitions[]
}
~~~

Rule precedence belongs in authoritative inventory/death services.

## EXACT EXAMPLES

### Araxyte egg — 28598

Exact cache metadata includes:

~~~
stackable=true
autokeep=false
autolost=true
~~~

### Cursed vigour ring — 28596

Exact cache metadata includes:

~~~
tradeable=false
autokeep=false
autolost=true
~~~

The hover text also describes death conversion behavior. That prose is mechanics evidence, but the conversion transition must retain its own provenance rather than being silently promoted from the policy flags.

### Cursed energy — 28595

Exact cache metadata includes:

~~~
tradeable=true
autokeep=false
autolost=true
stackable=true
~~~

This directly proves that tradeability and death-loss policy are independent axes.

## CONDITION / DEATH DISCOVERY CENSUS

A conservative text classifier over exact-current names/hover fields produced:

- 111 BROKEN_FORM candidates;
- 34 death-related text records;
- 10 autokeep-related text records;
- 7 explicit always-lost/risked-on-death style records;
- 4 repair-related records;
- 3 degradation-to-dust / hit-count degradation records;
- 3 broader degradation records;
- 1 explicit transform-on-death text candidate.

These are discovery candidates only.

They must not become authoritative transitions unless an explicit config mapping or stronger runtime/server authority supports them.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- precedence between overlapping policy keys;
- exact death-item keep/loss ordering;
- transformation destination/value for description-only death conversions;
- repair prices and repair source rules unless separately proven;
- degradation counters/timing unless separately proven;
- whether every cache policy field was actively enforced by the production server.

## ARCHITECTURE CONSEQUENCE

Chat 3 should consume a provenance-aware ItemPolicy / ItemCondition repository rather than reproducing cache parsing inside gameplay handlers.

Death, inventory, trade and repair services should own enforcement.

The client/cache adapter should remain read-only evidence/config ingestion.

## READY FOR CHAT 3

yes — policy metadata and authority boundary are strong enough for domain modeling

## REMAINING ITEM-DEFINITION WORK

This package does NOT close the full P1 item-definition census.

Still to normalize:

- inventory/display model ownership;
- wearable male/female models;
- zoom/rotation/offsets;
- recolor/retexture;
- item/ground action arrays;
- stack/note/template/clone relationships;
- value/presentation metadata;
- any custom SpawnPK visual fields;
- exact-current loader ownership for each field.