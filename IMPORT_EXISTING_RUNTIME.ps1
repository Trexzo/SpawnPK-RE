param([Parameter(Mandatory=$true)][string]$From)
Set-StrictMode -Version 2.0
$ErrorActionPreference="Stop"
$repo=$PSScriptRoot

. (Join-Path $repo "scripts\LocalLab-Visuals.ps1")
Set-LocalLabWindowTitle -Title "Runtime Import"
Write-LocalLabHeader -Phase "RUNTIME IMPORT"
Write-LocalLabFlavor -Stage "Import"
Write-LocalLabStatus -Label "SOURCE" -Value $From -Color Cyan

$items=@(
    @("evidence\client(6).jar","6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662","PINNED_CLIENT"),
    @("local-client\client-airgap.jar","9ff1b80fe81b1af2e174df71db33f5aeb5ee489c265ab2880bcda35f7797019b","AIRGAP_CLIENT"),
    @("local-client\client-localhost.jar","139cf87e18eed052707b05c9f49cac167ee9cc200600c74ed53a0035bfdac8cb","LOCALHOST_CLIENT")
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

Write-Host "EXTERNAL_RUNTIME_IMPORT_PASS" -ForegroundColor Green
Write-LocalLabStatus -Label "IMPORT" -Value "verified runtime copied successfully" -Color Green
