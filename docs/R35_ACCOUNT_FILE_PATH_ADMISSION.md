# R35 — do not treat unsafe existing account paths as missing profiles

## Defect found after R34

`FilePlayerRepository.load()` previously used
`if (!Files.isRegularFile(file)) return Optional.empty();`.
`Files.isRegularFile` returns `false` not only for an absent file, but
also for an existing directory, a dangling symlink and some I/O failures.
The live R34 fail-closed loader correctly rejected exceptions, but a
`false` result still looked like an ordinary first-time account.

A symlink pointing to a regular file also passed the old check because
`isRegularFile` follows symbolic links by default. That could read a
file outside the intended profile directory.

## Fix

- Reject account directories, symbolic-link files and any other
  non-regular existing paths. Reject symbolic-link ancestors, aligning
  with the existing atomic-save policy.
- Use `Files.readAttributes(..., NOFOLLOW_LINKS)`: **only**
  `NoSuchFileException` means the account is genuinely absent. An
  inaccessible existing path, unreadable metadata or non-regular file
  must throw and fail closed.
- Open the account file with `NOFOLLOW_LINKS` so a late leaf symlink
  cannot be followed after the metadata check.
- Unchanged: valid ordinary account files proceed through the exact
  existing snapshot parser, missing files can bootstrap fresh profiles,
  `AtomicAccountPublisher` continues to own atomic save semantics.
- R34's `LocalAccountLifecycle.loadStrict` propagates these
  `IOException` failures into a refused live World admission before
  any default profile save.

## Verification

`R35AccountFilePathAdmissionTest` uses disposable filesystem fixtures
and an isolated test World. It covers: real absence, existing directory,
ordinary file with invalid schema, existing leaf symlink, dangling
symlink and symlink account-directory parent. Direct repository
rejections carry the R35 marker; strict persisted-account loader refuses
World admission with zero membership/tick targets. Symbolic-link
fixtures may be unavailable on Windows without privileges; the test
reports `symlinkCasesChecked=false` instead of claiming those cases ran.
They run on Linux where creating links is supported.

## Boundaries

This does not provide a race-free OS file-descriptor binding for
ancestor path replacement or defend against arbitrary malicious writers;
it narrows the initial on-disk account admission behavior. No user
profiles, original v308 game client, owner v312 runtime, firewall rules,
VM or external networking touched. Keep this work stacked, draft and
unmerged until CI and authorized guest acceptance.
