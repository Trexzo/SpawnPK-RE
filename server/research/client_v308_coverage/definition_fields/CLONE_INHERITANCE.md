# Exact v308 Definition Clone / Inheritance Precedence

SYSTEM

Exact-current item/NPC override source resolution, recursive clone behavior, and per-field override ordering.

STATUS

CLOSED-FOR-CURRENT-OVERRIDE-CORPUS

## AUTHORITY

Exact client:

```
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
```

Exact matched cache:

```
.spawnpk (2)(1).zip
SHA-256 607425ad3fe69f4cfcaaf82220d9a0d4ebff9954e896819245f37271713d299a
```

Loaders:

```
items -> rs.t.a.d#b(int, Map)
NPCs  -> rs.t.a.b#b(int, Map)
```

## ITEM CLONE PRECEDENCE

The exact item loader starts from the definition for the current item id and then performs a clone pre-pass before ordinary override fields.

Exact source selection:

```
if fullClone exists:
    sourceId = fullClone
else if clone exists:
    sourceId = clone
else:
    no clone-source copy
```

Therefore, if both keys were ever supplied, **fullClone wins source selection**.

The exact-current `i.bin` corpus contains:

```
clone     = 2,714 records
fullClone =    43 records
both      =     0 records
```

So the precedence rule is real loader behavior, though no current override exercises the both-present case.

### Recursive source resolution

For a selected clone source:

1. if the source id is present in the current override map:
   - reuse its already-built override definition if cached;
   - otherwise recursively build that source override first;
2. otherwise load the base definition for that source id;
3. copy the resolved source definition into the current target;
4. restore the current target id;
5. apply the current record's ordinary fields afterward.

This means current-record fields override inherited source fields.

### Current clone graph

Exact-current item graph:

```
2,757 effective clone-source edges
1,931 source ids also exist in the override map
  826 source ids resolve directly to base definitions
0 cycles
maximum override-chain depth = 3
```

Depth distribution:

```
depth 1 -> 2,513 records
depth 2 ->   224 records
depth 3 ->    20 records
```

Exact depth-3 examples:

```
28050 -> 27677 -> 20800 -> 4082
27534 -> 27533 -> 27486 -> 22830
27532 -> 27531 -> 23630 -> 23626
```

No cycle guard should be inferred from this result; it only proves the exact-current corpus is acyclic.

## fullClone VS clone — HOVER INHERITANCE

The exact loader has a second pre-pass for hover metadata.

For exact-current data:

- `fullClone` participates in hover-text inheritance;
- ordinary `clone` alone does not trigger that full-hover inheritance path;
- `hoverClone` is supported by loader vocabulary but does not occur in current `i.bin`.

When the source hover metadata exists in the client's hover registry, the loader can copy that source hover string to the current item.

Therefore `fullClone` is not merely an alternate spelling of `clone`.

## OSRS FLAG INHERITANCE

After a clone source is copied, the source definition's OSRS flag is remembered.

After current-record fields are applied:

```
if current record does NOT explicitly contain osrs:
    inherit source osrs flag
else:
    explicit current osrs value wins
```

The explicit-key check is case-insensitive.

This is exact client precedence.

## equipClone / cloneEquip ARE NOT ITEM-LOADER CLONES

Exact-current `i.bin` contains:

```
equipClone = 213 records
cloneEquip =   1 record
```

Of the 213 `equipClone` records:

```
124 also contain clone/fullClone
89 do not
```

The exact v308 item-definition loader:

- recognizes lowercase `equipclone` / `cloneequip` in its field switch;
- routes both to the no-op branch;
- does not include either key in the clone-source pre-pass.

A whole-JAR string scan found this vocabulary in `rs/t/a/d.class`, where the branch is no-op.

Therefore, for **v308 item-definition construction**:

```
equipClone != clone
cloneEquip != clone
```

Do not make a semantic definition repository inherit model/equipment state from these keys unless a separate exact consumer is recovered.

They may represent historical/tooling/server metadata, but that meaning is not proved by this client loader.

## param_* KEYS ARE SKIPPED

Exact-current `i.bin` contains one record each for:

```
param_1 .. param_8
```

The exact item loader checks:

```
key.startsWith("param_")
```

and immediately skips the entry before the normal field switch.

A whole-JAR string scan finds the `param_` vocabulary in `rs/t/a/d.class`.

Therefore these are exact cache metadata but not v308 item-definition mutation fields.

## NPC CLONE PRECEDENCE

The exact NPC loader begins from the current/base NPC definition.

It additionally supports a `reset` control in loader vocabulary, but no `reset` key occurs in exact-current `e.bin`.

When `clone` is present:

1. if source id exists in the current override map:
   - recursively build it if necessary;
   - otherwise reuse the cached built override;
2. else resolve the source from the base NPC definition repository;
3. copy the source definition;
4. restore the current NPC id;
5. apply the current record's ordinary override fields.

Current exact NPC graph:

```
149 clone edges
 76 source ids also exist in the override map
 73 source ids resolve directly to base definitions
0 cycles
maximum override-chain depth = 2
```

Depth distribution:

```
depth 1 -> 136 records
depth 2 ->  13 records
```

Exact depth-2 examples:

```
8395 -> 8089 -> 1972
8399 -> 8089 -> 1972
8396 -> 8089 -> 1972
```

## POST-CLONE FIELD ORDER

For both item and NPC loaders the meaningful ordering is:

```
base/current definition
 -> clone/reset pre-pass
 -> recursively resolved source copy
 -> restore current identity
 -> apply current override fields
 -> loader-specific post-processing
```

So server-side definition reconstruction should not flatten clone chains by arbitrary JSON/map iteration order.

Clone source resolution is a distinct phase.

## AUTHORITY BOUNDARY

This package proves **client definition construction order**.

It does not prove:

- server trade/death/combat rules;
- why a historical config author chose a clone source;
- whether server-side tools separately consumed no-op keys such as `equipClone`;
- original production cache-generation logic;
- semantic equality between visually identical cloned definitions.

## ARCHITECTURE CONSEQUENCE

A semantic definition repository should resolve into an immutable effective definition while retaining provenance:

```
EffectiveItemDefinition {
    id
    baseSource?
    cloneChain[]
    explicitOverrideFields[]
    effectivePresentation
    sourceProvenance
}

EffectiveNpcDefinition {
    id
    baseSource?
    cloneChain[]
    explicitOverrideFields[]
    effectivePresentation
    sourceProvenance
}
```

Keep raw cache keys and obfuscated client fields inside the authority/adapter layer.

## READY FOR CHAT 3 / ISSUE #9

**yes**

This is sufficient to implement deterministic override resolution compatible with exact v308 without guessing gameplay policy.
