param(
    [string]$ConfigDir = (Join-Path $env:USERPROFILE '.spawnpk\configs')
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

throw (
    'LOCALLAB_CONFIG_PATCH_RETIRED ' +
    'liveConfigMutation=false ' +
    'isolatedCachePipelineRequired=true. ' +
    'Direct i.bin/e.bin mutation was retired by Issue #9. ' +
    'Use scripts\Run-R13AssetAcceptance.ps1 or ' +
    'tools\custom-assets\build_r13_isolated_profile.py with an isolated cache copy. ' +
    "RequestedConfigDir=$ConfigDir"
)
