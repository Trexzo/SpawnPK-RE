# G2052–2057 — read-only unlocked journal forensic inspection

Exact gameplay base: PR #2940 corrected head 574d8b1e14f39b5b5ba8b838100e72f6d7e24cb9 (860/860 independently hosted Linux/Java11/full Gradle success), cumulative parallel Chat1 #2941 corrected 863/863 Linux and targeted Windows source smoke success. This change is scoped to a standalone, experimental, non-granting journal and operator inspection utility.

## Production problem
Existing open(Path) always uses Files.createDirectories and opens/writes .journal.lock for cooperative ownership; if journal has unresolved crash-before temp or corrupt event record it throws and cannot produce a structured operator triage summary. The operator needs to tell an orphan pending write apart from readable-but-NOT-authoritative reservation history WITHOUT creating a lock or mutating events.

## New source
`LocalLabExperimentalDiskJournalForensics.inspectExisting(Path)` never creates directories or files, never acquires a filesystem lock, deletes a temp, credits a player, dispatches a consumer, or authorizes replay. It observes nofollow directory/ancestor and entry constraints, Unix single-link count/fileKey availability, exact canonical event filenames, bounded files, V1 checksum and canonical stage order, original existing lock-file presence, and reservation capacity limit. It returns a structured state and reason plus **potentially partial and unlocked** observed counts. `READABLE_UNAUTHENTICATED_HISTORY` only means the observed files parsed; NOT an authenticated server kill, grant ACK, global owner epoch, stable snapshot or restart permission. Missing dir, orphan pending, unsafe link/entry, corrupt record and unsupported capability are separate states. It reuses the existing verified journal parser/checks by making six static helpers package-private; no new independent parser/code-path drift.

## Genuine tests
- G2052: inspect a nonexistent journal; directory and lock file remain absent.
- G2053: inspect two-stage real settled NPC history; count one event/two reservations, unchanged lock and event bytes, no ACK.
- G2054: tampered event checksum and an unknown operator file are each diagnosed and never removed or rewritten.
- G2055: separate child JVM Runtime.halt(33) before ATOMIC_MOVE; orphan state clearly reported, no event or replay.
- G2056: separate child JVM Runtime.halt(34) after ATOMIC_MOVE; one parseable historical event, NO grant ACK or replay.
- G2057: a symlinked ancestor and an external hardlink to an event block clean reporting without following/clearing evidence.

Six JavaExec tests and critical retention; focused manifest 860→866 distinct. This is an **unlocked** observational API: uncooperative external actors can change files between and during scans; no assert of atomic snapshot, hostile-process exclusion or original SpawnPK attribution. Partial counts on blocked results cannot be trusted as totals. Windows unix:nlink probe was unsupported, so the inspector also refuses unsupported link providers by design. The journal remains UNWIRED and NON-GRANTING, issue #2587 durable event ID / per-consumer credit+ACK stays open. Default World/account/rewards/Windows source unchanged.

Exact-head hosted gate: 866/866, G2052–57 + G2046–51 PASS, Java11 bytecode major55 and complete Gradle. Draft, do not merge main.
