param(
    [Parameter(Mandatory=$true)]
    [string]$BaseSpawnpk,

    [string]$OutputHome,

    [switch]$ResetOutput
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($OutputHome)) {
    $OutputHome = Join-Path $repo 'runtime\locallab-user-home\r13'
}

function Get-NormalizedDirectoryPath([string]$Path) {
    $trimChars = [char[]]@(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    return [IO.Path]::GetFullPath($Path).TrimEnd($trimChars)
}

function Get-Sha256Hex {
    param(
        [Parameter(Mandatory=$true)]
        [System.IO.Stream]$Stream
    )

    if (-not $Stream.CanRead -or -not $Stream.CanSeek) {
        throw 'SHA-256 input stream must be readable and seekable.'
    }

    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $Stream.Position = 0
        $hash = $sha.ComputeHash($Stream)
        return (
            @(
                $hash | ForEach-Object {
                    $_.ToString('x2')
                }
            ) -join ''
        )
    }
    finally {
        $sha.Dispose()
        $Stream.Position = 0
    }
}

function Assert-UnderRuntimeRoot(
    [string]$Candidate,
    [string]$RuntimeRoot
) {
    $candidateFull = Get-NormalizedDirectoryPath $Candidate
    $rootFull = Get-NormalizedDirectoryPath $RuntimeRoot
    $prefix = $rootFull + [IO.Path]::DirectorySeparatorChar

    if ([StringComparer]::OrdinalIgnoreCase.Equals(
            $candidateFull,
            $rootFull
        ) -or
        -not $candidateFull.StartsWith(
            $prefix,
            [StringComparison]::OrdinalIgnoreCase
        )) {
        throw "R13 OutputHome must be a descendant of the LocalLab runtime root: $rootFull"
    }

    return $candidateFull
}

function Assert-NoReparsePointAncestors(
    [string]$Candidate,
    [string]$RuntimeRoot
) {
    $candidateFull = Assert-UnderRuntimeRoot $Candidate $RuntimeRoot
    $rootFull = Get-NormalizedDirectoryPath $RuntimeRoot
    $trimChars = [char[]]@(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )

    $cursor = $rootFull
    if (Test-Path -LiteralPath $cursor) {
        $rootItem = Get-Item -LiteralPath $cursor -Force
        if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "R13 runtime root must not be a reparse point: $cursor"
        }
    }

    $relative = $candidateFull.Substring($rootFull.Length).TrimStart($trimChars)
    foreach ($segment in @($relative -split '[\\/]')) {
        if ([string]::IsNullOrWhiteSpace($segment)) {
            continue
        }

        $cursor = Join-Path $cursor $segment
        if (-not (Test-Path -LiteralPath $cursor)) {
            break
        }

        $item = Get-Item -LiteralPath $cursor -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "R13 output path must not traverse a reparse point: $cursor"
        }
    }

    return $candidateFull
}

function Assert-DisjointDirectories(
    [string]$Left,
    [string]$Right
) {
    $leftFull = Get-NormalizedDirectoryPath $Left
    $rightFull = Get-NormalizedDirectoryPath $Right
    $separator = [IO.Path]::DirectorySeparatorChar
    $leftPrefix = $leftFull + $separator
    $rightPrefix = $rightFull + $separator

    if ([StringComparer]::OrdinalIgnoreCase.Equals(
            $leftFull,
            $rightFull
        ) -or
        $leftFull.StartsWith(
            $rightPrefix,
            [StringComparison]::OrdinalIgnoreCase
        ) -or
        $rightFull.StartsWith(
            $leftPrefix,
            [StringComparison]::OrdinalIgnoreCase
        )) {
        throw "R13 BaseSpawnpk and OutputHome must be disjoint: base=$leftFull output=$rightFull"
    }
}

function Assert-NoNestedReparsePoints([string]$Root) {
    $rootFull = Get-NormalizedDirectoryPath $Root
    if (-not (Test-Path -LiteralPath $rootFull -PathType Container)) {
        return
    }

    $pending = New-Object 'System.Collections.Generic.Stack[string]'
    $pending.Push($rootFull)

    while ($pending.Count -gt 0) {
        $directory = $pending.Pop()

        foreach ($child in @(
            Get-ChildItem -LiteralPath $directory -Force -ErrorAction Stop
        )) {
            if (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "R13 output reset refuses nested reparse point: $($child.FullName)"
            }

            if ($child.PSIsContainer) {
                $pending.Push($child.FullName)
            }
        }
    }
}

function Assert-NoReparsePointsInExistingPath(
    [string]$Path,
    [string]$Label
) {
    $cursor = Get-NormalizedDirectoryPath $Path

    while (-not [string]::IsNullOrWhiteSpace($cursor)) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "$Label path must not traverse a reparse point: $cursor"
            }
        }

        $parent = Split-Path -Parent $cursor
        if ([string]::IsNullOrWhiteSpace($parent) -or
            [StringComparer]::OrdinalIgnoreCase.Equals(
                $parent,
                $cursor
            )) {
            break
        }

        $cursor = $parent
    }
}

function Assert-OutputNotOwnedByRunningAirgapClient([string]$OutputHome) {
    $resolved = Get-NormalizedDirectoryPath $OutputHome
    $homeArgument = "-Duser.home=$resolved"
    $homePattern =
        '(?i)(?:^|[\s"])' +
        [regex]::Escape($homeArgument) +
        '(?=$|[\s"])'

    try {
        $processes = @(
            Get-CimInstance Win32_Process -ErrorAction Stop
        )
    }
    catch {
        throw "Cannot prove R13 output is unused because the process table could not be read: $($_.Exception.Message)"
    }

    $owners = @(
        $processes | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_.CommandLine) -and
            $_.Name -match '^javaw?\.exe$' -and
            $_.CommandLine -match '(?i)client-airgap\.jar' -and
            $_.CommandLine -match $homePattern
        }
    )

    if ($owners.Count -gt 0) {
        $pids = @(
            $owners | Select-Object -ExpandProperty ProcessId
        ) -join ','
        throw "R13 output is owned by a running airgap client. Close client PID(s) $pids before -ResetOutput: $resolved"
    }
}

$runtimeRoot = Join-Path $repo 'runtime\locallab-user-home'
$output = Assert-NoReparsePointAncestors $OutputHome $runtimeRoot
$base = Get-NormalizedDirectoryPath $BaseSpawnpk

if (-not (Test-Path -LiteralPath $base -PathType Container)) {
    throw "Authorized base .spawnpk directory is missing: $base"
}

if ((Split-Path -Leaf $base) -ne '.spawnpk') {
    throw "BaseSpawnpk must point to an exact .spawnpk directory: $base"
}

Assert-NoReparsePointsInExistingPath $base 'BaseSpawnpk'
Assert-DisjointDirectories $base $output

$existingOutput = Test-Path -LiteralPath $output
if ($existingOutput -and -not $ResetOutput) {
    throw "R13 output already exists: $output. Re-run with -ResetOutput to rebuild the canonical isolated runtime copy."
}

$selector = Join-Path $repo 'scripts\Select-LocalLabJava.ps1'
$runtimeBuilder = Join-Path $repo 'scripts\Build-V308LocalClients.ps1'
$profileBuilder = Join-Path $repo 'tools\custom-assets\build_r13_isolated_profile.py'
$buildServer = Join-Path $repo 'scripts\Build-Server.ps1'
$runAll = Join-Path $repo 'RUN_ALL_LOCAL_LAB.ps1'
$clientJar = Join-Path $repo 'evidence\client(6).jar'
$expectedClientSha =
    '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6'

foreach ($required in @(
    $selector,
    $runtimeBuilder,
    $profileBuilder,
    $buildServer,
    $runAll,
    $clientJar
)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing R13 acceptance component: $required"
    }
}

$hadCallerJavaHome = Test-Path Env:JAVA_HOME
$callerJavaHome = $env:JAVA_HOME
$callerPath = $env:Path

try {
    . $selector
    $java = Set-LocalLabJava

    $javaBin = Split-Path -Parent $java.Path
$javac = Join-Path $javaBin 'javac.exe'
if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) {
    throw "Selected LocalLab Java runtime has no javac beside java.exe: $javac. Use a JDK 11+ (Java 17 preferred)."
}

$python = Get-Command python.exe -ErrorAction SilentlyContinue
if ($null -eq $python) {
    $python = Get-Command python -ErrorAction SilentlyContinue
}
if ($null -eq $python) {
    throw 'Python is required for the deterministic R13 profile builder.'
}

Write-Host '=== R13 exact-v308 runtime rebuild ===' -ForegroundColor Cyan
& $runtimeBuilder
if ($LASTEXITCODE -ne 0) {
    throw "Exact-v308 local-client rebuild failed with code $LASTEXITCODE"
}

if ($existingOutput) {
    $null = Assert-NoReparsePointAncestors $output $runtimeRoot
    Assert-NoNestedReparsePoints $output
    Assert-NoReparsePointsInExistingPath $base 'BaseSpawnpk'
    Assert-DisjointDirectories $base $output
    Assert-OutputNotOwnedByRunningAirgapClient $output

    Write-Host "Removing previous R13 isolated output: $output" -ForegroundColor Yellow
    Remove-Item -LiteralPath $output -Recurse -Force
}

Write-Host '=== R13 isolated profile build ===' -ForegroundColor Cyan
$r13SnapshotRoot = Join-Path (
    [IO.Path]::GetTempPath()
) (
    'spawnpk-r13-v308-input-' + [Guid]::NewGuid().ToString('N')
)
$r13SnapshotClient = Join-Path $r13SnapshotRoot 'client-v308.jar'
$r13SourceGuard = $null
$r13PrivateGuard = $null
$r13ProfileFailure = $null
$r13CleanupFailures = @()

try {
    New-Item -ItemType Directory -Path $r13SnapshotRoot | Out-Null

    $r13SourceGuard = [IO.File]::Open(
        $clientJar,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read
    )

    $r13SourceSha = Get-Sha256Hex -Stream $r13SourceGuard
    if ($r13SourceSha -ne $expectedClientSha) {
        throw (
            'R13 exact-v308 source client SHA mismatch. ' +
            "Expected: $expectedClientSha Actual: $r13SourceSha"
        )
    }

    $r13SnapshotWriter = [IO.File]::Open(
        $r13SnapshotClient,
        [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write,
        [IO.FileShare]::None
    )
    try {
        $r13SourceGuard.Position = 0
        $r13SourceGuard.CopyTo($r13SnapshotWriter)
        $r13SnapshotWriter.Flush($true)
    }
    finally {
        $r13SnapshotWriter.Dispose()
    }

    $r13PrivateGuard = [IO.File]::Open(
        $r13SnapshotClient,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read
    )
    $r13PrivateSha = Get-Sha256Hex -Stream $r13PrivateGuard

    if ($r13PrivateSha -ne $expectedClientSha -or
        $r13PrivateSha -ne $r13SourceSha) {
        throw (
            'R13 invocation-owned exact-v308 snapshot identity mismatch. ' +
            "Expected: $expectedClientSha Source: $r13SourceSha Snapshot: $r13PrivateSha"
        )
    }

    $r13SourceGuard.Dispose()
    $r13SourceGuard = $null

    $profileArgs = @(
        $profileBuilder,
        '--base-spawnpk', $base,
        '--client-jar', $r13SnapshotClient,
        '--output-home', $output,
        '--java', $java.Path,
        '--javac', $javac
    )
    & $python.Source @profileArgs

    if ($LASTEXITCODE -ne 0) {
        throw "R13 isolated profile builder failed with code $LASTEXITCODE"
    }
}
catch {
    $r13ProfileFailure = $_
}
finally {
    if ($null -ne $r13PrivateGuard) {
        try {
            $r13PrivateGuard.Dispose()
            $r13PrivateGuard = $null
        }
        catch {
            $r13CleanupFailures +=
                "private guard dispose: $($_.Exception.Message)"
        }
    }

    if ($null -ne $r13SourceGuard) {
        try {
            $r13SourceGuard.Dispose()
            $r13SourceGuard = $null
        }
        catch {
            $r13CleanupFailures +=
                "source guard dispose: $($_.Exception.Message)"
        }
    }

    if (Test-Path -LiteralPath $r13SnapshotRoot) {
        try {
            Remove-Item -LiteralPath $r13SnapshotRoot -Recurse -Force -ErrorAction Stop
        }
        catch {
            $r13CleanupFailures +=
                "snapshot root cleanup: $($_.Exception.Message)"
        }
    }
}

if ($null -ne $r13ProfileFailure) {
    if ($r13CleanupFailures.Count -ne 0) {
        $r13ProfileFailure.Exception.Data['R13ClientSnapshotCleanupFailure'] =
            ($r13CleanupFailures -join ' | ')
    }
    throw $r13ProfileFailure
}

if ($r13CleanupFailures.Count -ne 0) {
    throw (
        'R13 exact-v308 input snapshot cleanup failed: ' +
        ($r13CleanupFailures -join ' | ')
    )
}

$manifest = Join-Path $output 'R13_PROFILE_MANIFEST.json'
if (-not (Test-Path -LiteralPath $manifest -PathType Leaf)) {
    throw "R13 profile builder did not publish its manifest: $manifest"
}

$manifestData = Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
if ($manifestData.format -ne 'spawnpk-r13-isolated-profile-v1') {
    throw "Unexpected R13 profile manifest format: $($manifestData.format)"
}
if ([int]$manifestData.itemId -ne 29999 -or
    [int]$manifestData.modelId -ne 79999 -or
    [int]$manifestData.textureId -ne 278) {
    throw 'R13 profile manifest does not describe item 29999 / model 79999 / texture 278.'
}

Write-Host (
    'R13_PROFILE_READY home={0} configSha256={1} modelSha256={2} textureSemanticSha256={3}' -f
        $output,
        $manifestData.outputConfigSha256,
        $manifestData.modelSha256,
        $manifestData.textureArchiveSemanticSha256
) -ForegroundColor Green

Write-Host '=== R13 LocalLab server build ===' -ForegroundColor Cyan
& $buildServer

if ($LASTEXITCODE -ne 0) {
    throw "R13 LocalLab server build failed with code $LASTEXITCODE"
}

$serverJar = Join-Path $repo 'server\build\SpawnPKLocalServer.jar'
if (-not (Test-Path -LiteralPath $serverJar -PathType Leaf)) {
    throw "R13 LocalLab server build completed without expected JAR: $serverJar"
}

Write-Host 'R13_SERVER_BUILD_READY serverJar=true' -ForegroundColor Green

$hadOldHome = Test-Path Env:SPAWNPK_LOCALLAB_USER_HOME
$oldHome = $env:SPAWNPK_LOCALLAB_USER_HOME

try {
    $env:SPAWNPK_LOCALLAB_USER_HOME = $output

    Write-Host '=== Starting LocalLab server + watcher + isolated v308 airgap client ===' -ForegroundColor Cyan
    & $runAll

    if ($LASTEXITCODE -ne 0) {
        throw "RUN_ALL_LOCAL_LAB.ps1 exited with code $LASTEXITCODE"
    }
}
finally {
    if ($hadOldHome) {
        $env:SPAWNPK_LOCALLAB_USER_HOME = $oldHome
    }
    else {
        Remove-Item Env:SPAWNPK_LOCALLAB_USER_HOME -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Write-Host 'R13_RUNTIME_ACCEPTANCE_READY automaticVisualPass=false' -ForegroundColor Green
Write-Host 'Manual visual acceptance checklist:' -ForegroundColor Cyan
Write-Host '  [ ] Login reaches LocalLab successfully.'
Write-Host '  [ ] Item Library lookup for item 29999 shows "LocalLab Model Probe".'
Write-Host '  [ ] Model 79999 is visibly non-stock (not the Vasa fallback).'
Write-Host '  [ ] Magenta/cyan checkerboard texture 278 is visible on the model.'
Write-Host '  [ ] Client remains stable while previewing/rotating the item model.'
Write-Host '  [ ] If world/equipment placement is exercised, it uses the same custom model rather than stock fallback.'
Write-Host ''
    Write-Host 'Do not record R13 visual PASS until those observations are made on the real GUI session.' -ForegroundColor Yellow
}
finally {
    if ($hadCallerJavaHome) {
        $env:JAVA_HOME = $callerJavaHome
    }
    else {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
    }
    $env:Path = $callerPath
}
