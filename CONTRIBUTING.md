# Contributing

Use a focused feature, fix, research, test, or documentation branch. For ordinary work, open a pull request against `main`; when the repository's active GitHub coordination queue names a cumulative integration/release target, follow that target instead. Do not assume a permanent intermediate branch name.

Do not edit `main` directly. Mainline changes should remain reviewable and compatible with the repository's cumulative certification and provenance model.

Promote to `main` only through the current cumulative integration/release process after the required certification and merged-main verification gates are satisfied.

## Before merge

For code or runtime changes, the applicable checks must pass before merge:

- the project builds successfully;
- relevant regression and repository-audit checks pass;
- authority/provenance is explicit for recovered, inferred, or LocalLab-created behavior;
- generated build output, account state, credentials, private client artifacts, and client binaries are not staged.

Documentation-only changes do not need unrelated runtime certification, but they must not alter or overstate the authority of technical evidence.

## Scope

Keep pull requests focused. Avoid mixing unrelated recovery work, gameplay changes, tooling changes, and documentation cleanup unless they are required for one coherent change.

When a change depends on external or private evidence, keep that material outside the public tree and commit only the derived, reviewable evidence that the repository's provenance rules permit.

See [MERGE_CONTRACT.md](MERGE_CONTRACT.md) and [docs/AUTHORITY_MODEL.md](docs/AUTHORITY_MODEL.md) for the current repository contract and evidence classification model.
