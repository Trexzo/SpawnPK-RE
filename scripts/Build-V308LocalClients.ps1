param(
    [string]$ClientJar = (Join-Path $PSScriptRoot '..\evidence\client(6).jar'),
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\local-client')
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$patcher = Join-Path $repo 'tools\runtime\build_v308_local_clients.py'
$runtimeCheck = Join-Path $PSScriptRoot 'Check-ExternalRuntime.ps1'
$canonicalEvidence =
    [IO.Path]::GetFullPath(
        (Join-Path $repo 'evidence\client(6).jar')
    )
$canonicalOutput =
    [IO.Path]::GetFullPath(
        (Join-Path $repo 'local-client')
    )
$client = [IO.Path]::GetFullPath($ClientJar)
$output = [IO.Path]::GetFullPath($OutputDirectory)
$expectedClient =
    '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6'

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

function Assert-CanonicalOutputPathSafe([string]$Path) {
    $repoRoot = [IO.Path]::GetFullPath($repo).TrimEnd('\','/')
    $full = [IO.Path]::GetFullPath($Path).TrimEnd('\','/')

    if (-not [StringComparer]::OrdinalIgnoreCase.Equals(
            $full,
            $canonicalOutput
        )) {
        throw "OutputDirectory must remain the canonical LocalLab runtime directory: $canonicalOutput"
    }

    $repoItem = Get-Item -LiteralPath $repoRoot -Force
    if (($repoItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Repository root must not be a reparse point: $repoRoot"
    }

    $relative = $full.Substring($repoRoot.Length).TrimStart('\','/')
    $cursor = $repoRoot

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
            throw "Canonical local-client path must not traverse a reparse point: $cursor"
        }
    }

    if (Test-Path -LiteralPath $full) {
        $outputItem = Get-Item -LiteralPath $full -Force
        if (-not $outputItem.PSIsContainer) {
            throw "Canonical local-client output exists but is not a directory: $full"
        }
        if (($outputItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Canonical local-client output must not be a reparse point: $full"
        }
    }

    return $full
}

foreach ($required in @($patcher, $runtimeCheck)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing v308 local-client build component: $required"
    }
}

if (-not [StringComparer]::OrdinalIgnoreCase.Equals(
        $client,
        $canonicalEvidence
    )) {
    throw "ClientJar must be the canonical evidence path for permanent runtime verification: $canonicalEvidence"
}

if (-not (Test-Path -LiteralPath $client -PathType Leaf)) {
    throw "Exact v308 client missing: $client"
}

if (-not [StringComparer]::OrdinalIgnoreCase.Equals(
        $output,
        $canonicalOutput
    )) {
    throw "OutputDirectory must be the canonical LocalLab runtime directory: $canonicalOutput"
}

# Lexical equality alone is not enough: reject junction/symlink aliases before
# creating or publishing anything below the canonical local-client directory.
$output = Assert-CanonicalOutputPathSafe $output

$python = Get-Command python -ErrorAction SilentlyContinue
if ($null -eq $python) {
    throw 'Python is required to build the exact-v308 LocalLab client variants.'
}

$snapshotRoot = Join-Path (
    [IO.Path]::GetTempPath()
) (
    'spawnpk-v308-input-' + [Guid]::NewGuid().ToString('N')
)
$snapshotClient = Join-Path $snapshotRoot 'client-v308.jar'
$sourceGuard = $null
$privateGuard = $null
$patcherFailure = $null
$cleanupFailures = @()

try {
    New-Item -ItemType Directory -Path $snapshotRoot | Out-Null

    $sourceGuard = [IO.File]::Open(
        $client,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read
    )

    $actualClient = Get-Sha256Hex -Stream $sourceGuard
    if ($actualClient -ne $expectedClient) {
        throw "Exact v308 client hash mismatch. Expected $expectedClient actual $actualClient"
    }

    $snapshotWriter = [IO.File]::Open(
        $snapshotClient,
        [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write,
        [IO.FileShare]::None
    )
    try {
        $sourceGuard.Position = 0
        $sourceGuard.CopyTo($snapshotWriter)
        $snapshotWriter.Flush($true)
    }
    finally {
        $snapshotWriter.Dispose()
    }

    $privateGuard = [IO.File]::Open(
        $snapshotClient,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read
    )

    $privateClientSha = Get-Sha256Hex -Stream $privateGuard
    if ($privateClientSha -ne $expectedClient -or
        $privateClientSha -ne $actualClient) {
        throw (
            'Invocation-owned exact-v308 input snapshot identity mismatch. ' +
            "Expected: $expectedClient Source: $actualClient Snapshot: $privateClientSha"
        )
    }

    $sourceGuard.Dispose()
    $sourceGuard = $null

    Write-Host (
        'V308_LOCAL_CLIENT_BUILD_PREFLIGHT_PASS ' +
        "clientSha256=$privateClientSha canonicalClient=true " +
        'invocationOwnedClient=true canonicalOutput=true'
    ) -ForegroundColor Green

    # The Python publication transaction owns creation of an absent canonical
    # local-client directory so rollback can distinguish transaction-created
    # directory state from pre-existing caller state. Re-prove path safety
    # immediately before crossing the process boundary, but do not create it here.
    $output = Assert-CanonicalOutputPathSafe $output

    & $python.Source $patcher $snapshotClient $output
    if ($LASTEXITCODE -ne 0) {
        throw "v308 local-client patcher exited with code $LASTEXITCODE"
    }
}
catch {
    $patcherFailure = $_
}
finally {
    if ($null -ne $privateGuard) {
        try {
            $privateGuard.Dispose()
            $privateGuard = $null
        }
        catch {
            $cleanupFailures +=
                "private guard dispose: $($_.Exception.Message)"
        }
    }

    if ($null -ne $sourceGuard) {
        try {
            $sourceGuard.Dispose()
            $sourceGuard = $null
        }
        catch {
            $cleanupFailures +=
                "source guard dispose: $($_.Exception.Message)"
        }
    }

    if (Test-Path -LiteralPath $snapshotRoot) {
        try {
            Remove-Item -LiteralPath $snapshotRoot -Recurse -Force -ErrorAction Stop
        }
        catch {
            $cleanupFailures +=
                "snapshot root cleanup: $($_.Exception.Message)"
        }
    }
}

if ($null -ne $patcherFailure) {
    if ($cleanupFailures.Count -ne 0) {
        $patcherFailure.Exception.Data['V308InputSnapshotCleanupFailure'] =
            ($cleanupFailures -join ' | ')
    }
    throw $patcherFailure
}

if ($cleanupFailures.Count -ne 0) {
    throw (
        'v308 local-client input snapshot cleanup failed: ' +
        ($cleanupFailures -join ' | ')
    )
}

& (Join-Path $PSScriptRoot 'Check-ExternalRuntime.ps1')
if ($LASTEXITCODE -ne 0) {
    throw "External runtime verification exited with code $LASTEXITCODE"
}

Write-Host 'V308_LOCAL_CLIENT_BUILD_AND_VERIFY_PASS' -ForegroundColor Green
