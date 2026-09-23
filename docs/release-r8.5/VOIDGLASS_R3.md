# Voidglass Nistirio R3 — retired native compositor trial

R3 originally replaced the rejected Hydra recolor with four compositions of existing native models. That compositor path is now **superseded by Issue #9's exact-v308 custom asset pipeline**.

## Identity retained

- LocalLab item: **29999**.
- LocalLab pet NPC: **12000**.
- Legacy invalid R2 item 32760 remains retired.
- Server lifecycle remains normal item -> Drop -> pet mapping.

## What is retired

- the four NPC candidate family `12000..12003`;
- native-model composition as the intended final visual;
- `::voidglass3 candidate 2..4` as meaningful visual alternatives;
- direct mutation of live `i.bin` / `e.bin` by `VoidglassR3ConfigPatchTool`.

`VoidglassR3ConfigPatchTool` now exposes only the exact-v308 namespace-preflight bridge. Patch/verify mutation modes fail closed.

## Successor

See `docs/CUSTOM_ASSET_PIPELINE_V308.md`.

The successor authoring row requests a single custom model identity and requires all item/NPC/model/animation/GFX/texture claims and references to pass an external `BASE_PLUS_EXACT_OVERRIDES` exact-v308 census before any binary build.

Current proven format boundary:

- legacy/no-marker textured + skinned model family;
- rigid one-hot vertex skin groups;
- guarded hierarchy flattening;
- at most 64 mapping triangles;
- texture slot 278 bootstrap;
- exact GFX model context;
- isolated cache copies only.

The repository-side successor does not claim the final binary model/cache build or live GUI visual acceptance yet.

## Runtime controls

Compatibility commands remain:

- `::voidglass3 give`
- `::voidglass3 candidate 1`
- `::voidglass3 next` (reselects the single authored identity)
- `::voidglass3 proc`
- `::voidglass3 status`
- `::voidglass3 reset`

`::voidglass2` remains an alias for compatibility. Candidate indices 2..4 are retired.
