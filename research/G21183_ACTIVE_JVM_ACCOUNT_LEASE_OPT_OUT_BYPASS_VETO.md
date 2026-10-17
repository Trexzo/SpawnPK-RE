# Chat 1 G21.183 — active JVM token cannot be bypassed by opting out mid-session

## Certified parent

G21.182 [Draft #2561](https://github.com/Trexzo/SpawnPK-RE/pull/2561), exact SHA `3f92f5d35db4e1c8416a54e6cc97e852053960d0`, [Actions #38541722199](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38541722199) SUCCESS Java 11 major55, **482/482** focused, full Gradle. G21.182's opt-in/default-OFF JVM lease deliberately remains stranded after an unconfirmed final write. It is **not** durable across a process restart.

## Concrete G21.183 source gap and narrowly scoped fix

The G21.181 `LocalJvmAccountSessionLease.acquire` previously began with `if(!Boolean.getBoolean(PROPERTY))return null;`. If an already-owned named account had acquired an opt-in token while the flag was TRUE, and the property was then switched FALSE during that still-active or stranded token, another isolated World could skip the token map check completely. It could hydrate the SAME underlying profile file and potentially save an independent state over the first World. This was inconsistent even with G21.181's intentionally limited *same-JVM active-token* contract.

G21.183 **does not** make arbitrary runtime property transitions safe or set the feature on by default. It changes only token-acquisition routing:

- Capture the opt-in flag once at acquisition start.
- If disabled and the map is empty, retain the prior fast-path return without touching the repository.
- If disabled while *any* active token exists, resolve the actual account path and enter the existing synchronized map.
- Reject when the resolved same account path already has a token, even when the flag was switched OFF.
- If the resolved path is distinct and the flag remains OFF, create no new token. Retain the former default-off policy for unrelated accounts.
- A token already held by a running `LocalSession` is still released only on confirmed final `SaveTicket` success. Failed/uncertain final saves remain JVM-memory negative, with NO automatic release.

This conservatively closes only the **pre-existing active token / opt-out bypass** gap. If an account had been admitted without a token while the feature was disabled, then the property is enabled afterward, no cross-session correctness guarantee is made. Cross-process writers, file-path resolver drift, direct legacy/raw `save()`, and crash/power-loss handling remain entirely outside the scope.

## G21.183 real socket acceptance target

`G21183CoreSocketActiveLeaseFlagOffVetoTest` uses two simultaneously running isolated Worlds with the same ordinary named account and actual local TCP `LocalSession` fixtures (synthetic revision317 ISAAC). World A authenticates while opt-in TRUE and holds one token. It performs a World-owned mutation `A_GUARDED`. The property switches FALSE while A is still online. World B's real socket attempt to the same account must **not** receive success or create membership; A and its token remain intact. World A then disconnects and completes a healthy final save, releasing the token despite flag OFF. Now World B may authenticate with default-off behavior (no new token) and must hydrate A's saved value, mutate `B_DEFAULT_OFF`, and final-save successfully. Check zero JVM account tokens, publication leases/watches, missing `src` alias and no unexpected persistence task failures.

Focus count **482→483**; dedicated Gradle `g21183CoreActiveLeaseFlagOffRegression`. Exact-head hosted Java11 major55, 483/483 current-train focused and full Gradle required. No result claimed until CI finishes.

## Safety boundaries

- Default remains OFF; do not advertise cross-process writer exclusion or a durable negative intent.
- No generic crash-safe multi-file epoch, signer custody, original v308/v309 graphical-client proof, or reconstructed historical SpawnPK behavior.
- No replay, grant, settlement, cleanup, rollover, pruning, unlink, session release or ACK authorization. Frozen R25 #1847 and G21.87/G21.88 unchanged.
- No Chat 2 gameplay code, Chat 3 client code, or `main` merge. Review standing shared coordination [#2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526). Durable negative [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and cross-World ownership [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) still OPEN. PR Draft/unmerged.
