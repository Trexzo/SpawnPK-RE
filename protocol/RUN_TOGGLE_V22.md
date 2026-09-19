# v2.2 persistent run-toggle reconstruction

Live v2.1 evidence showed every run-orb click emits client -> server opcode `185` with widget id `152`, while later normal movement packets continue to serialize `run=0`.

Static bytecode explains this: the movement writer serializes `rs.C.hY[5]`, and keyboard mapping in `rs.C` maps Java key code `17` (Ctrl) to `hY[5]`. Therefore the movement packet's run bit is a transient Ctrl-key override, not the persistent run-orb setting.

v2.2 implements the missing server-owned state:

- opcode `185`, widget `152`: toggle persistent running ON/OFF;
- ordinary `164/98/248` paths execute two tiles per 600 ms tick while persistent running is ON even if their packet run bit is 0;
- Ctrl-key packet run=1 still runs when persistent toggle is OFF;
- server -> client packet `36` acknowledges config/varp `173` value 1/0; its exact 3-byte payload is validated with the pinned client's real `S()` and `z()` readers;
- packet `110=100` and packet `126`, widget 149=`100%` remain the energy/orb initialization path.

Exact SpawnPK energy drain/regeneration remains intentionally unclaimed in v2.2.
