# Exact-current login reward client authority — v308

Date: 2026-09-20  
Scope: client authority / research only  
Issue: #187

## Artifact authority

Newest supplied exact-current client:

- SHA-256: `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`
- embedded client-build/config value: 308

Previous LocalLab pinned client:

- SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`
- embedded client-build/config value: 307

The whole-JAR v307 -> v308 comparison changes only `rs/f/a.class`
and only the embedded 307 -> 308 constant. The login/reward classes audited
below are byte-identical across the two builds.

Authority classification for the findings below:

`EXACT_CURRENT_CLIENT`

## Native root and reward container

Current client class:

`rs.n.c.am`

builds interface root:

`50600`

The class creates item container:

`50615`

with four parallel item-container arrays allocated at length:

`20`

This proves a native 20-slot login/daily-reward presentation container.

The same current interface contains exact client copy including:

- `one of the rewards below for logging in daily!`
- `Your next daily reward: @yel@24h 0m 0s`

The `24h 0m 0s` string is client presentation/default copy only. It is not
evidence of authoritative server reset cadence, timezone, or claim eligibility.

## S2C126 LOGIN_REWARD_IDX

The exact current S2C126 global-token dispatcher recognizes:

`LOGIN_REWARD_IDX <int>`

and parses the integer into:

`Client.P`

Exact bytecode flow:

```
payload.startsWith("LOGIN_REWARD_IDX")
 -> split on space
 -> Integer.parseInt(parts[1])
 -> Client.P = parsed value
```

The transport is therefore known exactly at the client effect boundary.

## Whole-JAR Client.P field-reference census

A classfile constant-pool census across all 10,970 JAR entries found references
to the exact field:

`rs/Client.P:I`

in only two classes:

1. `rs/Client.class`
   - constructor initialization
   - S2C126 `LOGIN_REWARD_IDX` setter

2. `rs/l/b/d.class`
   - native widget/item-container renderer

No other stock class references this field.

## Exact renderer join

The renderer consumes `Client.P` only in the login/reward interface context:

- active/root interface: `50600`
- rendered item container: `50615`

For each rendered container slot, the renderer:

- compares the current slot index with `Client.P` for the exact-current
  reward-slot highlight path;
- separately compares slot ordering against `Client.P` in the same
  reward-container rendering branch.

This gives an exact static join:

```
S2C126 LOGIN_REWARD_IDX <n>
        |
        v
Client.P = n
        |
        v
root 50600 / container 50615 renderer
        |
        v
reward-slot progression/selection presentation state
```

The conservative semantic name is therefore:

`LOGIN_REWARD_CONTAINER_INDEX`

This note intentionally does not rename it as "claimed day", "streak day",
"next reward day", or "eligible reward index", because the client renderer does
not establish those server-side meanings.

## What the client does not prove

This audit does not establish authoritative server rules for:

- reward item definitions;
- reward quantities;
- reward generation;
- daily reset timezone;
- authoritative 24-hour cadence;
- streak advancement;
- skipped-day behavior;
- catch-up behavior;
- claim authorization;
- claim idempotency;
- C2S claim transport;
- inventory/bank settlement;
- mailbox/coffer interaction;
- account persistence.

No production `DailyRewardService` should be inferred solely from this
presentation contract.

## Architecture consequence

Treat login rewards as another split presentation/domain boundary:

```
unknown server reward authority
        |
        +--> semantic future reward state, when independently justified
        |
        +--> S2C126 LOGIN_REWARD_IDX adapter
        |
        +--> normal item-container publication for widget 50615
```

Do not place reward mechanics, reset scheduling, or item settlement inside the
S2C126 application-control layer.

## Coordination

This research note does not modify or overlap:

- #157 semantic usage quotas;
- #162 mailbox/offline reward delivery;
- #178 Daily Challenge assignment;
- #182 Daily Money Making tracking;
- #148/#149 S2C126 production implementation.

No production code, tests, packet writers, gameplay state, persistence, or
workflows are changed by this research lane.

## Runtime acceptance

This static exact-current audit does not replace the repository's pinned-client
179/179 runtime authority and makes no claim that v308 has passed that external
LocalLab gate.
