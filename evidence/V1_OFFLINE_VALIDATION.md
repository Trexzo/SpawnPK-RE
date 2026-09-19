# v1 offline validation

v1 is the first M5 authoritative-movement build on top of the certified v0.3.4 M4 baseline.

Offline tests passed before packaging:

```text
MOVEMENT_STATE_PASS walkExpanded=6 runTiles=2 final=3093,3493
M5_PACKET81_CLIENT_PARITY_PASS bootstrap=55,55 walk=56,55->56,56 run=57,54
M5_AUTHORITATIVE_MOVEMENT_INTEGRATION_PASS c2s=164 target=3088,3495 s2c=81 dir=4
APPEARANCE_CLIENT_PARSER_PARITY_PASS
PACKET81_FULL_CLIENT_DECODER_PARITY_PASS
BOOTSTRAP_STATIC_ENCODING_PASS
LOCAL_M4_BOOTSTRAP_INTEGRATION_PASS
CLIENT_PACKET_PROBE_V1_PASS
LOCAL_HANDSHAKE_INTEGRATION_PASS
```

The packet-81 movement parity test invokes the original client's complete private packet-81 decoder through an initial placement, two walking updates and a two-step running update.

The authoritative integration test performs a synthetic loopback login, initializes ISAAC, sends a real encrypted client opcode `164` targeting one tile east, and requires the localhost server to return packet `81` direction `4` on the server tick.

This is strong offline proof of the movement contract. The first real GUI run is still required before M5 is called runtime-certified.
