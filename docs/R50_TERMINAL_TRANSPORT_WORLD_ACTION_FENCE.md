# R50 — fail-closed World command admission after known outbound connection failure

## Failure and scope

R49 correctly makes a failed outgoing packet stream terminal after a partial
socket write or flush IOException. The server must not resume encrypted packet
delivery on that session. R49 alone did **not** prevent an already-read typed
client request from being queued and subsequently executed on the World owner
after the session's outbound connection was known to be unusable. Such a request
could spend inventory, change NPC HP, or mutate account state despite a
dead outgoing transport.

R50 prevents **new typed request actions** on a session after that session's
outbound queue has entered either terminal transport failure or bounded
overflow. This is a targeted admission fence, not a persistent game transaction
journal, crash rollback, anti-duplication replay system or delivery ack.

## Implemented World request fences

1. `OutboundPacketQueue.requireGameActionAdmission()` checks for its R49
   sticky terminal transport error and for the existing sticky
   `overflowed` state. It is a read-only, synchronized check; it does not
   alter queued packets or socket bytes.
2. `ServerPacketWriter.requireGameActionAdmission()` delegates the check
   to a session-owned outbound queue, if one exists. Direct in-memory
   test writers remain unchanged and usable.
3. `LocalSession.processPendingOnWorld` calls the guard **before queuing**
   a command into the World, then **inside the World callback** immediately
   before draining the command. This closes the common delay between socket
   read and authoritative World execution.
4. `LocalPendingRequestDispatcher.acceptTypedRequests` repeats the guard
   at the start of **every typed request**, so one buffered client read
   containing multiple actions cannot proceed with action two after the
   outbound transport failed during action one.
5. An IOException propagates through the existing socket session teardown.
   The established final save and World unregister path is unchanged.

In-flight gameplay work may still complete when a transport fails concurrently
*after* the final admission check; this fence has no cross-thread atomic
reservation. R50 makes no claim of complete action rollback or eliminating
every networking race.

## Evidence

Windows JDK21 / Gradle8.10.2 initial `jar testClasses
verifyJava11Bytecode` **BUILD SUCCESSFUL** with **2,170 Java11
class-major55 classes**.

`R50TerminalTransportWorldAdmissionTest` verifies:

- Normal healthy queue supports two separate actions, and direct in-memory
  writers continue to work unchanged.
- A session can pass an early admission check and then receive a partial
  encrypted socket-write failure while the action waits in the World queue.
  The later World-callback guard rejects the action **before** its simulated
  inventory mutation, without retrying the failed socket.
- If action one was already admitted and committed, then the transport dies,
  a second typed request is refused and cannot mutate anything; the already
  committed first action is not falsely described as rolled back.
- Previously overflowed outbound queues reject new World actions even
  though this is separate from an R49 socket-write failure.

`test_r50_world_transport_admission_policy.py` contains four source-bound
checks pinning the actual prequeue, in-World and per-typed-request guard
placement, the writer seam and the read-only overflow/terminal check.

Dedicated Linux/Windows CI steps rerun the fault tests and every inherited
R49/R48/R47/R46/R45 acceptance, including two-Main-JVM synthetic
v308-shaped protocol combat/loot/banking/restart journeys.

## Limitations

- **No crash-safe gameplay transaction**: R50 does not guarantee all-or-
  nothing Rune debit, NPC damage, ground loot, Magic XP and account file
  update if the JVM dies mid-action.
- **No cross-thread atomicity after check**: a failure that arises during
  an already admitted action may still leave changed World state; proper
  transaction boundaries and a durable recovery model remain unimplemented.
- **No original GUI proof**: the user-owned original graphical v308 client
  has not been launched in an isolated Windows guest. All tests use
  private disposable Java11 server child processes, in-memory fault
  injectors and synthetic client-shaped TCP only.
- Default server rules and the explicitly CUSTOM_LOCALLAB practice
  spell damage/rune/XP fixtures are unchanged.

**Keep R50 DRAFT / STACKED ON R49 / UNMERGED until independent GitHub CI
passes and the change is reviewed.**
