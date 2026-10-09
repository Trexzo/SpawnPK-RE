# G21.66 — guarded strict terminal postimage publication (NO GRANT)

Base: G21.65 `5353818f3caafc01c26fda6e883850d58dba7c54` hosted-green 365 focused Java 11, #37949206211. [Issue #2308](https://github.com/Trexzo/SpawnPK-RE/issues/2308).

## New opt-in file operation

`WorldPlayerPersistence.publishReservedTerminalStrictly` requires the G21.27 held account reservation, exact G21.25 proposal, coherent G21.64 terminal snapshot and a matching file-backed repository. Admission occurs off the World tick, queues behind earlier writes on the SAME bounded persistence FIFO and blocks newer account saves/checkpoints by held reservation. Before I/O, the task rechecks full live owner/generation, message identity and PREPARED snapshot.

New `StrictDurablePlayerSnapshotWriter.saveStrictTerminalForWorld` is an isolated opt-in terminal mode. All existing PREPARED-only save APIs preserve their prior validation. The terminal path requires same selected account path and checks the full disk PREPARED image under G21.39 cooperating publication lock, followed by revalidation of live owner and reservation. It publishes G21.48 negative write-ahead marker *before* ATOMIC_MOVE, forces temp file and parent directory, revalidates the exact terminal postimage and owner before clearing only the matching transient marker. Post-move uncertainty arms G21.47 permanent negative marker under same lock; no ordinary receipt escapes uncertain result.

On success the terminal record contains proposed inventory+CLAIMED and transaction identity in one file, but World owner remains unchanged and G21.64 restart admission still quarantines it. The held G21.27 reservation remains active even after a successful file-operation receipt: no automatic release, native reward, replay or ACK. Missing or divergent disk preimage and stale owner are vetoed prior to replacement. Raw uncooperative writers and actual hardware power failures are not proven safe by this isolated opt-in path.

## Verification

Real file-backed World regression tests G21.65 FIFO preflight before publication, confirmed strict receipt and disk postimage, World restart quarantine, G21.48 cleanup, competing saves refusal, second publication refusal, unchanged live owner, stale-at-admission and late owner mutation veto, divergent raw file refusal, durable review marker veto, post-rename force failure yielding terminal bytes + G21.47/G21.48 negative markers, independent account writes and no temp/lock leaks. Current-train 365→366 focused Java11 and full hosted build required before certification.

**DRAFT/unmerged**; R25 #1847 untouched; native C2S185 widget32181 NO_GRANT. No positive exactly-once COMMIT or validated live-state reconciliation exists.

G21.66 review hotfix: the G21.37 socket marker-only check now treats the exact account's in-flight G21.66 terminal publication write-ahead G21.48 marker as transient, matching the older prepared strict checkpoint exception. All permanent review markers and any stranded transient marker still veto. A deterministic latch test pauses after G21.48 marker publication, confirms no false same-owner kick, then verifies successful cleanup and permanent G21.47 veto after uncertain postmove failure.

G21.66 last-moment cooperating World-save negative veto: under the same G21.39 account publication lock, ordinary guarded `FilePlayerRepository.saveForWorld` now inspects the exact on-disk account for any `extension.mailbox-terminal-snapshot.*` marker before replacement. A coherent, malformed or partial terminal namespace blocks stale concurrent World autosaves that could otherwise erase terminal transaction evidence. This adds one guarded disk read per normal file-backed World save; raw forensic `save()` remains intentionally outside cooperating guarantees and is never a legitimate live-save bypass. No assumption of uncooperative writer isolation. A dedicated regression attempts the stale World save after a confirmed terminal publication and requires exact terminal bytes to remain untouched.

G21.66 parity check: old World-bound `StrictDurablePlayerSnapshotWriter.saveStrictForWorld` PREPARED barriers now call the same pinned-disk terminal-veto helper while holding G21.39 publication lock. Neither a concurrent ordinary `FilePlayerRepository.saveForWorld` nor a second-JVM PREPARED strict barrier can overwrite an existing terminal account. Historical direct/forensic `saveStrict()` and `save()` remain explicit non-World APIs. Additional regression verifies both separate World save entry points deny stale PREPARED writes after terminal publication.
