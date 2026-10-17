# Chat 2 G2058–2063 — standalone operator inspection CLI for experimental NPC journal

Exact parent cumulative [PR #2948](https://github.com/Trexzo/SpawnPK-RE/pull/2948) at 5bb03eee1ccb507a4094f3285f11fdd0da1312f1 (872-test candidate including Chat1 Windows G21374–76 fixture fix, v308 negative release evidence, and Chat2 six G2052–57 forensic tests). New source is only the non-granting operator CLI, six tests, Gradle and this handoff. No account, World, NPC death, grant or Windows workflow production modifications.

## Actual operator executable
Use Java 11 with the compiled LocalLab server classes, e.g. `java -cp server/build/classes/java/main spk.local.LocalLabExperimentalDiskJournalInspector /path/to/existing-journal-dir`. Supply EXACTLY one directory pathname. The source is an independent `main`, not a chat-only pseudocode API, and uses existing `LocalLabExperimentalDiskJournalForensics.inspectExisting` without opening journal ownership or creating a lock file. Process exit codes: `0` parseable READABLE_UNAUTHENTICATED_HISTORY; `2` NO_EXISTING_JOURNAL; `3` BLOCKED orphan/corrupt/unsafe/capability/IO; `64` invalid args. Never uses a green/success code as grant/replay authority. One-line format `SPK_JOURNAL_FORENSICS_V1 state=... reason=... events=... reservations=... readOnly=true unlocked=true countsMayBePartial=true originalEventAuthority=false ownerEpoch=false grantAck=false replay=false`. Provider-specific error strings are normalized, and CLI intentionally does NOT print the supplied filesystem path or raw user/event metadata.

## Six real independent-JVM tests
- G2058 actual child java CLI on nonexistent directory, ensure no directory/lock created, exit2; no arguments exit64.
- G2059 real canonically finalized/ground-settled NPC G1 and G4 reservations, independent CLI exit0, events1/claims2, no changes to file or ground receipt.
- G2060 test worker child JVM Runtime.halt(33) with forced pending .tmp before atomic move, independent CLI exit3 unresolved pending with no event published.
- G2061 test worker child JVM Runtime.halt(34) after atomic move before ledger evidence, independent CLI exit0 parseable historical event but grantAck=false/replay=false.
- G2062 corrupt checksum and separately unknown non-event file yield exit3 with retained bytes, no repair.
- G2063 user/account-looking path and file names never appear in stdout; unknown state exit3; extra args exit64 and no path disclosure.

New focused manifest 872→878 distinct, six JavaExec tasks and critical-retention family. Production inspector only relies on read-only forensics; CLI path may still be concurrently mutated by malicious external process, and unprotected reports are NOT stable transactional snapshots. `unix:nlink` unsupported on hosted Windows; CLI may fail-closed with exit3 on unsupported provider and does not qualify original v308 client/GUI 179 parity.

## Safety and CI
No original SpawnPK server kill attribution, authenticated event identity, durable grant ACK, cross-JVM ownership epoch, fsynced directory durability or safe reward replay. Logs are intentionally bounded and path-redacted; a readable history is not a grant. #2587 remains open. Exact head requires Linux 878/878, all six G2058–63 stdout PASS plus previous G2052–57 + account G21374–76, Java11 bytecode major55, complete Gradle. Windows only source-targeted preflight with real token veto and v308 negative evidence. Draft/unmerged, main unchanged.
