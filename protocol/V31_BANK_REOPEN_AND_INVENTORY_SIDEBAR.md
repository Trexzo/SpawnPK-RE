# v3.1 bank reopen + visible inventory sidebar

Authority client SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`

## C2S opcode 130

Static writer proof in pinned `rs.Client.bQ()`: opcode writer is called with 130 and
no payload field writes follow. Exact framing: fixed 0. Semantic role: client
interface-close notification. v3.1 consumes it, marks the local bank closed, and
keeps ISAAC alignment.

## S2C packet 71

Fixed length 3. Client handler reads:

- `A()` -> interface/root id
- `N()` -> sidebar tab index (`(wire-128)&255`)

For inventory: root `3213`, tab `3`; wire payload `0C 8D 83`.

## S2C packet 106

Fixed length 1. Client handler reads `O()` (`(-wire)&255`) into the selected-tab
field. For tab 3 the wire payload is `FD`.

## Bank open contract

`71(3213,3) -> 106(3) -> 97(5292) -> 53(5382) -> 53(3214)`.

The inventory item widget `3214` is independently runtime-proven in the current
client; v3.1 adds the missing sidebar-root mapping so its packet-53 contents can be
rendered in the normal inventory pane.
