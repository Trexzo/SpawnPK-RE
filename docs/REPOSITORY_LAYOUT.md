# Repository layout

```text
server/src/        Java implementation/tests + distilled runtime authority data
server/data/       static runtime data (accounts excluded)
server/lib/        build dependencies
server/research/   retained R8.3/R8.4/R8.5 research
protocol/          protocol documentation/data
tools/             research/client/dev tools
scripts/           portable build/bootstrap/run wrappers
evidence/          text provenance + ignored local pinned client
docs/              setup/status/development documentation
```

Historical `.v*-backup` forests are intentionally excluded. Git history replaces
the cumulative backup-directory workflow.

Large raw R8.2 archaeology is excluded from normal clones; distilled data used by
LocalLab remains under `server/src/spk/local/data/research_r82/`.