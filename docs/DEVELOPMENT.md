# Development

Prefer reusable systems over one-off commands:

```text
client request -> exact decoder -> normalized semantic event -> domain/service state -> client publication
```

Unknown production outcomes remain fail-closed.

Before committing:

```powershell
.\scripts\Build-Server.ps1
.\RUN_V5185_FULL_SELFTEST.ps1 -Target $PWD
.\VERIFY_REPO.ps1
```

Branch names: `fix/...`, `feature/...`, `research/...`, `refactor/...`.

Immediate work: fix Make-X fixture/root, finish launcher cleanup, and replace the
rejected Voidglass compositor with a real custom-asset pipeline.