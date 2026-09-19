# Voidglass Nistirio R3 — native compositor trial

R3 explicitly supersedes the rejected R2 Hydra recolor.

## Corrected identity

- Item: **29999** (inside the exact client's hard 0..29999 item-definition table).
- Legacy R2 item 32760 is retired and removed from the live custom `i.bin` record set.
- Item action 5 is `Drop` in both the client definition and LocalLab semantic authority.
- Inventory model: 32324.
- Default pet mapping: item 29999 -> NPC 12000.

The R3 patcher migrates exact R2 live configs, verifies the result, and is byte-idempotent on a second application.

## Four non-Hydra live candidates

1. NPC 12000 — **Rift Reaper**
   - models: 32327, 32325, 32324, 32326, 32765, 32530
   - stand/walk: 1662 / 1663
   - scale: 58

2. NPC 12001 — **Arcane Singularity**
   - models: 17378, 17394, 17387, 17399, 17390, 34252
   - stand/walk: 66 / 63
   - scale: 45

3. NPC 12002 — **Nightmare Shard**
   - models: 39182, 32530, 40177
   - stand/walk: 8593 / 8592
   - scale: 45

4. NPC 12003 — **Ripper Soul**
   - models: 44733, 42282, 34252
   - stand/walk: 10921 / 10920
   - scale: 55

No candidate uses Hydra model 36185 or Hydra idle/walk 8233/8232.

## Proc

- forced text: `VOIDGLASS RIFT`
- GFX: 5042
- candidate-specific animation
- no gameplay modifier

Hydra proc animation 8236 and GFX 4098 are not used.

## Runtime controls

- `::voidglass3 give`
- `::voidglass3 candidate 1`
- `::voidglass3 candidate 2`
- `::voidglass3 candidate 3`
- `::voidglass3 candidate 4`
- `::voidglass3 next`
- `::voidglass3 proc`
- `::voidglass3 status`
- `::voidglass3 reset`

`::voidglass2` remains an alias to the R3 controls for compatibility.

## Boundary

R3 is a **new composition of existing native model bytes**. It is deliberately no longer a Hydra recolor, but it is not yet a newly-authored raw 3D mesh/model archive. If none of the four candidates is visually good enough, the next lane is true model/cache injection rather than another recolor/compositor tweak.
