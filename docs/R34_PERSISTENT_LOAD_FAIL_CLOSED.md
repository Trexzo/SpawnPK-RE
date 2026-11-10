# R34 — prevent persistent-profile replacement after failed hydrate

## Concrete data-loss path in inherited R33

The live `LocalSessionPlayerInitializer.initialize()` used
`LocalAccountLifecycle.load(Selection, WorldPlayer, WorldPlayerPersistence,...)`.
That method caught **all** repository exceptions (including malformed current/future
snapshot schema) and returned `LoadResult(0,false)` with `KEEP_DEFAULTS`.
`Selection.persistent`, however, remained true: the World player was registered
with default gameplay state. Its normal session-end save could later replace the
unreadable/corrupt existing profile file with the new defaults.

## Narrow R34 fix

- Add `LocalAccountLifecycle.loadStrict(...,WorldPlayerPersistence,...)` for
  **live session admission**. On repository read or snapshot-decode failure,
  it refuses World admission by throwing before player registration.
- Preserve the old `load(...)` recovery behavior for standalone historical
  test and migration fixtures. Their direct callers are not silently changed.
- Keep normal **missing** profile behavior: an absent account file loads
  defaults and remains eligible for normal registration and later saving.
- Do not catch and suppress an `Error` on the strict path; log an
  anonymous failure type and reject the session.
- No change to external original-client handshake, ISAAC, server ports, output
  packet grammar, player gameplay rules or account file format.

The R33 first-widget gate remains earlier in the session sequence. This fix
runs **after** a verified first 185/widget912 and **before** any registration
or gameplay bootstrap. It cannot undo the protocol's earlier success triplet
(`2,205,0`) and is not production credential authentication.

## Tests

`R34PersistentLoadAdmissionTest` uses real ephemeral localhost TCP against
`LocalSession`, an isolated World and a disposable test-only repository.
It covers: no file (normal login/bootstrap), existing file with unsupported
future format version, existing file with invalid gameplay schema and a
simulated repository IOException. Rejected cases must send no gameplay
bootstrap, create no lasting WorldPlayer or tick target, issue zero persistence
save calls, and preserve the malformed on-disk file byte-for-byte.

Linux and Windows CI run R34 in addition to R33/R32 and inherited regressions.
No original proprietary graphical JAR or live owner account is used.

## Important bounds

Fail-closed load and error handling protect corrupt persistent files from
being overwritten **by this login path**, not from all possible filesystem
faults, concurrent rogue processes, or sudden power loss. Profiles absent
from storage are intentionally allowed; this is not credential validation.
Keep PR draft/stacked/unmerged pending exact-source CI and approved guest
original-v308 acceptance.
