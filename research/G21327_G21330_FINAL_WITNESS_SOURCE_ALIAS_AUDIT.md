# G21.327-330 — exact source hardlinks in read-only final witness audit

Certified parent [#2887](https://github.com/Trexzo/SpawnPK-RE/pull/2887) exact `1575a1d8340d02186aae62eaca13ea88d434cbff`, 765/765 hosted Java11 major55 and full Gradle. Equivalent [#2888](https://github.com/Trexzo/SpawnPK-RE/pull/2888) also 765/765 certified.

**Actual source gap:** `LocalFinalSaveNegativeWitness.publishOnce` refuses hardlink aliases for selected account and ACTIVE (G21.319/320); the G21.323–26 read-only `LocalFinalSaveWitnessForensicAudit.inspect` validates witness links and exact selected account/marker SHA but did not reject source alias counts. A new hardlink to selected account or ACTIVE after publication could leave the bytes identical yet yield `MATCHES_UNTRUSTED_NEGATIVE`, misleading the operator. It NEVER granted login before this patch.

**Production:** classify account `accountObservedLinkCount != "1"` as `SOURCE_LINK_COUNT_UNPROVEN_NEGATIVE`, and selected ACTIVE `unix:nlink !=1` or unsupported as `ACTIVE_LINK_COUNT_UNPROVEN_NEGATIVE` before returning a matching-untrusted state. Existing exact source and witness selected names, double-read, canonical V1 parsing, all untrusted/no grant/report flags unchanged. No new writer/admission/recovery path.

**Tests:** G21327 genuine selected account hardlink after witness publication, then manual test-only removal; G21328 same for selected ACTIVE; G21329 unrelated account+ACTIVE aliases do not contaminate selected account audit; G21330 nlink3 for both sources verifies selected account then ACTIVE negative priority. All preserve bytes and confirm permanent login veto.

**Gate:** 765 + 4 = 769 distinct focused, four dedicated JavaExec tasks, existing 81 safety MUST-RETAIN anchors unchanged, hosted exact-head 769/769, G21327–30 markers, Java11 major55 and full Gradle. Draft only; main unchanged. This remains negative operator evidence only, NOT authenticated owner epoch/final-save receipt, safe ACTIVE retirement, replay, original SpawnPK source or persistent reward outbox (#2540/#2559/#2587).
