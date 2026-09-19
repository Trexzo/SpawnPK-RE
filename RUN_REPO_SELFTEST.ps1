param(
    [string]$Target = $PSScriptRoot,
    [string]$ConfigDir = (Join-Path $env:USERPROFILE ".spawnpk\configs")
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$harness = Join-Path $PSScriptRoot "RUN_V5185_FULL_SELFTEST.ps1"

$prior = $env:SPK_ALLOW_DEV_BUILD
$env:SPK_ALLOW_DEV_BUILD = "1"

try {
    & $harness -Target $Target -ConfigDir $ConfigDir

    if ($LASTEXITCODE -ne 0) {
        throw "R8.5 development-source regression suite failed."
    }
}
finally {
    if ($null -eq $prior) {
        Remove-Item Env:\SPK_ALLOW_DEV_BUILD -ErrorAction SilentlyContinue
    }
    else {
        $env:SPK_ALLOW_DEV_BUILD = $prior
    }
}