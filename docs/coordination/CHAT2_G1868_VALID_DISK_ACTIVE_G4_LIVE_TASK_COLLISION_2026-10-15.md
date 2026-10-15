# Chat 2 G18.6.8 — real on-disk VALID ACTIVE G4 collides with completed same-World task

Date: 2026-10-15.

**Certified parent**: G18.6.7 [#2648](https://github.com/Trexzo/SpawnPK-RE/pull/2648), exact `228701cc130ebad0e193e130b40e3f7f37c75f2b`, [hosted Actions #38728601770](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38728601770) SUCCESS, **514 unique focused**, Java11 major55/full Gradle. Additive gameplay test only, DRAFT/unmerged. Latest certified Chat1+Chat2 cumulative before this is #2646 at 513; no core/account production changes.

## New real-file semantic ownership collision

G18.6.3 previously demonstrated a valid but old ACTIVE Slayer snapshot colliding with a completed World/name-owned task using a **test-only in-memory snapshot copy**. G18.6.7 proved a real `FilePlayerRepository` serialization roundtrip for completed state without name-lifetime collision. **G18.6.8 combines actual file bytes and real installed NPC terminal delivery in the same World**:

1. World register A, legitimately start Monster Hunter G4 ACTIVE0 and DMM EASY0. **Explicit test-owned FilePlayerRepository.save(PlayerSnapshotCodec.capture(A))**, verify actual account file exists and loads via `PlayerSnapshotCodec.applyValidated`, retain exact file bytes. Spawn an actual NPC with immutable `RecipientOrigin=A`.
2. Unregister A, register B under same name, execute first genuine canonical NPC death. World-scoped Slayer task completes on B (COMPLETED1 persisted in B's *in-memory* namespace), G1 credits B and DMM progresses1/3, one coin settled. **No explicit second file save occurs; original account file still holds A's VALID ACTIVE0 snapshot byte-for-byte**.
3. Unregister B, register new C after a REAL `FilePlayerRepository.load` from unchanged saved A file and `PlayerSnapshotCodec.applyValidated` (not test-only namespace injection). World still retains `latestTask(name)` completed from B, so `LocalLabSlayerRuntime.ensureRestoredOwned` marks C `PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME` and returns no exposed G4 task even though persisted bytes are perfectly codec-valid.
4. Execute actual G1 due-time delayed respawn, verify newly spawned NPC origin EXACT C generation, then independently kill/finalize NPC #2 via installed observer. G4 C remains fail-closed **nonthrowingly**, but G1 B1/C1 and two distinct event receipts, DMM2/3, and two separate one-coin settlement receipts/aggregate quantity2 succeed. Disk file remains byte-identical original ACTIVE0 after both terminal events. No old NPC replay or phantom extra reward.

Marker `G1868_DISK_STALE_VALID_ACTIVE_G4_COLLISION_ISOLATION_PASS`.

## Authority, safety and CI

This is a **CUSTOM_LOCALLAB lifecycle inconsistency test**, not original SpawnPK Slayer semantics or a policy fix. Real file-backed snapshot operations are **explicit test-owned API calls**; no authenticated TCP login, LocalSession final save, JVM owner-token admission, prepared/strict writer, cross-process crash, automatic account recovery, forged client input, or durable event outbox. Do NOT infer that actual production logout should or did leave this precise file state; its staleness is deliberate in the fixture. No automatic task transfer/repair should be introduced without owner/source authority.

Diff vs #2648 exactly **three files**: dedicated new `G1868SlayerStaleFileCollisionTerminalIsolationTest.java`, `server/build.gradle` ONE focused manifest entry plus both count assertions **514→515 unique**, and this note. ZERO production Java, G21.205 account save/lease, older gameplay tests, client/default/main changes. Fresh exact-head CI must prove G1868, G1867, G1866, G21205, G21184 markers, 515/515 focused, Java11 major55 and full Gradle. Parent green does NOT certify this head. Chat1 can import only these three files if useful; avoid duplicating older stacked G18 work.

[Gameplay outbox #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), [original kill-credit authority #2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558) and account issues #2540/#2559 remain OPEN.