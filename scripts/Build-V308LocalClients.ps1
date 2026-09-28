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

$actualClient =
    (Get-FileHash -LiteralPath $client -Algorithm SHA256).Hash.ToLowerInvariant()

if ($actualClient -ne $expectedClient) {
    throw "Exact v308 client hash mismatch. Expected $expectedClient actual $actualClient"
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

Write-Host (
    'V308_LOCAL_CLIENT_BUILD_PREFLIGHT_PASS ' +
    "clientSha256=$actualClient canonicalClient=true canonicalOutput=true"
) -ForegroundColor Green

# The Python publication transaction owns creation of an absent canonical
# local-client directory so rollback can distinguish transaction-created
# directory state from pre-existing caller state. Re-prove path safety
# immediately before crossing the process boundary, but do not create it here.
$output = Assert-CanonicalOutputPathSafe $output

& $python.Source $patcher $client $output
if ($LASTEXITCODE -ne 0) {
    throw "v308 local-client patcher exited with code $LASTEXITCODE"
}

& (Join-Path $PSScriptRoot 'Check-ExternalRuntime.ps1')
if ($LASTEXITCODE -ne 0) {
    throw "External runtime verification exited with code $LASTEXITCODE"
}

Write-Host 'V308_LOCAL_CLIENT_BUILD_AND_VERIFY_PASS' -ForegroundColor Green
