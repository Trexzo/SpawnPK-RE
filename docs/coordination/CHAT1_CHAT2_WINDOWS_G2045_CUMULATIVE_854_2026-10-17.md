# Cumulative 854 gameplay pre-move + Windows verification baseline

Exact parent Chat1+2 Windows/main-aligned PR #2934 commit 583fb3c4b6f59c3e5d20553faca18260a13cdcd7 (849 focused candidate, Windows source-only workflow). Exact gameplay donor PR #2935 commit 9b1133f9fd6d3fe7fe6b52149bb337c22cc62255 (854 candidate from Chat2 G2041–45). Shared 849 build.gradle Git blob 747e99d7f1000c953f459bfdef70f65ca2f89e97 was byte-identical between PR #2934 and donor parent #2933.

Imported all eight G2041–45 donor changed Git blobs unchanged: journal, five G2041–45 real NIO fault-injection tests, donor Gradle with 854 distinct focused entries/five JavaExec tasks and critical retention family, and gameplay engineering handoff. Existing Windows PowerShell SHA-pinned Gradle bootstrap, Windows workflow, Java11 smoke, pinned-client evidence, and Chat1 account/world code remain byte-for-byte unchanged from PR #2934.

Source gate: exact-head Linux 854/854, G2041–45 plus G2029–40 and G21367–70, Java11 bytecode major55, strict Gradle SHA validation and BUILD SUCCESSFUL. Windows target remains ONLY source compile and two targeted synthetic tests; no Windows 854/854 claim, original-client v308 GUI or 179 parity. Windows unix:nlink probe in Chat1 preflight reported unsupported, so hardlink-dependent strict runtime opt-ins are NOT qualified.

Journal remains isolated UNWIRED NON-GRANTING. Pre-move rescanning and pinned temp bytes/inode check minimize a real gap but do not make hostile file writers atomic, pin global owner epochs, produce signed event IDs, guarantee fsynced directories, or enable consumer reward+ACK/replay. Issue #2587 stays unresolved.

Both source and Windows head CI must be verified independently. Draft only, main not modified. Do not merge alternative 841 review snapshots #2929 and #2930 together.
