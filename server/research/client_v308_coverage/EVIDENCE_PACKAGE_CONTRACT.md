# Chat 4 Exact v308 Evidence Package Contract

Chat 4 asks one question:

> **What does the real SpawnPK client/cache prove?**

This branch is research-only. It must not introduce gameplay mechanics, runtime
architecture, persistence policy, or inferred original-server rules.

## Primary authority

Exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
main      rs.gui.Launcher
revision  308
```

Do not confuse that authority with the older LocalLab regression fixture:

```
6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662
```

The older fixture may remain valid for inherited regression coverage, but it is
not authority for claims about the exact-current client.

## Authority vocabulary

Every substantive claim in a Chat 4 package must use one or more of:

- `EXACT_CURRENT_CLIENT` — statically or dynamically proven by the exact
  854f... v308 client.
- `EXACT_CURRENT_CACHE` — proven by the cache/config/interface/item/NPC/model/
  animation/GFX/sprite data used by that client.
- `HISTORICAL_CORROBORATION` — screenshot/video/archive/older SpawnPK evidence
  that supports presentation or wording but is not current-client proof.
- `LOCAL_RUNTIME_OBSERVATION` — observed in an isolated LocalLab runtime.
- `UNKNOWN_SERVER_AUTHORITY` — the client/cache cannot prove the original
  authoritative server rule.
- `LOCAL_LAB_POLICY` — an explicit LocalLab decision. This is never to be
  presented as recovered SpawnPK behavior.

Do not silently upgrade inference into exact authority.

## Required package format

Each recovered system gets one evidence file with this shape:

```
SYSTEM
<name>

STATUS
CLOSED | PARTIAL | OPEN | BLOCKED

AUTHORITY
EXACT_CURRENT_CLIENT
EXACT_CURRENT_CACHE
HISTORICAL_CORROBORATION
...

CLIENT CONTRACT
- exact C2S writers
- exact S2C readers
- interface/widget/config/state-machine contracts
- cache definitions
- presentation fields

SERVER SEMANTICS PROVEN
- only facts the evidence actually proves

SERVER SEMANTICS UNKNOWN
- UNKNOWN_SERVER_AUTHORITY: ...

FILES / METHODS
- jar classes/methods/fields
- cache/archive/config paths
- repository evidence files

TEST VECTORS
- exact byte payloads
- IDs
- expected decode/presentation output

CONFLICT / DEPENDENCY
- Chat 2 core/runtime dependencies
- Chat 3 gameplay/domain dependencies
- integration ordering constraints

READY FOR CHAT 2
yes/no + exact reason

READY FOR CHAT 3
yes/no + exact reason
```

## Research rules

1. Prefer exact-current client/cache proof over generic RuneScape knowledge.
2. Record negative findings. “No writer/preload path exists” can be important.
3. Keep transport identity internal; do not turn opcodes/widgets into public
   gameplay API merely because the client exposes them.
4. If a fee, requirement, reward, cooldown, restriction, probability, server
   state transition, persistence rule, or anti-abuse rule is not proven, label it
   `UNKNOWN_SERVER_AUTHORITY`.
5. Test vectors should be byte-exact where possible.
6. Preserve provenance down to class/method/field and cache source.
7. Do not modify another chat's branch.
8. Chat 4 may open research branches and evidence PRs; Chat 1 remains the only
   integration/merge owner.
