# Current LocalLab status

Imported baseline: v5.18.5 / Engine R8.5

Server SHA-256:

589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c

Canonical exact-current client SHA-256:

854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6

Historical v307 certification-provenance SHA-256:

6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662

Current deterministic LocalLab derivatives:

- localhost: 01c878a56ee25fb112dfe8b459dbd11ea26cfa8a92a7f287a4e5ee53f673cdbd
- airgap: 83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33

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

## Historical R8.5 issues recorded at initial Git import

The list below is retained as initial-import provenance and is not the current
development blocker list. Current in-flight authority is tracked in GitHub Issues
and Pull Requests.

- RUN_CLIENT_AIRGAP still requires Java selection corrective.
- RUN_ALL child-server launch path requires corrective.
- Make-X fixture opens the wrong interface/root and can disconnect.
- Voidglass R3 native-compositor visuals are rejected as final design.
- Voidglass item/lifecycle framework is retained.
- Future custom pet work should use a true custom model/cache pipeline.

These should be fixed in normal Git commits rather than hidden from
the initial imported baseline.
