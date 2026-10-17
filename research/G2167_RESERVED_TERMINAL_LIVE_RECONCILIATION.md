# G21.67 — reserved terminal/live reconciliation, NO GRANT

**Parent:** certified G21.66 `fd24f8bf50bbe56b24b7e46e83de678c9dd9a2bf`, [Actions #37952091463](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37952091463) SUCCESS, 366 focused Java11. [Issue #2310](https://github.com/Trexzo/SpawnPK-RE/issues/2310).

After G21.66 the strict terminal account file can contain hypothetical inventory credit and a Mailbox CLAIMED row, but the actual WorldPlayer remains at the original PREPARED/UNCLAIMED state and the durable terminal snapshot is quarantined on restart. This is a *deliberate gap* that must not be bridged by re-crediting inventory or blindly hydrating the file.

`WorldPlayerPersistence.reconcileReservedTerminalReadOnly` adds an off-World, bounded FIFO task for an active G21.27 account reservation. It requires the same World owner/generation, exact fresh G21.25 proposal and fully coherent G21.64 terminal snapshot both on admission and on the worker. It holds the existing G21.39 account publication lock to inspect the pinned raw FilePlayerRepository account with NOFOLLOW regular-file identity checks, full account-value comparison and pinned permanent/transient negative markers. It intentionally never calls admitted `loadForWorldSession`, which correctly quarantines terminal accounts.

The returned classifier distinguishes:
- `EXACT_TERMINAL_BOUND_RECEIPT_NO_GRANT`: exact coherent terminal record and G21.66 strict receipt match account, path, full snapshot digest, generation and idempotency key.
- `EXACT_TERMINAL_NO_RECEIPT`: terminal disk visible without supplied receipt.
- `EXACT_TERMINAL_RECEIPT_MISMATCH`: foreign/bad operation receipt.
- `EXACT_PREPARED_NO_GRANT`: original PREPARED record still on disk.
- `MISSING_ACCOUNT_QUARANTINE`, `DIVERGENT_ACCOUNT_QUARANTINE`: missing or unexpected disk bytes.
- `NEGATIVE_FENCE_QUARANTINE`: permanent G21.32/G21.47 or stranded G21.48 marker exists; this overrides all matching postimage evidence.

**Every state** explicitly has durabilityConfirmed=false, settlementCommitted=false, grant/replay/rollback/release/clientAck=false. A matching receipt is file-operation evidence, not authenticated positive transaction authorization. No WorldPlayer is mutated. Repeated observations cannot double credit. The held reservation remains protected even if the file and receipt agree; no automatic or safe release. This is a recovery **inspection**, not a live reconciliation **apply**.

Real file-backed World integration covers exact, repeat, no receipt, foreign receipt, negative marker, prepared-only, divergent, missing, live stale, forged terminal, duplicate pending queue, independent account save, original terminal raw bytes unchanged by repeated reads, restart quarantine, and no temp/lease leaks. Focused Java11 count 366→367, hosted exact-head CI required before certification. Draft/unmerged, native widget32181 NO_GRANT, R25 #1847 untouched.
