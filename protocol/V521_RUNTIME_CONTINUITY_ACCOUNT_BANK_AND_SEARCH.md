# v5.2.1 runtime continuity, persistent account, bank-close sync and native Spawn search

## Opcode 202

The live v5.2 stream paused at decoded client packet sequence 377 on opcode 202.

Static re-audit of the exact pinned client establishes the writer as:

`fv.a(202)`

with no subsequent payload writes before the next packet writer. Schema: `FIXED0`.

LocalLab now consumes opcode 202 as a zero-length long-session keepalive. This is a narrow evidence-backed addition; truly unknown packet schemas still pause instead of guessing lengths.

## Native item search

The item finder is client-native. LocalLab only supplies the normal Spawn shortcut and accepts the emitted command.

Known native surfaces:

- root 67027
- search button 64029
- search/status text 64033
- result container 64071
- result widgets 70000+
- up to 200 results
- search length >= 3

Quick spawn emits `::tabitem itemId amount` through client command packet 103.

## Persistent opensrc account

Canonical user: `opensrc`.

Login aliases accepted for the same persistent localhost account:

- `opensrc`
- `localtest` (migration alias)

State file: `server/data/accounts/opensrc.properties`.

The state file is runtime-created and excluded from patch payloads.

## Bank overlay close synchronization

Bank overlay inventory: 5064.
Normal inventory: 3214.

On bank close, LocalLab now emits a fresh packet-53 normal inventory container for 3214 after closing bank state. This prevents a real server inventory mutation from being hidden by stale normal-tab client state.

## Dyed Vitur

Current embedded catalogue row:

`24023  Scythe of vitur (dyed)  ... clone=21566`

Slot and weapon-pose metadata are inherited from resolved base weapon 21566. This avoids a one-off 24023 hardcode.
