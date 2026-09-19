# v5.1.1 SpawnPK-local scythe animation ID fix

## Live failure

v5.1 sent packet-81 pose:

`8057, 823, 819, 820, 821, 822, 824`

The first live screenshot showed the Bloodrend/scythe pose badly displaced. The live log independently recorded the same `8057` stand value, so this was a deterministic pose-selection failure rather than packet framing or movement corruption.

## Root cause: two animation ID spaces

The exact current SpawnPK `configs/a.bin` was decoded as MessagePack.

Relevant records:

```text
key/localId 15692
ref         scythe_stand
osid        8057
frames      12

key/localId 15552
ref         scythe_attack
osid        8056
frames      22
```

The pinned client's `rs.t.a.a` custom-animation loader constructs the animation definition using the map key as the definition/local ID. Its `osid` field does not replace that ID; it marks the sequence as OSRS-backed/source-derived.

Therefore:

```text
8057  = source/original OSRS scythe stand id
15692 = current SpawnPK client-local scythe_stand id
```

v5/v5.1 accidentally sent the source ID.

## v5.1.1 packet-81 profile

```text
stand       15692
stand-turn    823
walk          819
turn180       820
turn90CW      821
turn90CCW     822
run           824
```

The seven-field architecture remains correct. Only the recovered data value was wrong.

No separate scythe walk/run/turn entries were found in the current custom animation config, so the remaining six packet-81 values stay on the proven player baseline. `scythe_attack=15552` is recorded for later combat reconstruction but is not a packet-81 field.

## Safety/correctness boundary

- do not send raw OSRS `8057` to this SpawnPK client for the scythe stand;
- do not put attack `15552` into packet 81;
- do not change item-model rotations/offsets as a substitute for player pose;
- do not infer equipment slots from `Wield` alone;
- future imported animation metadata must distinguish `localId` from `osid`.
