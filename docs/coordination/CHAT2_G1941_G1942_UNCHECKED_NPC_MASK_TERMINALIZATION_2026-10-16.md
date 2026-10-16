# G19.4.1/42 — unchecked packet mask failure does not strand lethal canonical NPC

**Parent:** [G21.274 + G19.4.0 cumulative PR #2803](https://github.com/Trexzo/SpawnPK-RE/pull/2803) exact head `6c0375eea4ed75e8495e95d62313b9cee7c1205d` (657 distinct focused candidate; hosted certification must be checked separately). Retains all Chat 1 account-security and G1937–40 gameplay source changes unchanged apart from the narrow handler below.

## Source failure seam

The canonical NPC immediate attack mutation is authoritative before mask packet publication. G1939/40 ensured that a checked packet `IOException` cannot suppress newly lethal `finalizeLethal`. However `ServerPacketWriter` can also surface unchecked exceptions from its downstream `OutputStream` or NPC mask encoding/scene validation after lifecycle HP has already been committed. The prior `catch(IOException)` did **not** finalize an NPC in that case. Source example: `NpcRegistry.sendMaskLocal` first validates the current scene and throws `IllegalArgumentException` if the active projection disappeared; that can happen after the last admission scene check. The world NPC is dead, but the terminal callback is skipped. Real transport failures may also be unchecked.

## Production correction

`LocalCanonicalNpcAttackHandler.handleUnderPlayerCadence` catches `IOException|RuntimeException` **only** around packet-65 encoding/publication. If the committed result newly killed the NPC, it attempts `finalizeLethal(target)` and then rethrows the original packet exception. If finalizer also throws `IOException|RuntimeException|Error`, it propagates the terminal exception, retaining the original presentation exception as suppressed (identity check avoids illegal self-suppression). A nonlethal committed hit still throws the packet exception without terminalization. Success and all pre-damage checks unchanged. Does not catch catastrophic `Error` thrown by packet publication.

## Executable tests

- **G1941:** genuine ISAAC-decoded option2 opcode72, separate canonical NPCs at 20 and 10 HP, and a real `ServerPacketWriter` backed by an `OutputStream` that throws `IllegalStateException` at actual flush. Prove exactly one committed 10 HP hit, proper cooldown, unchanged exception identity/message, immediate lethal callback only for dead NPC, no same-tick duplicate, later legitimate nonlethal control after cooldown.
- **G1942:** the same runtime output failure on a lethal target combined with an independently throwing finalizer. Verify terminal exception stays primary and original presentation `IllegalStateException` is preserved exactly once as suppressed. No duplicate HP or invented reward grant.

Focused manifest 657→659 unique; dedicated Gradle JavaExec tasks. Exact-head hosted **659/659 focused + Java11 major55 + full Gradle** required before certification. Source remains a draft contribution; `main` untouched.

## Nonclaims

This is not a safe transactional rollback of partial packet writes, a durable terminal outbox, crash-consistent per-consumer progression acknowledgment, an original SpawnPK server kill-credit policy, or an atomic World/session close lease. Packet output can still have committed partial bytes; the exception propagates to the caller. All reward/attribution claims remain explicitly unsupported until source evidence and durable settlement coverage exist.
