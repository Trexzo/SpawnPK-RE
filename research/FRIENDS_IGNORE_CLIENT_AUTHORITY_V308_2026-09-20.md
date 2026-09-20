# Exact-current v308 friends / ignore-list authority

Date: 2026-09-20

Exact-current client SHA-256:

`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`

Source audited directly:

`rs.Client`

This note records client authority only. It does not promote client-side
preflight or UI limits into original-server policy.

## Friend add

Exact method:

`Client.a(long nameKey)`

The client rejects the request before sending when:

- `nameKey == 0`
- current friend count is already 350
- `nameKey` already exists in the local friend list
- `nameKey` exists in the local ignore list
- decoded/canonicalized name equals the local player's own name

After passing those checks, the client appends local friend state and emits:

```text
opcode 188
i64 nameKey
```

The packet writer calls are exact:

```text
rs.x.e.a(188)
rs.x.e.a(long)
```

Authority: `EXACT_CURRENT_CLIENT`.

## Friend remove

Exact method:

`Client.f(long nameKey)`

If `nameKey` is present, the client removes the matching local friend row,
compacts the arrays, and emits:

```text
opcode 215
i64 nameKey
```

No outbound packet is emitted when the key is absent.

Authority: `EXACT_CURRENT_CLIENT`.

## Ignore add

Exact method:

`Client.h(long nameKey)`

The client rejects the request before sending when:

- `nameKey == 0`
- current ignore count is already 100
- `nameKey` already exists in the local ignore list
- `nameKey` exists in the local friend list

After passing those checks, the client appends local ignore state and emits:

```text
opcode 133
i64 nameKey
```

The audited ignore-add path does not independently prove the same self-name
rejection used by friend add.

Authority: `EXACT_CURRENT_CLIENT`.

## Ignore remove

Exact method:

`Client.i(long nameKey)`

If `nameKey` is present, the client removes the matching local ignore row,
compacts the array, and emits:

```text
opcode 74
i64 nameKey
```

No outbound packet is emitted when the key is absent.

Authority: `EXACT_CURRENT_CLIENT`.

## Exact local capacities

The current client contains explicit local limits:

```text
friends = 350
ignores = 100
```

The corresponding client messages are:

```text
Your friendlist is full. Max of 350 users.
Your ignore list is full. Max of 100 hit
```

These are exact client-side constraints.

They are **not** automatically promoted to authoritative server storage
capacity. A future domain service should receive capacity as server policy and
may choose 350 / 100 for exact-client-compatible operation.

## Cross-list exclusivity

Both audited add paths check the other list before sending:

```text
friend add  -> rejects if already ignored
ignore add  -> rejects if already a friend
```

This proves exact client preflight behavior.

Whether the production server independently enforced the same invariant is not
proven by the client alone.

## Friend status rendering

The native friend renderer consumes integer status values and renders at least:

```text
0  -> @red@Offline
10 -> @gre@Online
11 -> <img=203> @yel@AFK
```

The friend-list UI also has top-level presentation states including:

```text
Loading friend list
Connecting to friendserver
Please wait...
```

This establishes exact presentation vocabulary only. The authoritative source,
lifecycle and server semantics of presence status remain server authority.

## UI input entry points

The current client exposes native input prompts for:

```text
Enter name of friend to add to list
Enter name of friend to delete from list
Enter name of player to add to list
Enter name of player to delete from list
```

The first pair feeds the friend methods above. The second pair feeds the
ignore-list methods above.

## Safe architecture consequence

The client proves four semantic intents:

```text
ADD_FRIEND
REMOVE_FRIEND
ADD_IGNORE
REMOVE_IGNORE
```

with exact transport:

```text
ADD_FRIEND    -> C2S188 + i64 name key
REMOVE_FRIEND -> C2S215 + i64 name key
ADD_IGNORE    -> C2S133 + i64 name key
REMOVE_IGNORE -> C2S74  + i64 name key
```

A maintainable LocalLab design should keep those packet identities in protocol
adapters and own friend/ignore membership in a protocol-independent social
domain service.

## Not proven here

Do not infer from this audit:

- original server persistence format
- server-side capacity limits
- mutual-friend relationships
- privacy/public/private-message policy
- private-message authorization
- ignore effects on chat/trade/follow/duel
- friend presence source of truth
- cross-world routing
- clan or friends-chat membership
- report/block behavior
- account-level moderation state

Those require separate server/runtime authority.

## Coordination

This research lane is intentionally separate from:

- clan / Clan Wars work (#169)
- packet-to-domain routing (#17)
- persistence (#15)
- public content API (#16)
- exact S2C126 work (#148/#149)

No production behavior is changed by this note.
