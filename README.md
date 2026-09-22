<img width="246" height="449" alt="image" src="https://github.com/user-attachments/assets/543ea4c9-38ac-44f1-8af8-98045a8d315d" />

<img width="500" height="449" alt="image" src="https://github.com/user-attachments/assets/835f4622-855b-4ff5-a9ad-64c3ea46814f" />

# SpawnPK-Src

Unofficial **SpawnPK client research, reconstruction, and LocalLab server-development environment**.

The project combines exact-current client/cache archaeology with a loopback-only Java server, protocol reconstruction, reusable gameplay/domain systems, provenance-tracked research, and regression infrastructure.

It is **not** presented as the original SpawnPK server source.

## Project state

The certified foundation is:

- **SpawnPK LocalLab v5.18.5 / Engine R8.5**
- certified server SHA-256: `589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c`
- inherited regression contract: **179/179**
- Java bytecode target: **Java 11 / class major 55**

The historical certified regression fixture remains:

- client build/config: **307**
- SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`

Current exact-client research also covers:

- client build/config: **308**
- SHA-256: `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`

Static comparison of the supplied v307 and v308 JARs found the same 10,970 entries with one changed class and an embedded `307 -> 308` build/config constant change.

**v308 is current exact-client research authority, but it is not yet promoted as the canonical runtime regression fixture until the real v308 artifact passes the inherited 179/179 acceptance gate.**

See:

- `server/research/r85/CLIENT_V308_DELTA.md`
- `docs/release-r8.5/`
- `docs/AUTHORITY_MODEL.md`

## What is in this repository

This is no longer only a packet/client reconstruction tree.

Current `main` contains foundations for:

- loopback-only game and auxiliary networking;
- exact login, ISAAC and packet reconstruction;
- shared `World` ownership and `WorldPulse`;
- player, NPC, object and ground-item registries;
- bounded world-command execution;
- movement, routing, collision and pathfinding;
- player persistence and snapshot schemas;
- combat state, timing, presentation and weapon authority;
- prayer and magic state;
- items, equipment, banking and inventory interaction;
- pets and NPC presentation;
- marketplace and atomic transaction foundations;
- construction;
- clans and Clan Wars evidence/domain structures;
- Bounty Hunter;
- Collection Log;
- loadouts;
- parties, matchmaking and world instances;
- objectives/progression;
- conversions and recipes;
- mailbox/reward-delivery foundations;
- timed effects and usage quotas;
- semantic content/plugin APIs;
- synchronous domain events;
- exact-client application/UI protocol adapters;
- extensive client/cache research datasets and provenance records.

Not every subsystem above represents recovered original-server behavior.

Many systems deliberately provide **protocol-independent foundations** while original SpawnPK rules such as rewards, formulas, eligibility, pricing, matchmaking policy or persistence semantics remain unknown.

## Authority model

Every recovered or created behavior should retain its evidence class.

1. `EXACT_CURRENT_CLIENT`
2. `EXACT_CURRENT_CACHE`
3. `LOCAL_RUNTIME_PROVEN`
4. `HISTORICAL_CORROBORATION`
5. `INFERENCE`
6. `UNKNOWN_SERVER_AUTHORITY`
7. `CUSTOM_LOCALLAB`

The repository must not silently turn inference, unknown production behavior, or LocalLab-created content into claimed original SpawnPK behavior.

See `docs/AUTHORITY_MODEL.md`.

## Architecture direction

The intended boundary is:

```text
socket bytes
    |
    v
exact packet decoder / schema
    |
    v
typed ClientRequest
    |
    v
validation + semantic routing
    |
    v
World / WorldCommandInbox
    |
    v
domain service
    |
    +--> DomainEventBus
    |
    v
presentation / protocol adapter
    |
    v
client
```

Raw packet IDs and client widget identities belong at the protocol/presentation boundary.

Gameplay systems should operate on semantic domain concepts instead of using packet numbers as APIs.

Unknown production outcomes remain fail-closed.

## Repository layout

```text
server/src/
    Java server implementation, tests, authority repositories,
    protocol adapters and distilled research data

server/data/
    static LocalLab runtime data
    mutable account data is excluded

server/research/
    retained R8.3 / R8.4 / R8.5 client archaeology,
    javap evidence and protocol reconstruction

server/certified/
    sealed R8.5 server artifact and provenance

protocol/
    login, C2S/S2C and application-protocol documentation

research/
    exact-current higher-level client/cache audits

evidence/
    regression/certification evidence and checksums
    original client JARs are intentionally excluded

scripts/
    build, Java selection, runtime checks and launch helpers

tools/
    research, client-patching and historical LocalLab utilities

docs/
    setup, authority, architecture and historical release documentation
```

Historical `.v*-backup` directory forests are intentionally excluded. Git history replaces that workflow.

## Build toolchain

Development builds use **JDK 21** with:

```text
javac --release 11
```

The resulting LocalLab classes therefore target Java 11 bytecode.

Temurin `javac 21.0.12.1` produced the closest reconstruction of the sealed R8.5 artifact during the compiler matrix:

```text
494 / 499 certified class entries exact
```

Java 17 remains the preferred proven runtime/client Java.

Build through the repository wrapper:

```powershell
.\scripts\Build-Server.ps1
```

or directly through Gradle:

```powershell
cd server
.\gradlew.bat build
```

The build:

- selects JDK 21;
- compiles with `--release 11`;
- verifies class major version 55;
- creates `server\build\SpawnPKLocalServer.jar`;
- runs the inherited R8.5 regression task when the required external client fixture is available.

To make absence of the exact regression client fatal:

```powershell
cd server
.\gradlew.bat build -PrequireExactClient=true
```

## Existing LocalLab developer quick start

If you already have the certified external LocalLab runtime:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force

.\IMPORT_EXISTING_RUNTIME.ps1 `
    -From "$env:USERPROFILE\Desktop\SpawnPK-LocalLab-v0.1"

.\BOOTSTRAP.ps1
.\RUN_LOCAL_LAB.ps1
```

The LocalLab binds only to loopback:

```text
GAME  127.0.0.1:43594
AUX   127.0.0.1:43595
```

The auxiliary endpoint exists to keep supported client HTTP/cache traffic local.

For a clean setup, read:

- `docs/SETUP_WINDOWS.md`
- `docs/EXTERNAL_RUNTIME.md`

## Verification

Repository wrappers:

```powershell
.\scripts\Build-Server.ps1
.\RUN_REPO_SELFTEST.ps1
.\VERIFY_REPO.ps1
```

The inherited R8.5 harness is retained as:

```powershell
.\RUN_V5185_FULL_SELFTEST.ps1
```

The sealed R8.5 server artifact is preserved at:

```text
server/certified/SpawnPKLocalServer-R8.5-certified.jar
```

The source tree is the development authority going forward; the sealed JAR remains historical/certification provenance.

## Exact-client research

The repository contains extensive static recovery of the current SpawnPK client and cache, including:

- login and ISAAC framing;
- C2S and S2C packet schemas;
- S2C126 application-control behavior;
- S2C250 application operations;
- interfaces and dynamic widget actions;
- items and equipment;
- NPCs and pets;
- movement/collision/world authority;
- marketplace/mailbox;
- raids and event interfaces;
- achievements and Adventure;
- friends/ignore;
- login rewards;
- construction;
- Clan Wars;
- gambling surfaces;
- timed effects;
- settings and command/control-token behavior.

Client-visible behavior is not automatically equivalent to original server behavior.

The client can prove transport, presentation, local state machines and embedded metadata. It generally cannot by itself prove authoritative server formulas, reward settlement, persistence, anti-abuse rules or business logic.

## Current development

Development continues beyond the certified R8.5 baseline.

Active work is split broadly between:

```text
Core/runtime
    World ownership, lifecycle, persistence, concurrency,
    networking and typed request transport

Gameplay/domain
    protocol-independent game systems and composition

Exact recovery
    current client/cache archaeology and evidence packages

Integration/release
    cumulative replay, regression and mainline acceptance
```

Large active PRs and research branches may contain work that is **not yet on `main`**.

The README describes the checked-in mainline foundation. GitHub Issues and Pull Requests are the source of truth for in-flight work.

## Content API

The repository includes a semantic content API under:

```text
server/src/spk/content/api/
```

It exposes domain-facing handlers and contexts for commands, NPC/object/player interactions, item options, item-on-* interactions and presentation services.

Content registrations carry explicit provenance.

Built-in modules currently distinguish LocalLab-created, runtime-proven and unknown-server-authority behavior rather than collapsing them into one source category.

## External binaries and private state

Original/current SpawnPK client binaries are intentionally **not committed**.

Developers must provide their own authorized external runtime.

Also excluded from normal Git history are:

- account state;
- local `.spawnpk` cache/config state;
- patched LocalLab client JARs;
- generated builds;
- runtime backups;
- logs;
- release archives;
- raw private client artifacts.

Do not commit credentials, account profiles or proprietary client binaries.

## Provenance / licensing

This repository contains reconstruction work, LocalLab-created code, derived research records and a sealed historical LocalLab server artifact.

Keep distribution decisions separate from technical completeness.

Licensing and provenance should be reviewed before making the repository or any bundled artifacts public.

## Development rule

> Recover what the client/cache can actually prove.  
> Model unknown server behavior explicitly as unknown.  
> Build reusable LocalLab systems without pretending they are recovered production authority.

That distinction is part of the architecture, not just documentation.
