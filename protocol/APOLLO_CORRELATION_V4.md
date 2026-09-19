# Apollo r317 historical-reference ledger — v4

Evidence priority:
1. CURRENT_CLIENT_STATIC
2. CURRENT_RUNTIME
3. LOCALHOST_RUNTIME
4. SPAWNPK_OFFICIAL
5. APOLLO_R317_REFERENCE
6. GENERIC_317_REFERENCE

Apollo is a historical implementation aid only. The pinned SpawnPK client wins on every disagreement.

| subsystem | v4 classification | note |
|---|---|---|
| 164/98 walking | UNCHANGED/CORRELATED | ordinary r317 family; exact SpawnPK transforms independently verified |
| 248 minimap walking | MODIFIED | ordinary path core + exact SpawnPK/client 14-byte extension |
| 103 commands | CORRELATED | command channel; SpawnPK adds custom commands such as bank tabs |
| 185 button | CORRELATED | exact pinned client fixed2 widget id |
| 132 first object action | CORRELATED | exact pinned client fixed6 transforms |
| 130 close interface | CORRELATED | exact pinned client fixed0 |
| 97/248 interface opening | MODIFIED | bank uses current-client main+side packet248 path |
| 53 containers | CORRELATED | extended quantity encoding verified against pinned client readers |
| 81 player synchronization | MODIFIED/VERIFIED | SpawnPK appearance/body contract recovered from exact client |
| 65 NPC synchronization | CORRELATED/VERIFIED | minimal one-NPC spawn parity-tested against exact client |
| bank placeholders | CUSTOM | widget39971 and zero-qty slot preservation are SpawnPK-specific |
| bank tabs | CUSTOM | setbanktab/swapbanktab over opcode103 |
| Blood Fountain | CUSTOM CONTENT | current custom NPC 1799; exact production position unresolved |
| World Tournament portal | UNKNOWN/CUSTOM CONTENT | identity/placement unresolved; v4 does not guess |
