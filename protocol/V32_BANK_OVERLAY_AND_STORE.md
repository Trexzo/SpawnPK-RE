# v3.2 exact bank overlay + Store actions

Pinned-client cache proof:

- `5292` main bank root.
- `5382` bank item widget, 8x44, actions Withdraw 1/5/10/All/X.
- `5063` bank inventory overlay root.
- `5064` bank inventory item widget, 4x7, actions Store 1/5/10/All/X.
- `3213/3214` is the normal inventory and is not the bank overlay.

Server packet 248 is the exact main+side interface opener in this client. The
branch reads `T()` into main root `cH` and `A()` into side root `fu`, so v3.2
sends `248(5292,5063)`, followed by packet-53 updates for `5382` and `5064`.

The generic item action menu maps W[0..4] to action ids 632/78/867/431/53,
which serialize through client opcodes 145/117/43/129/135.  The same packet
schemas are therefore used for Withdraw on widget 5382 and Store on widget
5064; the server distinguishes semantics by widget id.

C2S opcode 41 is exact fixed-6 (`itemId`, transformed slot, transformed widget)
and is observed only. This prevents normal inventory item clicks from pausing
framing without inventing equip semantics.
