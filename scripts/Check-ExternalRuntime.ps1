Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=Split-Path -Parent $PSScriptRoot
$files=@(
    @("PINNED_CLIENT","evidence\client(6).jar","6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662"),
    @("AIRGAP_CLIENT","local-client\client-airgap.jar","9ff1b80fe81b1af2e174df71db33f5aeb5ee489c265ab2880bcda35f7797019b"),
    @("LOCALHOST_CLIENT","local-client\client-localhost.jar","139cf87e18eed052707b05c9f49cac167ee9cc200600c74ed53a0035bfdac8cb")
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
Write-Host "EXTERNAL_RUNTIME_OK" -ForegroundColor Green