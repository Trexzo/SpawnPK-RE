# v3 bank/container protocol contract

Authority client SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`

## Open bank

Live bank object: object `26972`, C2S opcode `132`, fixed 6 bytes:
`p(worldX), d(objectId), o(worldY)`.

Local response:
- S2C 97 fixed2: big-endian root `5292`
- S2C 53 var-short: bank container `5382`
- S2C 53 var-short: inventory container `3214`

## Packet 53 exact payload

The client reads `A(widget), A(count)` followed by `y(quantity)`, optional
`Y(quantity32)` when the quantity byte is 255, then `U(itemIdPlusOne)`.
The local encoder is directly parity-tested against those original readers.

## Bank item action schemas

- 145 fixed6: `o(widget), o(slot), o(item)` — Withdraw 1
- 117 fixed6: `p(widget), p(item), n(slot)` — Withdraw 5
- 43 fixed6: `n(widget), o(item), o(slot)` — Withdraw 10
- 129 fixed6: `o(slot), d(widget), o(item)` — Withdraw All
- 135 fixed6: `n(slot), o(widget), n(item)` — Withdraw X (decode only in v3)
- 141 fixed10: `o(slot), d(widget), o(item), g(extraInt)` — Withdraw 14
- 140 fixed6: `o(slot), d(widget), o(item)` — Withdraw All But One

Transforms:
- `d`: BE unsigned short
- `n`: LE unsigned short
- `o`: BE short with low byte +128
- `p`: LE short with low byte +128
- `g`: BE 32-bit integer for this action path

## Deposit carried items

C2S 185 widget `26012` deposits all current local inventory stacks into the
in-memory local bank and refreshes both packet-53 containers.

## Scope

This is a clean-room localhost protocol implementation. It does not contact or
modify any production server/account.
