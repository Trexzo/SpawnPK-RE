<#
R44: stage the already-approved R23/R38 offline v308 Sandbox GUI bundle
with a newly built, exact-head LocalLab server JAR.

Runs on the HOST only to copy and hash private, existing inputs into a NEW
temporary folder. It NEVER starts any guest, server, Java, GUI or network
connection; does not edit Windows optional features, firewall, or the
source bundle. The resulting .wsb is REVIEW ONLY, with no LogonCommand.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$SourceAssets,
    [Parameter(Mandatory=$true)][string]$SourceWsb,
    [Parameter(Mandatory=$true)][string]$GuestJdkRoot,
    [Parameter(Mandatory=$true)][string]$ServerJar,
    [Parameter(Mandatory=$true)][string]$ExpectedServerSha256,
    [Parameter(Mandatory=$true)][string]$RepositoryHead,
    [Parameter(Mandatory=$true)][string]$DestinationRoot
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'

function Full-ExistingDirectory([string]$inputPath) {
    $full=[IO.Path]::GetFullPath($inputPath).TrimEnd('\')
    if(-not (Test-Path -LiteralPath $full -PathType Container)){
        throw "R44_REFUSED: missing required directory: $full"
    }
    $node=Get-Item -LiteralPath $full -Force -ErrorAction Stop
    if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){
        throw "R44_REFUSED: reparse-point input directory: $full"
    }
    return $full
}
function VerifiedHash([string]$inputPath,[string]$expected) {
    if($expected -cnotmatch '^[0-9a-f]{64}$'){
        throw 'R44_REFUSED: expected hash must be exact lower-case SHA256'
    }
    if(-not (Test-Path -LiteralPath $inputPath -PathType Leaf)){
        throw "R44_REFUSED: missing exact binary: $inputPath"
    }
    $actual=(Get-FileHash -LiteralPath $inputPath -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
    if($actual -cne $expected){
        throw "R44_REFUSED: binary hash mismatch: $inputPath"
    }
    return $actual
}

if($RepositoryHead -cnotmatch '^[0-9a-f]{40}$'){
    throw 'R44_REFUSED: exact committed Git head required'
}
$source=Full-ExistingDirectory $SourceAssets
$jdk=Full-ExistingDirectory $GuestJdkRoot
$server=[IO.Path]::GetFullPath($ServerJar)
$sourceWsbPath=[IO.Path]::GetFullPath($SourceWsb)
$dest=[IO.Path]::GetFullPath($DestinationRoot).TrimEnd('\')
$temp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
if(-not $dest.StartsWith($temp+'\',[StringComparison]::OrdinalIgnoreCase) -or
   -not [IO.Path]::GetFileName($dest).StartsWith('spawnpk-r44-guest-',
    [StringComparison]::OrdinalIgnoreCase) -or
   (Test-Path -LiteralPath $dest)){
    throw 'R44_REFUSED: destination must be a NEW spawnpk-r44-guest-* directory under Temp'
}
if(-not (Test-Path -LiteralPath $sourceWsbPath -PathType Leaf)){
    throw 'R44_REFUSED: source review-only WSB not found'
}
$oldPinsPath=Join-Path $source 'asset-pins.json'
if(-not (Test-Path -LiteralPath $oldPinsPath -PathType Leaf)){
    throw 'R44_REFUSED: original asset pins missing'
}
$pins=Get-Content -LiteralPath $oldPinsPath -Raw -ErrorAction Stop |
    ConvertFrom-Json -ErrorAction Stop
$expectedClient='024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7'
$expectedJava='b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef'
if($pins.airgap_sha256 -cne $expectedClient -or
   $pins.java_sha256 -cne $expectedJava -or
   $pins.source_head -cnotmatch '^[0-9a-f]{40}$'){
    throw 'R44_REFUSED: original R38 asset provenance mismatched'
}
[void](VerifiedHash (Join-Path $source 'client-airgap.jar') $expectedClient)
[void](VerifiedHash (Join-Path $jdk 'bin\java.exe') $expectedJava)
$newServerSha=(VerifiedHash $server $ExpectedServerSha256)
# The static GUI audit refers to the client only: do not rewrite a
# client provenance claim when replacing just the server JAR.
$auditPath=Join-Path $source 'r27-v308-gui-bootstrap-audit.json'
$audit=Get-Content -LiteralPath $auditPath -Raw -ErrorAction Stop |
    ConvertFrom-Json -ErrorAction Stop
if($audit.result -cne 'R27_PINNED_V308_REAL_GUI_BOOTSTRAP_STATIC_AUDIT_PASS' -or
   $audit.certified_airgap_v308_sha256 -cne $expectedClient -or
   $audit.client_gui_started -cne $false){
    throw 'R44_REFUSED: client GUI static audit missing or not a pre-launch audit'
}

[xml]$template=Get-Content -LiteralPath $sourceWsbPath -Raw -ErrorAction Stop
$cfg=$template.Configuration
if($cfg.Networking -cne 'Disable' -or
   $cfg.ClipboardRedirection -cne 'Disable' -or
   $cfg.VGpu -cne 'Disable' -or
   $cfg.ProtectedClient -cne 'Enable' -or
   $template.SelectNodes('//LogonCommand').Count -ne 0){
    throw 'R44_REFUSED: WSB template not an offline review-only configuration'
}
$folders=@($template.SelectNodes('/Configuration/MappedFolders/MappedFolder'))
if($folders.Count -ne 2){
    throw 'R44_REFUSED: source guest must have exactly two readonly mapped folders'
}
$assetMapping=@($folders|Where-Object{$_.SandboxFolder -ceq 'C:\SPKR23\assets'})
$jdkMapping=@($folders|Where-Object{$_.SandboxFolder -ceq 'C:\SPKR23\jdk'})
if($assetMapping.Count -ne 1 -or $jdkMapping.Count -ne 1 -or
   @($folders|Where-Object{$_.ReadOnly -cne 'true'}).Count -ne 0){
    throw 'R44_REFUSED: guest mappings must be exactly the two readonly R23 mounts'
}

# All validation is completed before creating the new folder. Neither
# original guest cache nor owner client profile is ever overwritten.
$assetDest=Join-Path $dest 'assets'
[void][IO.Directory]::CreateDirectory($assetDest)
Copy-Item -Path (Join-Path $source '*') -Destination $assetDest -Recurse -Force -ErrorAction Stop
Copy-Item -LiteralPath $server -Destination (Join-Path $assetDest 'SpawnPKLocalServer.jar') -Force -ErrorAction Stop
$pins.source_head=$RepositoryHead
$pins.server_jar_sha256=$newServerSha
$pins|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $assetDest 'asset-pins.json') -Encoding UTF8 -ErrorAction Stop

[void](VerifiedHash (Join-Path $assetDest 'client-airgap.jar') $expectedClient)
[void](VerifiedHash (Join-Path $assetDest 'SpawnPKLocalServer.jar') $newServerSha)
$assetMapping[0].HostFolder=$assetDest
$jdkMapping[0].HostFolder=$jdk
$wsb=Join-Path $dest 'SpawnPK-R44-NetworkDisabled-REVIEW-ONLY.wsb'
$template.Save($wsb)

[xml]$recheck=Get-Content -LiteralPath $wsb -Raw -ErrorAction Stop
if($recheck.Configuration.Networking -cne 'Disable' -or
   $recheck.Configuration.ClipboardRedirection -cne 'Disable' -or
   $recheck.SelectNodes('//LogonCommand').Count -ne 0 -or
   @($recheck.SelectNodes('/Configuration/MappedFolders/MappedFolder')|
       Where-Object{$_.ReadOnly -cne 'true'}).Count -ne 0){
    throw 'R44_REFUSED: generated guest config failed poststage safety validation'
}
$receipt=[ordered]@{
    result='R44_V308_GUI_R43_PINNED_GUEST_BUNDLE_STAGED_NO_EXECUTION'
    source_head=$RepositoryHead
    server_jar_sha256=$newServerSha
    certified_airgap_v308_sha256=$expectedClient
    guest_jdk_java_sha256=$expectedJava
    readonly_mounts=2
    networking_disabled=$true
    clipboard_disabled=$true
    vgpu_disabled=$true
    logon_command_present=$false
    original_v308_gui_started=$false
    windows_sandbox_started=$false
    actual_gui_login_verified=$false
    source_r38_assets_unchanged=$true
    review_wsb=$wsb
}
$receiptPath=Join-Path $dest 'r44-review-only-receipt.json'
$receipt|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $receiptPath -Encoding UTF8 -ErrorAction Stop
$receipt|ConvertTo-Json -Depth 6
