# v5.2.2 — Native Search, Crestbearer equipment, and full C2S framing

## Native Spawn/Search
The pinned client packet-71 handler rewrites interface id `0` to root `67027`. The real SpawnPK UI places the magnifying-glass Spawn/Search tab in the bottom-right position, which is sidebar index `13` in the fixed 0..13 tab layout. v5.2.1 incorrectly mapped root 67027 to index 10. v5.2.2 corrects the index to 13. Search/filter/result construction remains client-side and right-click spawning continues to emit `::tabitem <id> <amount>`.

## Crestbearer equipment
The first live v5.2.1 run proved the client click path: `opcode 41` was emitted for 23141/23142/23143. The server rejected them only because the fail-closed equipment resolver had no slot anchors. v5.2.2 adds explicit custom-item anchors instead of weakening the resolver into a `Wear => guess` rule.

- 23141 Crestbearer helmet -> HEAD / full helm
- 23142 Crestbearer platebody -> CHEST / full body
- 23143 Crestbearer platelegs -> LEGS
- 22105/22106/22107 mage set -> HEAD/CHEST/LEGS
- 28708/28707/28706 dyed melee -> HEAD/CHEST/LEGS
- 28872/28871/28870 hide set -> HEAD/CHEST/LEGS
- 19050 Completionist cape -> CAPE; 23063 Grand comp. cape (melee) inherits through clone

Full-body chest coverage suppresses the default arms identity-kit position in packet 81, just as full-helm coverage suppresses hair/beard.

## 86/86 C2S framing
The exact-client opcode audit is integrated. Semantic handlers remain only where LocalLab actually implements the behavior. Every other exact-current-client opcode is consumed according to its authoritative FIXED/VARBYTE frame and logged `framingOnly=true`. This prevents legitimate UI/combat/social packets from permanently desynchronizing the stream while preserving fail-closed behavior for opcodes outside the pinned client's writer authority.
