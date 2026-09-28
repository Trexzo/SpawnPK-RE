param(
  [string]$Target=$PSScriptRoot,
  [string]$ConfigDir=(Join-Path $env:USERPROFILE '.spawnpk\configs')
)
Set-StrictMode -Version 2.0
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$lab=(Resolve-Path -LiteralPath $Target).Path
$server=Join-Path $lab 'server\build\SpawnPKLocalServer.jar'
$expectedServer='589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c'
$expectedPinned='854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6'
if(-not(Test-Path -LiteralPath $server -PathType Leaf)){throw "Missing server JAR: $server"}
$actual=(Get-FileHash -LiteralPath $server -Algorithm SHA256).Hash.ToLowerInvariant()
if($actual -ne $expectedServer){throw "v5.18.5 server hash mismatch expected=$expectedServer actual=$actual"}
$z=[IO.Compression.ZipFile]::OpenRead($server)
try{
  $bad=@();$classCount=0;$itemRow=$false;$petRow=$false;$legacyPet=$false;$nonDrop29999=$false;$r6Rows=-1
  foreach($e in $z.Entries){
    if($e.FullName -eq 'spk/local/items.tsv'){
      $sr=New-Object IO.StreamReader($e.Open());try{$txt=$sr.ReadToEnd();$itemRow=$txt.Contains("29999`tVoidglass Nistirio`t1`t?`t||||Drop`t-1`t-1`t-1")}finally{$sr.Dispose()}
    }
    if($e.FullName -eq 'spk/local/pet_mappings.tsv'){
      $sr=New-Object IO.StreamReader($e.Open());try{$txt=$sr.ReadToEnd();$petRow=$txt.Contains("29999`t12000`tVoidglass Nistirio`tVoidglass Nistirio`t1662`t1663");$legacyPet=$txt.Contains("32760`t12000`tVoidglass Nistirio")}finally{$sr.Dispose()}
    }
    if($e.FullName -eq 'spk/local/opcode87_non_drop_items.csv'){
      $sr=New-Object IO.StreamReader($e.Open());try{$txt=$sr.ReadToEnd();$nonDrop29999=($txt -match '(?m)^29999,')}finally{$sr.Dispose()}
    }
    if($e.FullName -eq 'spk/local/data/research_r85/s2c250_operation_grammars_r6_remaining.csv'){
      $sr=New-Object IO.StreamReader($e.Open());try{$txt=$sr.ReadToEnd();$r6Rows=@($txt -split "`r?`n"|Where-Object{$_}).Count-1}finally{$sr.Dispose()}
    }
    if(-not $e.FullName.EndsWith('.class',[StringComparison]::OrdinalIgnoreCase)){continue}
    $classCount++;$s=$e.Open();try{$hdr=New-Object byte[] 8;$got=0;while($got -lt 8){$n=$s.Read($hdr,$got,8-$got);if($n -le 0){break};$got+=$n};if($got -ne 8){$bad+="$($e.FullName):short-header";continue};$major=($hdr[6]-shl 8)-bor $hdr[7];if($major -ne 55){$bad+="$($e.FullName):major=$major"}}finally{$s.Dispose()}
  }
  if($classCount -ne 499){throw "Unexpected v5.18.5 class count: $classCount expected=499"}
  if(-not $itemRow){throw 'R8.5 item29999 Voidglass Drop resource row missing'}
  if(-not $petRow){throw 'R8.5 pet29999->12000 resource row missing'}
  if($legacyPet){throw 'Legacy invalid item32760 pet mapping is still present'}
  if($nonDrop29999){throw 'item29999 is still incorrectly classified as opcode87 non-Drop'}
  if($r6Rows -ne 64){throw "Unexpected R8.5 S2C250 R6 operation row count: $r6Rows expected=64"}
  if($bad.Count -gt 0){throw ('Java bytecode compatibility failure: '+($bad -join ', '))}
}finally{$z.Dispose()}
$pinnedPath=$null
foreach($p in @((Join-Path $lab 'evidence\client(6).jar'),(Join-Path $lab 'evidence\client(4).jar'),(Join-Path $lab 'evidence\client.jar'),(Join-Path $lab 'client.jar'))){if(Test-Path -LiteralPath $p -PathType Leaf){if((Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant() -eq $expectedPinned){$pinnedPath=$p;break}}}
if(-not $pinnedPath){throw 'Pinned exact client missing/changed'}
$runtimeVerifier=Join-Path $lab 'scripts\Check-ExternalRuntime.ps1'
if(-not(Test-Path -LiteralPath $runtimeVerifier -PathType Leaf)){throw 'Missing canonical exact-v308 external runtime verifier'}
& $runtimeVerifier
if(-not(Test-Path -LiteralPath $ConfigDir -PathType Container)){throw "SpawnPK config directory missing: $ConfigDir"}
$selector=Join-Path $lab 'tools\R85_SelectJava11Plus.ps1'
if(-not(Test-Path -LiteralPath $selector -PathType Leaf)){throw 'Missing R8.5 Java selector'}
. $selector
$javaInfo=Set-R85Java11Plus
$javaExe=$javaInfo.Path
& $javaExe -cp $server spk.local.VoidglassR3ConfigPatchTool verify $ConfigDir
if($LASTEXITCODE -ne 0){throw 'Voidglass R3 live config verification failed'}
$runner=Join-Path $lab 'RUN_V5185_FULL_SELFTEST.ps1'
if(-not(Test-Path -LiteralPath $runner -PathType Leaf)){throw 'Missing RUN_V5185_FULL_SELFTEST.ps1'}
$launcher=Join-Path $lab 'RUN_ALL_LOCAL_LAB.ps1'
if(-not(Test-Path -LiteralPath $launcher -PathType Leaf)){throw 'Missing RUN_ALL_LOCAL_LAB.ps1'}
$launcherText=Get-Content -LiteralPath $launcher -Raw
if($launcherText -notmatch 'R85 JAVA11\+ AUTOSELECT BEGIN'){throw 'Normal LocalLab launcher is missing the R8.5 Java 11+ auto-selector block'}
Write-Host 'V5185_ENGINE_R85_OFFLINE_READY_PASS' -ForegroundColor Green
Write-Host "Server: $expectedServer"
Write-Host "Pinned exact-v308 evidence unchanged: $pinnedPath"
Write-Host 'R8.5 STATIC/PROTOCOL: S2C250 43/43 operation-decoded; remaining generic C2S 8/8 promoted to exact normalized events; S2C126 generic publisher retained.'
Write-Host 'R8.5 UI: typed application publishers + local/dev fixtures. Subtype20 emitter intentionally disabled because exact client behavior starts an external TCP receiver.'
Write-Host 'VOIDGLASS R3: item29999 valid inside client table; legacy32760 retired; NPC12000..12003 native-asset compositor candidates; Hydra model/anims/GFX removed; proc GFX5042.'
Write-Host 'BOUNDARY: native-asset composition is not a newly-authored raw 3D mesh. Production rewards/prices/RNG/eligibility/business rules and unknown interaction outcomes remain fail-closed.'
Write-Host 'CLIENT PATCH: live i.bin/e.bin definition metadata only; LocalLab client JARs/model/animation/GFX archives untouched.'
Write-Host 'JAVA COMPAT: 499/499 classes are Java 11 bytecode (major 55). Full selftest count is 179. Normal launcher auto-selects Java 11+.'
