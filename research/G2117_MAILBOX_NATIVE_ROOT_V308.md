# G21.17 — exact pinned-v308 Mailbox root and safe LocalLab open

## Pinned binary evidence

SHA-256 verified against the archived exact-v308 client JAR:

`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`

From `javap -c -p -classpath client.jar rs.n.c.c.a`:

- In `public void a()`, bytecode offset 52 begins with `sipush 32019`.
- The next instruction calls `d:(I)Lrs/n/e;`, then the interface hierarchy builder `new rs/n/d/c` attaches widgets beneath that node.
- Child `32020` is the main mail background, `32023` is the scroll-list surface, and children `32166`/`32167` host subject/reward detail.
- Existing independently certified ScriptPacket subtype31 selectors operate on these exact inbox and reward controllers. See `Trexzo/SpawnPK-Client` research `r2-chat2-v308-mailbox-interface-r124.md` and `r2-chat2-v308-mail-inbox-r293.md`.
- The native MailNotificationOverlay emits `::mail`, exact C2S103. See `r2-chat2-v308-mail-coffer-action-prompts-r113.md`.

These bytecode facts establish **the exact current-client Mailbox root widget ID 32019**. They do **not** establish original SpawnPK server implementation or the policy that decides when to open that interface.

## Exact transport reused

`BootstrapPackets.interface97(32019)` encodes exact S2C97 (fixed-size 2, big-endian) as:

```text
S2C opcode: 97, ISAAC-encoded on the live wire
fixed payload: 0x7D 0x13 (32019)
```

The same protocol is used by established native UI opening paths, e.g. `NativeItemLibraryService.open` and `LocalPkRatingsUiHandler.open`.

## G21.17 LocalLab policy (NOT reconstructed original server order)

The server opens the exact client root first and publishes the certified bounded inbox projection:

1. `S2C97(root=32019)`
2. `S2C250(subtype31, operation0 CLEAR)`
3. `S2C250(subtype31, operation1 APPEND)` for each of at most 35 validated messages
4. `S2C250(subtype31, operation5 FINALIZE)`

This is labeled `CUSTOM_LOCALLAB_G2117` publication order, even though each individual packet and widget identity is exact pinned-client authority.

`ServerPacketWriter.beginBatch/endBatch/abortBatch` stages these packets so domain/preflight exceptions do not publish an orphaned root. I/O failures after commit are not reversible.

C2S103 `::mail` invokes the native root-opening bridge; `::mail sync` remains an explicit rootless development path. Exact C2S185 row clicks and Refresh only pass through the active owner/generation-fenced Mailbox view. C2S130 closes that scope.

No original reward generation, expiry, bank/inventory settlement, claim authorization, automatic read behavior, or original live server opening order is inferred. Hosted CI verifies packet bytes and source wiring, **not** native interactive runtime acceptance.
