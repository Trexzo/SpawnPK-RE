# G21.268/269 — second-JVM new-inode postmove hardlink evidence

**Source parent:** Draft [#2795](https://github.com/Trexzo/SpawnPK-RE/pull/2795), head `b82144611d1bd6fa33deeb249ab4469bca62312e` (645 focused candidate). Its G21.266 optional post-ATOMIC_MOVE hardlink-count recheck is a source change pending its own exact-head CI, and that branch itself builds upon the 643 candidate [#2794](https://github.com/Trexzo/SpawnPK-RE/pull/2794), then hosted certified 641 integration [#2793](https://github.com/Trexzo/SpawnPK-RE/pull/2793).

## Scope and rationale

G21.266 tests the actual newly published RAW account inode against the existing G21.230 default-OFF `unix:nlink` gate after account atomic replacement. The first focused fixture G21.266 creates a hardlink in-process through a package-only deterministic seam. This extension proves the same result with a **real independent Java process** that ignores the cooperating G21.185/G21.39 account locks.

- **G21.268:** a genuine second JVM is spawned synchronously inside the held primary JVM postmove hook. The child hard-links the selected account's already-replaced new inode, verifies the alias' exact bytes and exits with a distinctive marker. The primary must emit an UNCERTAIN result with `MULTIPLE_ACCOUNT_HARDLINKS_DENIED` cause, while the account and foreign alias retain exact post-replacement data. The writer cannot call it rollback, nor remove an alien link. The fixture alone cleans up its own alias and proves a healthy subsequent RAW save.
- **G21.269:** the second JVM creates an alias to an *unrelated* account during another account's postmove seam. The currently selected account's legitimate postimage must still succeed, the unrelated account/alien link bytes remain intact, and no global hardlink refusal occurs.

Two Java source files including common fork fixture, two JavaExec tasks; manifest **645→647 distinct focused**. **No additional production Java changes.** Chat2 gameplay, account ownership code, protocol/G1, review markers, original SpawnPK authority untouched.

## Limits

The imported G21.266 gate is **observed-only** and can still be raced by an uncooperative process after its final `nlink` check. This evidence does not establish a durable cross-JVM account-ownership epoch, post-crash final-save ACK, alias repair, safe reconnect, replay, marker release or mailbox grant authority. Strict opt-in only, unsupported nlink filesystem fails closed. Keep `main` unchanged and all branches draft.

**Required fresh certification:** hosted exact-head 647/647 focused including G21268/69 markers, Java11 major55 and full Gradle, plus independent success of stacked parents.
