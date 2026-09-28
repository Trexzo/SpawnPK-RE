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

$python = Get-Command python -ErrorAction SilentlyContinue
if ($null -eq $python) {
    throw 'Python is required to build the exact-v308 LocalLab client variants.'
}

Write-Host (
    'V308_LOCAL_CLIENT_BUILD_PREFLIGHT_PASS ' +
    "clientSha256=$actualClient canonicalClient=true canonicalOutput=true"
) -ForegroundColor Green

# No canonical output state is created before every admission preflight above passes.
New-Item -ItemType Directory -Force -Path $output | Out-Null

& $python.Source $patcher $client $output
if ($LASTEXITCODE -ne 0) {
    throw "v308 local-client patcher exited with code $LASTEXITCODE"
}

& (Join-Path $PSScriptRoot 'Check-ExternalRuntime.ps1')
if ($LASTEXITCODE -ne 0) {
    throw "External runtime verification exited with code $LASTEXITCODE"
}

Write-Host 'V308_LOCAL_CLIENT_BUILD_AND_VERIFY_PASS' -ForegroundColor Green
