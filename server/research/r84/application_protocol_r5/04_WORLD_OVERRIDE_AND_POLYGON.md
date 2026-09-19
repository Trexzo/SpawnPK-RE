# World-side client application protocols

## Subtype 2 — Dynamic scene object override

Operation 0 removes one override:
`u16 worldX, u16 worldY, u8 plane, u8 ignoredCurrentClientByte`

The key is `(worldX, worldY, plane)`. The client removes its stored override and calls the scene updater with object id -1, orientation 0, type 10.

Operation 1 adds/replaces one override:
`i32 objectId, u16 worldX, u16 worldY, u8 plane, u8 objectType, u8 orientation`

The client stores all six semantic fields and immediately invokes its normal scene-object updater.

Operation 2 clears every stored override matching one object definition:
`i32 objectId`

Each matching scene object is removed and its override record deleted.

## Subtype 12 — World tile polygon/highlight overlay

Operation 0 adds/replaces a world-tile overlay:
`u8 fillR,G,B,A; u8 outlineR,G,B,A; u16 worldX; u16 worldY; u8 hasLabel; [string label]`

Operation 1 removes by world coordinate:
`u16 worldX; u16 worldY`

This is exact client rendering authority, not proof that any particular production world tile should receive an overlay.
