# G21.279 / G21.280 — read-only operator account-forensic evidence

**Exact source parent:** [#2814](https://github.com/Trexzo/SpawnPK-RE/pull/2814), commit `b5810e93ea4868ba18b8c5e5a43e048992a51a61`, 671-test cumulative Chat1+Chat2 candidate. Earlier combined [#2811](https://github.com/Trexzo/SpawnPK-RE/pull/2811) `2770ba90dd613c507f7a5bd46c4231877a5cc75b` independently passed hosted 667/667. G1947–50 World NPC spectator hitmark relay/packet batch tests remain inherited intact.

## Why a real module, not an invented account recovery flow

G21.188 ACTIVE review, G21.32 durable mailbox review, G21.47 strict uncertain, G21.48 strict write intent, and G21.88 disk COMMIT can coexist with an account leaf. Prior tests prove many fail-closed admission and publication guards, but no small read-only forensic inventory existed for inspecting the exact selected file and *which known negative names* were observed. Operators need conservative evidence to investigate incidents. A diagnostic is **not** authority to clear markers, grant rewards, reconnect, unlock ownership or infer a verified final-save epoch.

**New production module:** `LocalAccountForensicEvidence.java`, package `spk.local`, self-contained read-only CLI plus package-private `inspect(Path)`. Takes exactly one explicitly supplied account filename; verifies ancestry using existing NOFOLLOW *read-only* preflight without acquiring/creating publication locks. Observes only the selected account file and these five known sidecars using NOFOLLOW attributes. Missing marker = ABSENT (never positive admission). A symlink, directory or malformed/nonregular marker = **PRESENT_NONREGULAR_NEGATIVE**, never followed or ignored. Refuses symlink/nonregular/oversized account leaves, hashes up to 16 MiB of exact account bytes through NOFOLLOW `FileChannel`, and checks file-key, creation/mtime/size and sidecar metadata consistency across observations. A changing witness aborts. Outputs full SHA-256 and marker statuses, explicitly with `loginAuthorized=false recoveryAuthorized=false grantAuthorized=false finalSaveAck=false ownerEpochProven=false`. Doesn't read marker payloads or account contents into logs.

**G21.279 real filesystem regression:** selected account SHA-256 and all-absent state with zero new lock or markers. Then create a **real valid** ACTIVE intent through the existing durable writer and one strict uncertain sidecar. Exactly two negatives must be reported; account and marker contents and directory-entry count must remain identical after the read-only audit. No invented recovery authority.

**G21.280 negative-control:** unrelated account containing a nonregular disk-COMMIT sidecar directory must not contaminate the selected account's marker inventory, yet must remain negative on the affected account; no created lock entries or altered bytes. Where supported, an account-leaf symlink must be refused without following/rewriting its target.

## Scope and evidence limitations

Source delta is a genuine production forensic module, two Java test classes, Gradle named JavaExec targets and **671→673** focused manifest. No World load/save/admission, account ownership, RAW replacement, ACTIVE write logic, mailbox settlement, packet handlers, client or NPC presentation behavior changed.

The scan is observational and **not an atomic snapshot**. An uncooperative actor can substitute/restore bytes between checks (ABA), introduce a marker after the final observation, and conceal other authority-bearing sidecars not among the five inventoried. No OS lease or durable epoch is authenticated, and even **zero known sidecar names observed** NEVER means safe admission or safe final logout. File SHA-256 is an identification fingerprint, not a signed receipt.

## CLI

From the server Gradle project, with an explicit selected account path (the task never searches directories):
```powershell
./gradlew -p server accountForensicReadOnly -PforensicAccountFile='C:\\path\\to\\selected-account.properties'
```

Or using the built server/test classpath directly:
```
java -cp <classpath> spk.local.LocalAccountForensicEvidence /absolute/path/to/selected-account.properties
```
Read-only output is `G21279_READ_ONLY_ACCOUNT_FORENSIC_EVIDENCE ...`. A diagnostic exception means **UNKNOWN/NO AUTHORITY**, not a clean account. Never run cleanup based on this alone.

**Release gate:** fresh hosted exact-head **673/673** including G21279/G21280, G21277/78, G1947–50, Java11 major55 and full Gradle. Keep draft, `main` unchanged.
