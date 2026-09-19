# SpawnPK LocalLab v5.18.5 — Engine R8.5 Implementation Exhaustion + Voidglass R3

This is a cumulative offline LocalLab upgrade from the certified v5.18.4.2 baseline.

## Required installed baseline

Server SHA-256:

`417135d2079bd5134017fd74ee48d76a5ca2d09fdcf7d0472e24fce48c56126d`

R8.5 output server SHA-256:

`589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c`

Pinned exact client remains:

`6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`

## Main changes

- S2C250 operation-level static closure promoted to **43/43**.
- Typed application UI publishers and local/dev fixture service.
- Eight remaining generic C2S interaction opcodes promoted out of framing-only into exact normalized events, while unknown outcomes stay fail-closed.
- Existing C2S16 / definition-driven inventory router / Override cosmetic system retained.
- Voidglass R2 replaced by R3: valid item 29999, old 32760 retired, no Hydra model/anims/GFX, four native-model compositor candidates, new VOIDGLASS RIFT proc.
- Normal `RUN_ALL_LOCAL_LAB.ps1` is patched safely to auto-select the highest installed Java >=11, eliminating the Java-8 JNI failure.
- `server\data` is snapshotted and must remain byte-identical through installation/certification.
- LocalLab client JARs are hash-guarded and untouched.

## Safe application fixture command

`::appfixture <fixture>` publishes clearly local/dev-only presentation fixtures for recovered client state machines. These fixtures do not assert production mailbox contents, prices, rewards or gameplay rules.

## Install behavior

The installer:

1. verifies every package file against `SHA256SUMS.txt`;
2. refuses non-MAIN lanes or live LocalLab Java processes;
3. requires the exact v5.18.4.2 server hash;
4. verifies the pinned current client;
5. auto-selects Java >=11;
6. preflights the R2 -> R3 live config migration before changing anything;
7. certifies the installed v5.18.4.2 baseline;
8. creates a `.v5185-r85-backup-*` rollback snapshot;
9. installs the R8.5 server/source/scripts;
10. injects the Java >=11 selector into the normal launcher after its PowerShell param block;
11. migrates `i.bin/e.bin` to Voidglass R3;
12. runs the full 179-test contract and final verifier;
13. proves `server\data` and LocalLab client JARs remained unchanged;
14. does **not** auto-launch.

Use `ROLLBACK_R8_5.ps1` if manual rollback is required.
