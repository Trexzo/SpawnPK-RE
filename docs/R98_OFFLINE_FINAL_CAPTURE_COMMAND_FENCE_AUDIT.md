# R98 — offline World-command replay fence tied to final capture

**DRAFT / STACKED ON R97 PR #3142 / OFFLINE ONLY / NOT DEPLOYED**

## The gap left after R96/R97

R96 and R97 check durable R60/R63/R64/R89/R90 source state and
publish a separate PREPARED/COMMITTED World retirement witness.
Their in-memory lease gate prevents new durable persistence writes
during retirement, but a command already queued before the final
snapshot can still mutate the same World player unless gameplay
ingress is fenced independently.

The existing R74 `WorldCommandInbox.OfflineCommandAdmission` hook
checks commands **at enqueue and again at actual handler dispatch**
inside the World ownership/mutation critical section. R98 uses this
existing path rather than creating a second command queue.

## Opt-in command fence

`R98OfflineFinalCaptureCommandFence` is an independent test-only
policy installed on a disposable `World.isolatedForTest` instance
before that World begins processing. It cannot replace an existing
R74 command policy. Production `World.shared`, `LocalSession`,
network packet handlers, plugins, and realtime actions are unchanged.

A currently owned actor, executing as a World command while holding
its real `player.mutationLock()`, calls
`quiesceAndCaptureFinalInsideActorForTest`. This method:

1. Verifies exact World execution context, player mutation-lock
   ownership, actor generation and account username.
2. Sticky-marks that actor/generation quiesced **before** capturing the
   immutable snapshot. This stays set even if snapshot capture fails.
3. Calls the actual R72
   `WorldPlayerPersistence.captureDeferredFinalSave`, binding the source
   World object and generation through R91/R92 provenance. R98 installs
   the existing R72 terminal-capture fence before World start, so
   later ordinary captures/checkpoints of this generation cannot
   bypass the terminal snapshot.
4. Records the exact `SaveTicket`. Outside the World actor, the
   caller submits the captured terminal save through the existing
   backpressure-aware IO path; it NEVER waits for IO under World locks.
   Retirement before this IO future completes is refused.
   Commands already queued before
   this handler now fail when the inbox rechecks them at dispatch;
   commands submitted afterward fail during enqueue.

An R98-specific `R96OfflineDurableRetirementWitness` factory takes
the R98 fence as an extra proof requirement. Before any R96
PREPARED witness or in-memory retirement transition, it verifies
that the actor/generation is quiesced, the requested capture ordinal
matches the same final ticket, and that ticket's actual asynchronous
save future completed successfully. R96's existing factory and R97
tests remain unchanged.

The same R96/R97 cross-file source checks still apply after this
in-memory proof; success of an arbitrary saved ordinal is not enough
to justify R98 witness publication.

## Executable scenarios

The R98 Linux/Windows regression runs a genuine isolated World and
real R89/R90 persistence worker:

- Inside one World command handler, enqueue an ordinary mutating
  command *before* quiescence, then quiesce and capture final state.
  The queued command is refused later during dispatch rather than
  being grandfathered because it was already in the inbox.
- Another command submitted *after* capture is refused at enqueue.
  Neither increments its mutation counter nor reaches item spawn.
- The actual World source/generation and ordinal 1 are bound to the
  R72 terminal-capture ticket. A deliberately premature R96 retirement
  attempt, before the capture is submitted to the separate IO worker,
  is refused. Its real off-thread IO completion and R89/R90 durable
  producer watermark must commit before any R96 PREPARED receipt.
- A constructed witness claiming ordinal 2 is denied before any
  retirement state/file change.
- A valid R98-linked R96 witness goes PREPARED -> actual World
  unregister -> COMMITTED. The persisted canonical snapshot digest
  must equal the immutable final capture digest.
- A distinct World fixture successfully saves ordinal 1 without
  quiescing ingress; even though that save is committed, it cannot
  publish an R98-linked retirement witness. Ordinary commands still
  execute normally in this non-quiesced fixture.

Dedicated Linux/Windows Java11-compatible test jobs run the new
entrypoint; independently triggered inherited R96/R97 workflows,
full Linux Gradle build and Windows atomic-persistence regressions
provide broader safeguards.

## Explicit boundaries

This gate applies **only to WorldCommandInbox commands**. It does
not fence direct `WorldPlayer` state mutators, realtime callbacks,
scheduled plugins, networking, non-cooperating OS processes, direct
filesystem writes or all gameplay reward paths. In particular,
R98 does NOT claim the broader server has a globally quiescent
World actor or make real logins safely restartable.

The quiescence bit is in-memory, not a durable certificate. R96/R97
disk evidence and fresh-JVM review remain read-only. No player
login, automatic recovery, multi-file atomic transaction,
cryptographic authentication or physical power-loss guarantee is
added. This is not a production logout path.

**Keep the stacked PR DRAFT/UNMERGED/UNDEPLOYED; require exact-head
Linux and Windows CI evidence before marking R98 verified.**
