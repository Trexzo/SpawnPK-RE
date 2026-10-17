# G21.321–322 — complementary ACTIVE/witness hardlink crash-window regressions

Base: [#2881](https://github.com/Trexzo/SpawnPK-RE/pull/2881) exact `6f26c23ebb8fd912b4a635763f9375a247a25884` (751 focused candidate). Certified ancestor [#2880](https://github.com/Trexzo/SpawnPK-RE/pull/2880) `092b4dfbc47ac4f3b3166e13e5cac76a14317f20` 749/749 hosted Java11/full Gradle.

G21.319/320 modified default-OFF `LocalFinalSaveNegativeWitness` to require observed exact selected ACTIVE and witness `unix:nlink==1` at publication boundaries. These follow-up regressions exercise independent real filesystem cutpoints:
- G21321 **preexisting ACTIVE hardlink**: establish 2-link selected ACTIVE before entering witness publication, expect explicit G21.319 refusal **before CREATE_NEW**, no partial witness and ACTIVE negative marker never deleted.
- G21322 **midpublication witness link**: add real alias to the created witness via the existing after-write/before-file-force deterministic seam; require G21.320 refusal after force/reinspection and retain both witness names and unchanged ACTIVE/account. No partial state cleanup or automatic account login/recovery.

Focused manifest 751→**753 unique** and 71→**73 protected mandatory** tests; two dedicated JavaExec tasks. No changes to production source from G21.319/320. No original SpawnPK behavior, authenticated owner epoch, final-save ACK, marker retirement or durable reward replay is claimed. Requires own hosted exact-head 753/753, new G21321/22 markers, Java11 major55 and full Gradle. Draft-only, `main` unchanged.
