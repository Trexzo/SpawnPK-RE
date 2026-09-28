Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=Split-Path -Parent $PSScriptRoot
$files=@(
    @("PINNED_CLIENT","evidence\client(6).jar","854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"),
    @("AIRGAP_CLIENT","local-client\client-airgap.jar","83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33"),
    @("LOCALHOST_CLIENT","local-client\client-localhost.jar","01c878a56ee25fb112dfe8b459dbd11ea26cfa8a92a7f287a4e5ee53f673cdbd")
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