Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=Split-Path -Parent $PSScriptRoot
$files=@(
    @("PINNED_CLIENT","evidence\client(6).jar","854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"),
    @("AIRGAP_CLIENT","local-client\client-airgap.jar","024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7"),
    @("LOCALHOST_CLIENT","local-client\client-localhost.jar","15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e")
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