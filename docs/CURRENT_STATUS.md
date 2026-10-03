# Historical LocalLab status — sealed R8.5 baseline (2026-09-19)

Imported baseline: v5.18.5 / Engine R8.5

> This file is the sealed initial-import snapshot, not the live development status.
> Its v307 client pin is historical certification provenance.
> Canonical current runtime authority is exact v308:
> `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`.
> For current operational setup/status use `README.md`, `docs/EXTERNAL_RUNTIME.md`,
> and the active GitHub integration/release queue.

Server SHA-256:

589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c

Historical baseline pinned client SHA-256:

6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662

## Authority hierarchy

1. EXACT_CURRENT_CLIENT
2. EXACT_CURRENT_CACHE
3. LOCAL_RUNTIME_PROVEN
4. HISTORICAL_CORROBORATION
5. INFERENCE
6. UNKNOWN_SERVER_AUTHORITY
7. CUSTOM_LOCALLAB

Do not promote inferred or LocalLab-only behavior to production
SpawnPK authority.

## R8.5 known issues at initial Git import

- RUN_CLIENT_AIRGAP still requires Java selection corrective.
- RUN_ALL child-server launch path requires corrective.
- Make-X fixture opens the wrong interface/root and can disconnect.
- Voidglass R3 native-compositor visuals are rejected as final design.
- Voidglass item/lifecycle framework is retained.
- Future custom pet work should use a true custom model/cache pipeline.

These should be fixed in normal Git commits rather than hidden from
the initial imported baseline.
