# LocalLab custom asset pipeline

Issue #9 replaces the rejected Voidglass native-compositor trial with a declarative custom-content boundary.

## Authority

All rows in this pipeline are **CUSTOM_LOCALLAB**. They are not recovered original SpawnPK server semantics and must never be relabeled as `EXACT_CURRENT_CACHE` or `LOCAL_RUNTIME_PROVEN` merely because LocalLab can render them.

The exact current client/cache remains external to this repository.

## Data flow

```text
server/data/items.tsv
        |
        +--> item identity / inventory action metadata

server/data/custom_pet_mappings.tsv
        |
        +--> PetDefinitionRepository
        +--> ordinary LocalLab pet Drop -> follow -> Pick-up lifecycle

server/data/custom_asset_manifest.tsv
        |
        +--> CustomAssetManifestRepository
        +--> custom NPC/model/animation/GFX presentation definitions
        +--> VoidglassR3CustomContent

server/data/equipment_slots.tsv
server/data/equipment_slot_overrides.tsv
        |
        +--> EquipmentMetadataRepository
```

The manifest is the source of truth for custom model/GFX presentation metadata kept in Git. A separate exact-current client/cache packer may consume the same IDs when that external cache is available; the server does not mutate proprietary cache files at runtime.

## Voidglass R3

Voidglass remains item `29999` and uses the normal LocalLab pet lifecycle. Its four visual candidates are now rows in `custom_asset_manifest.tsv`; the default item -> NPC mapping is a row in `custom_pet_mappings.tsv`.

The retired R2 compositor resource rows are removed. No Hydra model/animation fallback is part of the R3 manifest.

## Validation

Repository tests validate:

- exact-current item and NPC ID bounds used by the packet/client contract,
- animation/model/GFX numeric bounds,
- deterministic content-key + variant uniqueness,
- required `CUSTOM_LOCALLAB` provenance,
- four Voidglass R3 candidate definitions,
- the ordinary pet repository mapping for item `29999`,
- equipment ambiguity overrides loaded from data rather than Java tables.

A real visual/cache acceptance test still requires the external exact-current client/cache material and must not be claimed from GitHub CI alone.
