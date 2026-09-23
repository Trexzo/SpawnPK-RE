# Exact-v308 Custom Asset Pipeline

Issue #9 successor boundary for Chat 2.

This pipeline replaces the rejected Voidglass R3 native-compositor trial with a fail-closed authoring contract grounded in the exact-current v308 evidence. It is deliberately split into two layers:

1. **source-controlled authoring metadata** in `server/data/`;
2. **external cache build/injection artifacts** produced only from an exact-current namespace snapshot and written only to an isolated cache copy.

No proprietary exact client/cache bytes belong in this repository.

## Authority

Canonical exact client:

`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`

Every authored row must carry `CUSTOM_LOCALLAB` provenance.

The current proven model boundary is intentionally narrow:

- legacy/no-marker textured + skinned model family;
- at most 64 mapping triangles (`0..63`);
- one rigid one-hot skin group per vertex;
- hierarchy flattening only when the guarded decomposition does not introduce meaningful shear;
- deterministic UV/material binding;
- texture slot `278` as the proven bootstrap/atlas slot only;
- existing exact animations may be referenced only when the authored model/skeleton pairing is independently proven;
- `AnimationMode.NONE` keeps an asset static rather than inventing an animation binding;
- custom frame-group/sequence identities are representable, but must still pass exact namespace preflight before build;
- blended weights are unsupported and must fail closed.

Slot 278 is not an unlimited custom-texture namespace.

## Source-controlled inputs

### `custom_asset_authoring.tsv`

Declares semantic asset identity and build constraints. The initial Voidglass row requests item `29999`, NPC `12000`, primary-context model `79999`, texture `278`, 12 legacy mapping triangles, **no stand/walk animation binding yet**, and exact existing GFX `5042`, all under `CUSTOM_LOCALLAB_VOIDGLASS_V308_PIPELINE` authority. The static `-1/-1` NPC animation state is directly proven for the custom `12000 -> 79999` presentation path; a concrete stand/walk sequence must not be assigned until that exact model/skeleton pairing is independently proven.

These are **requested claims/references**, not proof that the IDs are free. A build must not proceed until preflight validates them against an exact-current snapshot.

### `custom_definition_overlays.tsv`

Stores item/NPC override fields one row at a time so every field retains provenance.

The repository applies the exact-v308 clone boundary:

- item source selection: `fullClone` before `clone`;
- NPC source selection: `clone`;
- source overrides are recursively resolved before the target's current fields;
- target identity is restored before current fields apply;
- item source `osrs` is inherited unless the target explicitly supplies `osrs`;
- `equipClone` / `cloneEquip` are rejected as item clone-prepass keys;
- `param_*` is rejected because exact v308 skips it before active-field handling;
- unknown/unapproved authoring fields fail closed.

This layer models exact client definition construction. It does not invent server prices, rewards, eligibility, combat policy or other private server authority.

## Exact namespace snapshot

`CustomAssetNamespacePreflightMain` consumes an external TSV census:

```text
clientSha256	854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
scope	BASE_PLUS_EXACT_OVERRIDES
recordType	namespace	context	value
CAPACITY	ITEM	GLOBAL	...
PRESENT	ITEM	GLOBAL	...
GFX_CONTEXT	GFX	PRIMARY	...
```

The producer of this snapshot must census both base cache capacity and exact-current override corpora (`i.bin`, `e.bin`, `a.bin`, `g.bin`, textures/models as applicable). For GFX it must resolve the actual PRIMARY/OSRS model context and fail instead of emitting an unresolved dependency.

Preflight rejects collisions, out-of-capacity IDs, missing stock references, unresolved GFX model context, and inconsistent item/NPC projections. It hashes the exact snapshot, definition metadata and normalized authoring plan.

Successful marker:

`CUSTOM_ASSET_NAMESPACE_PREFLIGHT_PASS ... planSha256=<sha256>`

The plan hash is the deterministic boundary handed to the binary build stage.

## Cache build/output boundary

Binary model/texture/definition/frame/animation encoders are a separate build layer. The previously proven R11/R12 research formats must be reused from verified source; they must not be re-created from prose or guessed.

A complete build stage must:

1. consume a successful preflight plan;
2. compile only the proven legacy textured/skinned model family;
3. reject blended weights;
4. reject unsupported hierarchy shear;
5. reject mapping counts above 64;
6. augment the proven texture-278 bootstrap deterministically;
7. serialize definitions using the clone/context rules above;
8. honor PRIMARY vs OSRS model context for GFX;
9. copy the exact source cache first and write only to the copy;
10. verify the source cache hashes are unchanged;
11. emit deterministic artifact hashes and markers for model, texture, definitions, frame group, animation and cache clone.

The repository-side contract intentionally does **not** claim those binary artifacts have been rebuilt in this successor slice.

## Server lifecycle

`PetDefinitionRepository` now consumes the authored PET row normally. The old Voidglass reflection into its private `BY_ITEM` map is retired.

`VoidglassR3CustomContent.ensureRuntimePetMapping()` is retained only as a compatibility validation seam; it no longer mutates the repository.

The current Voidglass identity remains `29999 -> 12000`, but its visual contract is a single custom-pipeline model claim rather than four native-compositor candidates.

Optional equipment metadata is carried in the same authoring row and is consumed by `EquipmentMetadataRepository` when a future authored asset declares a slot.

## Acceptance boundary

Repository regression:

`CUSTOM_ASSET_PIPELINE_CONTRACT_PASS ...`

This proves metadata parsing, clone rules, deterministic plan hashing, collision rejection, GFX-context rejection and server pet mapping.

It does not prove visual acceptance. Final acceptance still requires the verified binary build layer, exact-engine validation, isolated cache-copy hash proof, and a real local v308 GUI/world session.

No current release (#431) acceptance claim is implied by this next-train branch.


## Preserved R10-R12 research provenance

The successor now carries `tools/custom-assets/verify_issue9_research_evidence.py` to pin the exact safe research packages that established the current format boundary.

Verified package SHA-256 values:

- R10 full authoring kit: `a83712a70b532dee3fde91c623cabe19e3e785064d9e03c45181f142f6f5a2f6`;
- R11 successful Blender Actions artifact: `573b828ff42e2b8d563cd3fa985906d01f0625021f145a786783b805129d729b`;
- R12 texture/render kit: `3143425a58240c424ba2ad189269627a0458500c6bde7e578619219e5a71a709`.

The verifier also pins the preserved R10 rig/frame/a.bin compiler source, the R12 deterministic texture-278 archive builder, the exact R11 Blender-authored OBJ/skin/material/rig/PNG inputs, and the certified generated model/frame/animation hashes.

The R11 authored-source hashes are byte-identical in the successful Actions artifact and in the R12 safe kit.

One reproducibility boundary remains explicit: the standalone legacy textured+skinned model writer used for R11 was proven and independently rerun, but its source was not preserved in the surviving R11/R12 packages or research branch. The certified custom model output remains pinned at:

`6cf617b5e14e60b5bc58d4f1c72e11476f09382d40a72f49be122009157c7fad`

Do not substitute the preserved R10 FF-FF model converter: R11 rejected that family for the combined texture+skin contract. Until the legacy writer source is recovered or independently re-established against exact v308, the repository must report `modelWriterSourcePreserved=false` and must not claim source-to-model reproducibility.
