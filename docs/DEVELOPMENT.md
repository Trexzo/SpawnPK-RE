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

Active priorities move with the cumulative recovery/integration frontier.
Use the repository's current GitHub Issues, Pull Requests, and integration/release
queue as the source of truth instead of retaining a hardcoded immediate-work list
in this document.