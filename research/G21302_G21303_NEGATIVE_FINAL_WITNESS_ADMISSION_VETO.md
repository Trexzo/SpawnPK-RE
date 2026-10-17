# G21.302/303 — retained final-review witness is a permanent negative session-admission signal

Cumulative parent [#2859](https://github.com/Trexzo/SpawnPK-RE/pull/2859) exact `d01d710180ebca88e59fe73fc23b3e5442bf713c`, 719 candidate combining G21298–G21301 negative forced final witness, hard-kill and postforce uncertainty with Chat2 G1973–76 diagnostics. Last hosted-certified cumulative [#2854](https://github.com/Trexzo/SpawnPK-RE/pull/2854) 711/711.

## Source-backed gap
G21.298/299 deliberately created an untrusted, write-once negative `.g21298-final-review-witness` after forced real final World save, retaining the ACTIVE G21.188 marker. **But** the existing `FilePlayerRepository.loadForWorldSession` admission checks and G21.189 late review veto checked ACTIVE and Mailbox negative sidecars, **not the new sibling witness**. If an external/manual actor removed ACTIVE or restarted the server with the experimental witness creation flag OFF, a previously left negative witness could be ignored by admission. No positive read should be inferred merely because ACTIVE is now absent.

## Correction
- Implement `LocalFinalSaveNegativeWitness.requireNoNegativeWitness(exactAccountPath)`: read-only NOFOLLOW existence check of the exact sibling file. ABSENT means no additional veto **not proven safe**; any existing regular/nonregular/malformed/symlink leaf is NEGATIVE. Permission/metadata errors throw and cannot authorize admission.
- `FilePlayerRepository.loadForWorldSession` calls it **inside the original cooperating G21.39 account publication lock** before hydration and after the testable postread boundary. This remains active even when `experimentalNegativeFinalSaveReviewWitness` is OFF after process restart. It does not affect legacy RAW forensic `repository.load`.
- `LocalReviewIntentAdmissionVeto.requireAdmissible` also checks it on the exact held account lease path when optional review veto enabled, covering earlier and last pre-membership policy checks. Late FIFO load check also inherits the real repository's default negative veto.
- No untrusted witness contents are authenticated/parsed; no replay, account write, marker removal, owner generation change or positive recovery. This is an intentionally conservative new denial ONLY for paths already containing that negative sidecar.

## Regression cases
- **G21302** publishes an untrusted negative witness against a legitimate account+ACTIVE, simulates foreign/manual removal of ACTIVE, then turns off witness writer and attempts genuine `FilePlayerRepository.loadForWorldSession` hydration. It MUST fail via exact G21.302 while nonadmitting raw `repository.load` remains possible, preserving source bytes.
- **G21303** installs a malformed/nonregular foreign account witness. Foreign admitted session MUST be vetoed, while selected unrelated clean account still loads; no cleanup/side effects.
- Manifest 719→**721 unique focused**; dedicated JavaExec gates; G21.293 critical 23 regression anchors and Chat2 G1973–76 retained.

**Boundaries:** still no authenticated durable owner epoch, receipt-trusted recovery, automatic ACTIVE retirement or safe repeated logins. Uncooperative publication remains unfenced after last check; no filesystem observation can close atomic race by itself. #2540/#2559 unresolved. Full exact-head hosted 721/721, G21302/G21303, Java11 major55 and full Gradle required; draft/unmerged and main unchanged.
