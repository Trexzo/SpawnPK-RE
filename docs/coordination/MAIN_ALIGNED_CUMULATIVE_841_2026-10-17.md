# Main-aligned cumulative integration audit — 2026-10-17

## Exact lineage and scope

- Target default branch: `main` `9bbae7ce66682310de56e5a3c365dcc3573dc260`, whose **two missing maintenance commits** `31e2b048f74d5640fe9007007112b2c5e3f69931` and `9bbae7ce66682310de56e5a3c365dcc3573dc260` are documentation-only.
- Cumulative gameplay/core source parent: [#2928](https://github.com/Trexzo/SpawnPK-RE/pull/2928) `86f5b1f977b5ce9f41d988490c317ea018193b7e` (**841 unique focused candidate**; its own exact-head CI must be independently confirmed before certification).
- The two lineages share merge base `3e842a32da1cf5d05f4d1f447c6105388fd2130a`. GitHub compare shows only FOUR new maintenance files on `main` relative to cumulative. Their exact Git blob IDs are retained unchanged on this merge. No source code, build manifest or client artifacts are reconciled by hand.
- Merge commit has **two parents**, first cumulative source, second current main. It is a candidate PR to `main` but **does not change `main`**. It is not an authorization to merge or close other draft PRs.

## Validation required

1. Git compare `main...merge` shows `behind_by=0` and the tree includes both maintenance documents and cumulative source.
2. Git compare `cumulative...merge` lists only these four maintenance files and this provenance document, with no Java, Gradle, cache or client modifications.
3. Hosted exact-head PR Actions: **841/841 focused**, explicit G21367–70 and G2029–32, Java11 class major55, all Gradle verification/build.
4. Original v308/v309 graphical Windows runtime, two-JVM races, crash/power-loss semantics, broader repository acceptance, PR conflict consolidation and integration decision remain **unverified here** and cannot be inferred from focused test counts.
5. Preserve the existing maintenance backlog ledger. Its contents are historical snapshots, not current truth for issues or CI, and are imported as-is rather than rewritten.

## Architecture limits

Account-side checks provide negative preimage/alias observations, not atomic hostile-writer exclusion, cross-JVM durable owner epoch, or authenticated final-save ACK. Gameplay journal remains UNWIRED and NON-GRANTING with test-supplied UUIDs. No authenticated event identity, reward+consumer ACK, directory durability guarantee or safe replay is implied.
