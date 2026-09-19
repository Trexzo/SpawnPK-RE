# SpawnPK Client Static Audit R3 — exact-current control/VM/settings/routing closure

Date: 2026-09-18

This package continues the R2 feature-discovery audit against the exact current SpawnPK client rather than re-auditing the already-closed equipment/projectile/constructed-region boundaries.

## Exact inputs

- `client.jar` v150 SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`
- `configs.zip` v110 SHA-256: `1897cdd733bbb87084a948c391d33ff3ed8f22ccf057ef5ccd204575195adf70`
- `cache.zip` v67 SHA-256: `87d35c168509aac7ff54b744818b007b6549b3fa1ee2c40fdcfc1352ce8d365d`
- Client Discovery R2 source SHA-256: `9150bbb90b2d73cfba32e6afd709ea0f01d9b4a75309a7fcaabecb000cee2ba6`

## R3 closes

1. S2C126 is structurally a generic `string + int targetKey` update bus. Every registered interface controller sees the `(targetKey,string)` pair before global control-token handling.
2. Collection Log is the concrete current controller that overrides that bus, with target keys `54315`, `54421`, `54422` recovered exactly.
3. Argument-bearing global controls are mapped for marketplace, Item Library, Knowledgebase, login reward index, clan-chat clearing and related controls.
4. The exact `X` widget-expression VM is decoded for opcodes 0..20.
5. `bk` and `bl` are dead/unread in this exact current client: Construction writes them, but no class reads them. They must not be treated as active comparison authority.
6. Current settings controls are joined end-to-end: raw varp -> internal config index -> widget -> state field -> default -> persistence key -> client-side side effect.
7. Generic interface button menu action `315` sends C2S opcode `185` carrying the widget ID after controller hooks. Clan Wars and gambling selector widgets are mapped to that contract.
8. R2 achievement bootstrap IDs are cross-linked against exact current config/base object definitions where statically available.

## Important boundary

This package recovers client contracts and local behavior. It does **not** infer production server outcomes. Marketplace rows/prices, mailbox contents, party membership authority, achievement reward awarding, Clan Wars match rules/results, gambling RNG/results and other production business rules remain server-owned unless separately observed.


## R3.1 additive closure

R3.1 preserves the complete R3 package and adds the two findings completed immediately after R3 sealing: the exact menu-action/C2S router and concrete widget-expression `X` producer semantics. No earlier R3 file was removed.
