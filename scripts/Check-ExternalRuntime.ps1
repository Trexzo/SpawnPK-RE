Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=Split-Path -Parent $PSScriptRoot
$files=@(
    @("PINNED_CLIENT","evidence\client(6).jar","854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"),
    @("AIRGAP_CLIENT","local-client\client-airgap.jar","46b7d7d35c38fdd2ea49c3c7b7bea1406787f5f188d9fe102faedd20c983554a"),
    @("LOCALHOST_CLIENT","local-client\client-localhost.jar","b8bccc927de4599d2d7b3d93ad66d91087f504e4e5250b9140b5b942cdc737d4")
)
foreach($row in $files){
    $p=Join-Path $repo $row[1]
    if(-not(Test-Path -LiteralPath $p -PathType Leaf)){throw "$($row[0]) missing: $p"}
    $a=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()
    if($a -ne $row[2]){throw "$($row[0]) hash mismatch.
Expected: $($row[2])
Actual:   $a"}
    Write-Host "$($row[0]) HASH OK" -ForegroundColor Green
}
Write-Host "EXTERNAL_RUNTIME_OK exactClient=v308 coherentTriplet=true" -ForegroundColor Green