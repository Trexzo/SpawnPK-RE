# Current Release Queue — Authoritative Record

> Mirrors the authoritative queue state from issue #392 (Chat 1), 2026-10-03 post-#1774.
> This file records the current promotion surface only; older superseded queue blocks
> from the issue thread remain historical and are intentionally not duplicated here.

## Current Queue

- Canonical repo: `Trexzo/SpawnPK-RE`.
- Current `main`: `3e842a32da1cf5d05f4d1f447c6105388fd2130a`.
- Current promotion pointer: **#1776 @ `790fd096f6ff1be17541f1269ebd3dd2d10faef5`**.
- Graph against main: **1155 ahead / 0 behind**, mergeable Draft.
- Exact PR-triggered hosted evidence on the promotion head:
  - run `37115684365` — **SUCCESS**;
  - all prior final-retirement / timeout-handoff / checkpoint / ordinary-save /
    session-command / pet-realtime markers PASS;
  - `PET_FOLLOW_TRANSPORT_ATOMICITY_PASS queueRetractPreservesState=true retryCommitsOnce=true reanchorRetractPreservesState=true directFailureRestoresState=true`;
  - `GROUND_PRESENTATION_BATCH_COMMIT_FENCE_PASS abortRetainsEvent=true retryPublishes=true commitConsumesEvent=true staleGenerationRejected=true`;
  - `MINIPET_BATCH_FAILURE_ATOMICITY_PASS configureAbortRestoresWriter=true configureStateUnchanged=true offAbortRestoresWriter=true offStateUnchanged=true retryWorks=true`;
  - mini-pet regression additionally proves `batchDepth=0`, pending buffer empty,
    cipher checkpoint cleared, and full ISAAC snapshot restored after failed
    configure/off batch settlement;
  - `GRADLE_CURRENT_TRAIN_FOCUSED_PASS count=178 bytecodeMajor=55`;
  - `LOCALLAB_LAUNCHER_CONTRACT_PASS`;
  - `V308_WALK_HERE_FALLBACK_PATCH_SELFTEST_PASS radiusBefore=1 radiusAfter=2 byteLengthNeutral=true failClosed=true`;
  - `BUILD SUCCESSFUL`.

## Absorbed Work

- #1774/#1775 MiniPetService failure cleanup: failed packet batches abort once;
  catch paths never retry `endBatch()`; original failures are preserved and abort
  failures are suppressed.
- #1771/#1772 ground-presentation commit fencing.
- #1767/#1769 pet follower transport atomicity.

## Provenance / Closed Surfaces

- #1773 is closed as a superseded promotion surface.
- #1775 remains source/provenance.
- #1776 is the sole current main-target promotion pointer.
- No newer strict successor is open at this refresh.
- #813/#1113 remain untouched as historical source/provenance.

## Promotion Status

**Do not promote yet.** Remaining gates are external:

1. Fresh exact-current private v308 cumulative/release acceptance on
   **exact HEAD `790fd096f6ff1be17541f1269ebd3dd2d10faef5`**.
2. Issue #1106 human HOME blocked-scenery **and minimap** Walk-here acceptance
   on this exact runtime.
3. Issue #9 human isolated R13 GUI/world visual acceptance
   (`automaticVisualPass=false`) on this exact runtime.

Previous machine/human evidence on earlier heads remains historical because
release evidence binds exact `gitHead`.
