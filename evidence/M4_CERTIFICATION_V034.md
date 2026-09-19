# M4 certification — v0.3.4

M4 is promoted from PARTIAL to CERTIFIED based on the localhost-only v0.3.3 runtime run plus prior exact static/parser checks.

Runtime evidence established:

- local prelogin/login accepted on 127.0.0.1:43594;
- bootstrap sequence 249 -> 73 -> 81 sent;
- region rendered;
- local humanoid body rendered coherently;
- bogus overhead-icon sentinels corrected;
- packet-81 idle ticks continued repeatedly;
- loading acknowledgements (121) continued;
- focus packets (3) decoded without losing ISAAC alignment;
- multiple client walking packets (164) were framed and decoded while movement remained observe-only;
- no reconnect login type 18 occurred during the certified run window.

Prior offline proof retained:

- exact 249/73/81 decoder schema audit;
- appearance parser parity against the pinned client;
- complete private packet-81 decoder parity against the pinned client.

Certification scope:

M4 certifies stable world/bootstrap + local self appearance/update sequencing. It does **not** certify authoritative movement, NPCs, inventory, combat or other game systems. Those begin with M5.
