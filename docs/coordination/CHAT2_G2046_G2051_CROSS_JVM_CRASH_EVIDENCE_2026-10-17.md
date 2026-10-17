# Chat 2 G2046–2051: real separate JVM process and crash-cutpoint evidence

Parent exact standalone gameplay PR #2935 SHA 9b1133f9fd6d3fe7fe6b52149bb337c22cc62255 (854/854 hosted verified), itself part of cumulative Chat1+Chat2 candidate PR #2936 and #2939 with account pre-ACTIVE link provider protections. These tests are NOT simulated process re-entries: each uses ProcessBuilder to launch a distinct hosted JVM using the test classpath and explicit child output/exit status.

## Source addition
Experimental LocalLabExperimentalDiskAttemptJournal gains a second **test-only** PublicationTestCutpoint invoked just AFTER ATOMIC_MOVE but BEFORE parse/readback and in-memory ledger evidence. Ordinary reserve still passes null; neither callback nor the journal grants or replays gameplay progress. ChildWorker synthetic fixtures can intentionally Runtime.halt(33) BEFORE move or Runtime.halt(34) AFTER move. The OS releases the process lock. Historical old event records stay read-only on reopen; no durable grant ACK inferred.

## Six real focused regression gates
- G2046: child holds exclusive file lock, second independently launched JVM + parent cannot acquire; after release another child can read historical evidence. Proves cooperative lock, not global owner epoch.
- G2047: separate JVM reads original parent's event and attempts to reuse the original caller UUID with its newly created finalized real NPC. Refuses, preserves old record and no replay.
- G2048: **negative proof**: a noncooperating second JVM atomically replaces the held lock filename with a new inode, then succeeds acquiring that NEW lock. The incumbent JVM detects old lock identity drift; cross-process global ownership is demonstrably **NOT** safe. Do not report this as a secure multi-process lock guarantee.
- G2049: child performs genuine canonical NPC death/settlement, forces pending reservation, then hard-exits JVM at before-move cutpoint (33). Parent sees orphan temp, no event, reopen REFUSED fail-closed; no speculative recovery.
- G2050: child performs genuine canonical NPC death/settlement, atomically publishes reservation, then hard-exits JVM BEFORE in-process acknowledgement (34). Parent parses one valid historical reservation and explicitly reports no consumer grant ACK/replay. Independent third JVM cannot extend old event.
- G2051: external noncooperating Java process corrupts an event file WHILE parent holds cooperative lock. Parent audited snapshot rejects checksum drift but outside write was possible; honest negative proof.

Manifest 854→860 unique focused tests. New test-only G2046DiskJournalChildWorker helper (not counted as focused), six JavaExec tasks, must-retain critical coverage family. No Chat1 account, World, packet, cache, original client or real gameplay grant production modifications.

## Safety and release gates
This is an experiment and source-backed reliability evidence, NOT original server award semantics. CPU/killed-process simulation does not prove OS power-loss durability, directory fsync, authenticated server death event identity, cross-JVM owner epoch, atomic account credit+ACK, or safe reward replay. Hardlink provider Windows capability unsupported on hosted runner; Windows GUI and 179 original-client checks remain NOT RUN. Issue #2587 stays open.

Hosted exact-head require 860/860, G2046–51 markers, Java11 major55, full Gradle. Draft/unmerged; main untouched.
