param([Parameter(Mandatory=$true)][string]$From)
Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=$PSScriptRoot
$items=@(
    @("evidence\client(6).jar","854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6","PINNED_CLIENT"),
    @("local-client\client-airgap.jar","46b7d7d35c38fdd2ea49c3c7b7bea1406787f5f188d9fe102faedd20c983554a","AIRGAP_CLIENT"),
    @("local-client\client-localhost.jar","b8bccc927de4599d2d7b3d93ad66d91087f504e4e5250b9140b5b942cdc737d4","LOCALHOST_CLIENT")
)
foreach($row in $items){
    $src=Join-Path $From $row[0]
    if(-not(Test-Path -LiteralPath $src -PathType Leaf)){throw "$($row[2]) missing: $src"}
    $actual=(Get-FileHash -LiteralPath $src -Algorithm SHA256).Hash.ToLowerInvariant()
    if($actual -ne $row[1]){throw "$($row[2]) source hash mismatch.
Expected: $($row[1])
Actual:   $actual"}
    $dst=Join-Path $repo $row[0]
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dst) | Out-Null
    Copy-Item -LiteralPath $src -Destination $dst -Force
    Write-Host "$($row[2]) IMPORTED" -ForegroundColor Green
}
Write-Host "EXTERNAL_RUNTIME_IMPORT_PASS exactClient=v308 coherentTriplet=true" -ForegroundColor Green