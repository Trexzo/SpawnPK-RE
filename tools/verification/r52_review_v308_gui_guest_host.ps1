<#
R52: read-only Windows HOST review of a staged R44-style v308 GUI guest.
NO Java/Sandbox launch, network, Windows optional-feature query, OS changes,
profile access, mutations, or host process termination.
A present WindowsSandbox.exe is NOT proof of an operational isolated guest.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ReviewRoot,
    [Parameter(Mandatory=$true)][string]$ExpectedSourceHead,
    [Parameter(Mandatory=$true)][string]$ExpectedServerSha256
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'

function Require([bool]$condition,[string]$why) {
    if(-not $condition) { throw ('R52_REFUSED: '+$why) }
}
function ExactFile([string]$path,[string]$sha) {
    Require ($sha -cmatch '^[0-9a-f]{64}$') 'expected lowercase SHA256 invalid'
    Require (Test-Path -LiteralPath $path -PathType Leaf) 'required file missing'
    $file=Get-Item -LiteralPath $path -Force -ErrorAction Stop
    Require (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'reparse-point file refused'
    $actual=(Get-FileHash -LiteralPath $path -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
    Require ($actual -ceq $sha) 'file SHA256 mismatch'
}
function ReadBoundedJson([string]$path) {
    Require (Test-Path -LiteralPath $path -PathType Leaf) 'JSON metadata missing'
    Require ((Get-Item -LiteralPath $path).Length -le 16384) 'JSON metadata unexpectedly large'
    return (Get-Content -LiteralPath $path -Raw -ErrorAction Stop |
        ConvertFrom-Json -ErrorAction Stop)
}
try {
    Require ($env:OS -ceq 'Windows_NT') 'this is a Windows host-only audit'
    Require ($env:USERNAME -cne 'WDAGUtilityAccount') 'never run host probe inside guest'
    Require ($ExpectedSourceHead -cmatch '^[0-9a-f]{40}$') 'source commit SHA invalid'
    Require ($ExpectedServerSha256 -cmatch '^[0-9a-f]{64}$') 'server SHA256 invalid'

    $root=[IO.Path]::GetFullPath($ReviewRoot).TrimEnd('\')
    Require (Test-Path -LiteralPath $root -PathType Container) 'staging root missing'
    $rootItem=Get-Item -LiteralPath $root -Force -ErrorAction Stop
    Require (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'staging root reparse point'
    $assets=Join-Path $root 'assets'
    Require (Test-Path -LiteralPath $assets -PathType Container) 'assets folder missing'
    $assetItem=Get-Item -LiteralPath $assets -Force -ErrorAction Stop
    Require (($assetItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'assets reparse point'

    $receipt=ReadBoundedJson (Join-Path $root 'r44-review-only-receipt.json')
    $pins=ReadBoundedJson (Join-Path $assets 'asset-pins.json')
    # The legacy R44 receipt has an R43-specific result label; never infer
    # server version from that label. The signed-off exact source and binary
    # hash are authoritative within this local static review.
    Require ($receipt.result -ceq 'R44_V308_GUI_R43_PINNED_GUEST_BUNDLE_STAGED_NO_EXECUTION') 'not an R44-format staged receipt'
    Require ($receipt.source_head -ceq $ExpectedSourceHead -and
        $pins.source_head -ceq $ExpectedSourceHead) 'stage source commit mismatch'
    Require ($receipt.server_jar_sha256 -ceq $ExpectedServerSha256 -and
        $pins.server_jar_sha256 -ceq $ExpectedServerSha256) 'stage server SHA mismatch'
    Require ($receipt.networking_disabled -ceq $true -and
        $receipt.clipboard_disabled -ceq $true -and
        $receipt.vgpu_disabled -ceq $true -and
        $receipt.logon_command_present -ceq $false -and
        $receipt.windows_sandbox_started -ceq $false -and
        $receipt.original_v308_gui_started -ceq $false -and
        $receipt.actual_gui_login_verified -ceq $false) 'review-only receipt asserts unsafe or unverified state'

    $clientSha='024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7'
    $javaSha='b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef'
    Require ($receipt.certified_airgap_v308_sha256 -ceq $clientSha -and
        $pins.airgap_sha256 -ceq $clientSha) 'client pin mismatch'
    Require ($receipt.guest_jdk_java_sha256 -ceq $javaSha -and
        $pins.java_sha256 -ceq $javaSha) 'JDK pin mismatch'

    $wsb=Join-Path $root 'SpawnPK-R44-NetworkDisabled-REVIEW-ONLY.wsb'
    Require (Test-Path -LiteralPath $wsb -PathType Leaf) 'review WSB missing'
    Require ((Get-Item -LiteralPath $wsb).Length -le 16384) 'WSB unexpectedly large'
    [xml]$document=Get-Content -LiteralPath $wsb -Raw -ErrorAction Stop
    $cfg=$document.Configuration
    Require ($cfg -ne $null) 'WSB lacks Configuration'
    foreach($name in @('Networking','VGpu','AudioInput','VideoInput',
                       'PrinterRedirection','ClipboardRedirection')) {
        Require ($cfg.$name -ceq 'Disable') ('WSB unsafe setting '+$name)
    }
    Require ($cfg.ProtectedClient -ceq 'Enable') 'protected guest not requested'
    Require ($document.SelectNodes('//LogonCommand').Count -eq 0) 'WSB has automatic logon command'
    $mapped=@($document.SelectNodes('/Configuration/MappedFolders/MappedFolder'))
    Require ($mapped.Count -eq 2) 'expected exactly two guest mapped folders'
    $assetMap=@($mapped|Where-Object{ $_.SandboxFolder -ceq 'C:\SPKR23\assets' })
    $jdkMap=@($mapped|Where-Object{ $_.SandboxFolder -ceq 'C:\SPKR23\jdk' })
    Require ($assetMap.Count -eq 1 -and $jdkMap.Count -eq 1) 'guest mount targets wrong'
    Require (@($mapped|Where-Object{ $_.ReadOnly -cne 'true' }).Count -eq 0) 'writable guest mapped folder'
    $mappedAssets=[IO.Path]::GetFullPath([string]$assetMap[0].HostFolder).TrimEnd('\')
    Require ([string]::Equals($mappedAssets,$assets,[StringComparison]::OrdinalIgnoreCase)) 'guest assets map to wrong directory'
    $jdkRoot=[IO.Path]::GetFullPath([string]$jdkMap[0].HostFolder).TrimEnd('\')
    Require (Test-Path -LiteralPath $jdkRoot -PathType Container) 'mapped guest JDK missing'
    Require (((Get-Item -LiteralPath $jdkRoot -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'mapped JDK reparse point'
    Require ([string]::Equals(
        [IO.Path]::GetFullPath([string]$receipt.review_wsb),$wsb,
        [StringComparison]::OrdinalIgnoreCase)) 'receipt references a different WSB'

    ExactFile (Join-Path $assets 'client-airgap.jar') $clientSha
    ExactFile (Join-Path $assets 'SpawnPKLocalServer.jar') $ExpectedServerSha256
    ExactFile (Join-Path $jdkRoot 'bin\java.exe') $javaSha

    $sandboxExecutable=Join-Path $env:windir 'System32\WindowsSandbox.exe'
    $sandboxPresent=(Test-Path -LiteralPath $sandboxExecutable -PathType Leaf)
    $result=if($sandboxPresent) {
        'R52_STATIC_PINS_PASS_SANDBOX_BINARY_PRESENT_GUEST_UNVERIFIED'
    } else {
        'R52_STATIC_PINS_PASS_SANDBOX_BINARY_MISSING'
    }
    [ordered]@{
        result=$result
        source_head=$ExpectedSourceHead
        server_jar_sha256=$ExpectedServerSha256
        client_jar_sha256=$clientSha
        network_disabled=$true
        readonly_mounts=2
        sandbox_executable_present=[bool]$sandboxPresent
        sandbox_feature_state='UNKNOWN_NOT_QUERIED'
        guest_isolation_verified=$false
        graphical_client_started=$false
        gui_login_or_walk_verified=$false
        host_mutations=$false
        next_step='Separately verify and authorize an isolated offline Windows guest before any graphical launch'
    } | ConvertTo-Json -Depth 4
    exit 0
} catch {
    [ordered]@{
        result='R52_HOST_READINESS_REVIEW_BLOCKED'
        reason=$_.Exception.Message
        host_mutations=$false
        graphical_client_started=$false
    } | ConvertTo-Json -Depth 3
    exit 2
}
