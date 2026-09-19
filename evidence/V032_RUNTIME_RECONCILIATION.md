# v0.3.2 runtime reconciliation

Status remains **M4 PARTIAL / NOT CERTIFIED**.

The v0.3.1 localhost run established:

- loopback auxiliary HTTP startup succeeds;
- local game login succeeds;
- 249 -> 73 -> 81 candidate is accepted far enough to render the region and maintain a stable player-centered camera;
- self overhead icons are rendered at the local entity position, proving the client recognizes the local player entity;
- the v0.3 all-zero equipment appearance has no renderable body;
- the zero-valued bd/bf appearance fields select visible overhead icon states rather than 'none';
- client opcode 103 is a var-byte command-text family; the observed startup stream contains gpuflagon, queuedswitching and soundon;
- with exact 103 framing, subsequent ISAAC alignment yields repeated fixed-zero opcode 0, fixed-zero opcode 121, and then opcode 164 movement-family traffic.

v0.3.2 changes:

- bd=255 and bf=255 for no overhead status icons;
- default classic identity-kit body candidate in the seven character-design equipment slots;
- opcode 103 exact decoder;
- opcode 0 and 121 fixed-zero decoders;
- 164/98/248 variable-byte framing with movement observation only;
- unknown client opcodes still pause rather than guess;
- movement is **not** applied server-side.
