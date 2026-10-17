# Chat 2 G2041–2045 — verify forced journal temp and old records immediately before move

Parent exact commit: 1364e53d314ec79abb168f2e16bf1bb187dac817; Chat2 PR #2933 849 focused candidate. Earlier PR #2931 845 and main-aligned PR #2930 841 were independently CI green.

## Concrete source gap
Previously journal.reserve() scanned existing records before creating/forcing a pending temp and then replaced destination with ATOMIC_MOVE without rescanning. Another writer could mutate owned pending bytes, hardlink its inode, substitute the pending inode, or replace an existing event inode with identical bytes between scan and move. New code re-scans entire directory after creating/forcing pending, allowing ONLY its own pinned pending file, while checking canonical event records and original lock/directory identities. It verifies pending exact initial byte content with bounded NOFOLLOW FileChannel, original fileKey and unix:nlink==1 and existing destination identity before ATOMIC_MOVE. Unexpected destination names on first event are denied. A package-private PublicationTestCutpoint permits deterministic real filesystem fault injections only in tests; ordinary reserve passes null. Existing postmove content verification remains.

## Genuine canonical NPC focused tests
- G2041: tamper forced temp bytes for G4, retain previous G1; orphan pending retained and audited read blocks.
- G2042: external hardlink alias to forced pending; fail BEFORE new event.
- G2043: replace existing G1 destination with byte-identical new inode at publication cutpoint; fail before G4 replaces it.
- G2044: replace pending inode with byte-identical new inode at publication cutpoint; identity check refuses.
- G2045: healthy real NPC newly-created G1 and G4 extension pass force/recheck/move and audited read; historical read-only reopen retained.

854 unique focused classes (849+5), five individual JavaExec gates and critical-retention family. Account, World, NPC settlement and grant production code unchanged.

## Boundaries
Only fail-closed observation, NOT hostile process atomic exclusion: a nonparticipating writer may still race last check and move. FileChannel.force on pending does not guarantee directory metadata fsync and the index does not have durable authenticated server event identity, owner epoch, atomic per-consumer grant+ACK or safe automatic replay. Caller UUID and process-local player identity are not true SpawnPK authority. Unsupported unix:nlink/fileKey Windows providers fail closed; no Windows gameplay/client qualification. #2587 remains open. Journal remains UNWIRED and NON-GRANTING.

Exact-head hosted gate must show 854/854, G2041–45, G2033–40, G21367–70, verifyJava11Bytecode major55, full Gradle; keep draft/unmerged and main unchanged.
