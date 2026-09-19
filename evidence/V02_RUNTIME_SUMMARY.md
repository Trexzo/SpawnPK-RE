# v0.2 runtime summary (sanitized)

The user's localhost-only v0.2 run established:

- loopback game listener accepted the client on `127.0.0.1:43594`;
- normal login type 16 / revision 317 completed locally;
- local success response was accepted;
- first client packet decoded as opcode 185 / widget 912;
- the server emitted candidate packets 249, 73 and 81;
- the client rendered the game region;
- recurring valid empty packet-81 ticks continued through at least tick 275 with no
  reconnect shown in the supplied run.

Strict interpretation: this is strong session-stability and region-rendering evidence, but
M4 remains `PARTIAL / NOT CERTIFIED` until player appearance/update-mask processing is
runtime-validated and the complete initialization state is coherent.
