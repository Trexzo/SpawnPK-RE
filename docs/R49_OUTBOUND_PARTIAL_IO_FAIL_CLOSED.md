# R49 — fail-closed on ambiguous outbound socket writes (no replay)

## Failure found after R48

R48 rejects predictable lethal-loot conflicts **before** a CUSTOM_LOCALLAB
spell consumes Air/Mind runes or publishes an NPC hit. That still leaves
unpredictable I/O failures and JVM crashes. The LocalLab server transmits
v308-shaped, ISAAC-coded game frames through a session-owned
`ServerPacketWriter` to an `OutboundPacketQueue`, then drains that queue
to the accepted TCP connection from `LocalSession.run()`.

The pre-R49 queue removes each packet from its FIFO **before** passing the
byte array to `OutputStream.write`. A socket may accept a prefix of the
packet and then throw `IOException`; the queue previously left the
remaining queued packets available and allowed subsequent offers and
drains. Sending a later packet after a truncated, ciphered packet would
corrupt the client's protocol framing (and risks interpreting an ISAAC
opcode as part of the missing payload). A failed `OutputStream.flush`
is also ambiguous about remote delivery.

**No packet-level retry can safely repair an arbitrary partial write.**

## Exact implementation

`server/src/spk/local/OutboundPacketQueue.java`:

1. Adds one permanent, per-session terminal `IOException` marker
   `terminalTransportFailure`, independent of the existing bounded
   queue overflow state.
2. The drain holds a private `drainLock` across socket writes to
   serialize any concurrent drain callers **without** holding the FIFO
   queue monitor while the slow `OutputStream.write` or
   `OutputStream.flush` is executing. World packet publishers still
   synchronize only briefly for `offer` and queue metadata.
3. On either a partial `write` failure or `flush` failure, the
   terminal state becomes sticky, pending undelivered tail packets are
   cleared, and the queue reports a permanent **no-replay** failure.
4. All later `offer` or `drainTo` calls throw an `IOException`
   before any further network writes. Existing bounded queue overflow
   semantics, normal FIFO and ISAAC output bytes stay unchanged.
5. `LocalSession.run()` already lets the socket's `drainOutbound`
   exception leave its loop; its existing `finally` detaches the
   World tick target, unregisters runtime services, saves its player
   and unregisters the WorldPlayer. R49 changes **only** the transport
   queue. No extra login, World action or save side effect is added.

## Reproducible fault-injected test

`server/src/spk/local/R49OutboundPacketTransportFailureTest.java`
is a pure Java11 fault-injection test, executed in Linux/Windows CI:

- Normal queued frames are byte-for-byte identical to the
  original direct `ServerPacketWriter` output with identical
  ISAAC seed, and drain in FIFO order.
- Two queued encrypted frames; a fake socket writes **exactly
  three bytes** of the first packet then throws. The same prefix is
  observable, but the queue becomes terminal, discards its pending
  tail, cannot be drained again, refuses later packet offers, and
  refuses calls through the existing `ServerPacketWriter`.
  No second call to the failed fake socket is made.
- A second fake socket accepts a complete first packet but throws
  at flush while a second packet remains buffered because of the
  configured drain budget; terminal failure discards the unsent
  second frame and refuses retry even though a flush might or might
  not have delivered the first packet.
- A deliberately bounded queue still rejects overflow exactly
  as before, independently from socket transport errors.
- No live networking or owner accounts, no graphical client or
  Windows Sandbox needed for these deterministic negative controls.

Initial Windows acceptance on 2026-11-14:
`jar testClasses verifyJava11Bytecode` **BUILD SUCCESSFUL**,
**2,168 major55 Java11 class files**;
`R49_OUTBOUND_PARTIAL_IO_FAIL_CLOSED_PASS` for normal frame parity,
partial-prefix no-replay, failed-offer denial, queued-tail discard,
flush-error no-replay and unchanged overflow behavior.

Dedicated R49 Linux/Windows CI steps run the same fault test and all
inherited R48/R47/R46/R45 tests (including real two-JVM synthetic
spell/loot/bank/restart gameplay). The PR remains draft and unmerged
pending cross-platform CI.

## Important unresolved transaction and durability scope

R49 **does not undo an accepted game action** after a socket failure.
Rune debits and NPC damage may already have happened in server memory
when a socket write/flush fails. The existing session teardown still
attempts the final account save, and no power-loss safety or
transactional persistence guarantee is claimed.

R49 is a targeted **fail-closed transport** change, not a write-ahead
ledger, persistent gameplay action journal, delivery acknowledgement,
reconnect replay protocol, crash-safe atomicity, or original SpawnPK
combat rule reconstruction. Those are independent future milestones.

The original graphical v308 client is still untested because the
authorized isolated Windows Sandbox guest is unavailable; synthetic
v308-shaped TCP acceptance is not evidence of original GUI playability.

**DRAFT, STACKED ON R48, UNMERGED** pending independent Linux and
Windows CI and later explicit review.
