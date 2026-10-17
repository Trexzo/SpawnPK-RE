# G21.245 — primary/named legacy prepublication lock object recheck

Built on Draft [G21.244 #2741](https://github.com/Trexzo/SpawnPK-RE/pull/2741) exact `298e3569cec56ba5cef127c176372abc3295a1e2`, its own hosted CI required.

This extension validates the remaining *two* legacy cooperating RAW writers that G21.244 had wired but had not deterministically tested at their final move. G21.245 introduces package-only no-op-by-default pre-move hooks on the **same implementation methods** used by ordinary AccountStore.save and LocalAccountProfiles.save; no separate serializer or account layout. The focused regression first writes each legacy account normally, substitutes a real `.session.lck` inode after legacy fixed-temp serialization but before final file move, asserts IOException and byte-identical old account postimage, verifies temp cleanup and no leaked RAW reservation, then confirms both writers can save normally afterward.

Also tightens FilePlayerRepository's historical non-atomic fallback: if ATOMIC_MOVE is unsupported, recheck the original opt-in OS lock object's name before the compatibility move. G21.244 already rechecks both legacy fallback paths.

G21.245 manifest **586→587**, exact-head hosted Java 11 major 55 and full Gradle required. Entirely experimental opt-in safety narrowing: **NOT** a durable epoch, final-save ACK, atomic race exclusion against hostile swaps after the recheck, crash recovery, original SpawnPK behavior, external writer completeness, or ACTIVE review clearance. Leave #2540/#2559 OPEN, DRAFT/unmerged.
