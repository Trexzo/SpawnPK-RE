# M4 runtime / reconnect diagnosis

## Runtime result

The real localhost-patched client accepted local login and rendered the map after server packets `249`, `73`, `81`.

## Why v0.1 reconnected after about 15 seconds

Pinned current-client bytecode in the main game loop increments `Client.lP` by one each loop. If `lP > 750` and the reconnect option is enabled, it calls the reconnect path.

Inside `rs.Client.bP()`, immediately after a complete inbound packet is read, the client executes `Client.lP = 0`.

Therefore v0.1's one-shot bootstrap loaded the world but then provided no complete inbound packet for ~750 loops, causing the observed reconnect.

## v0.2 fix

In `--bootstrap` mode, send valid minimal packet `81` payload `00 7F F0` every 600 ms. Static packet-81 parsing proves the payload consumes exactly 3 bytes and represents: local player unchanged, zero existing other players, 2047 sentinel.
