# G21.37 — active session negative review-fence detection and termination

**Certified parent:** G21.36 exact `9a8e4ea01f0ba14f69f012eb7916d158e86439b9`; hosted [workflow 37830550816](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37830550816) SUCCESS, 335 focused Java11, full Gradle build. Frozen R25 PR #1847 untouched.

## Gap and new bounded behavior

G21.35 and G21.32 reject newly initialized/logging-in accounts with durable G21.32 negative review markers. G21.36 refuses *new* ordinary, autosave/checkpoint and final disconnect **writes** while such a marker exists. But a file-backed account already online can continue using the `LocalSession` socket loop after another actor arms a G21.34 review marker; after that point, ordinary gameplay may continue while persistence correctly rejects all saves. This could create unsaved gameplay progression and a misleading still-active client session.

`MailboxActiveSessionReviewGuard` is a small **per-connection negative-only guard**. `forSession(world.persistence(),username,persistent)` enables it only for persistent accounts hosted by a concrete `FilePlayerRepository`, because only that adapter implements the G21.32 sidecar contract. The guard inspects **marker existence**, never trusts marker contents, never hydrates account state and never invokes a Mailbox claim, grant, auto-clear or replay.

The real `LocalSession.run` now calls:
- `requireAtBoundary()` immediately **before `LocalLoginTransport.writeLoginSuccess(out)`**, catching markers armed after initial G21.35 registration but before the actual login-success packet.
- Another forced `requireAtBoundary()` before `worldTickGate.activate()` and tick-target attachment, catching markers added during the initial bootstrap.
- `poll(System.nanoTime())` before processing commands on each established socket-loop iteration. It consults the filesystem at most **once per one-second interval**. There is no scheduled task, private poll thread or World tick callback I/O.

On an observed marker (including a malformed/symlink marker), guard throws `IOException G21.37 MAILBOX_LIVE_REVIEW_FENCE ... action=TERMINATE_SESSION`; on I/O or runtime failure while reading presence metadata it throws `IOException G21.37 MAILBOX_LIVE_REVIEW_STATUS_FAILED ... action=TERMINATE_SESSION`. The preexisting `LocalSession` outer exception handler closes the socket, runs existing teardown, and G21.36 continues to refuse a normal final save into a review-fenced file. No new packets, player commands, inventory mutation or rollback behavior is introduced.

This is a **local marker metadata query directly on the session/socket thread**; it is not queued to WorldPlayerPersistence FIFO, because it does not read or rewrite account data and responds to an independent marker outside that account-file queue. It is rate-limited, but not subject to a guaranteed I/O latency deadline if the underlying filesystem is unhealthy. It is not an instant revocation guarantee: a marker may appear between checks and the session may process in-flight actions before detection.

## Focused verification

`G2137MailboxActiveSessionReviewGuardIntegrationTest` exercises the actual FilePlayerRepository/World marker path and guard used by the production session call sites: clean account passes forced boundary + normal poll, independently armed marker triggers the next due poll and forced boundary, malformed marker still triggers, unrelated account unaffected, unsupported non-file repository and nonpersistent identity do not invent a sidecar veto, injected marker metadata read failure fails closed, periodic queries are counted/throttled, forced boundaries bypass throttle, fresh World instance still observes the negative marker, no live reward/item grant and no marker auto-release.

**Important limitation:** This regression tests the session guard and associated marker path, and source-level integration of its actual LocalSession call sites; it **does not simulate a full native v308 socket login or capture login packets**, so no such packet-level proof is claimed. The exact-head hosted build must pass all 336 focused Java11 tests.

## Remaining safety work

There is no atomic cross-process ownership lock joining marker arming, World tick mutations and account persistence. Existing or just-dispatched World commands can still execute before the next due session probe, and a marker could appear between a forced check and subsequent packet publication. A full action-generation revoke handshake and a true crash-durable positive transaction ledger remain unresolved. Native C2S185 widget32181 remains NO_GRANT; bank widget32178 excluded; no auto-grant, rollback, replay or negative marker deletion.
