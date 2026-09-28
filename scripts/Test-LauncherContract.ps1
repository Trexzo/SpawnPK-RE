param(
    [switch]$SkipJavaProbe
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot

function Assert-True(
    [bool]$Condition,
    [string]$Message
) {
    if (-not $Condition) {
        throw $Message
    }
}

function Read-RepoFile([string]$RelativePath) {
    $path = Join-Path $repo $RelativePath
    Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "Missing required launcher file: $RelativePath"
    return Get-Content -LiteralPath $path -Raw
}

function Assert-Parses([string]$RelativePath) {
    $path = Join-Path $repo $RelativePath
    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $path,
        [ref]$tokens,
        [ref]$errors
    )
    if ($errors.Count -gt 0) {
        $detail = ($errors | ForEach-Object { $_.Message }) -join '; '
        throw "PowerShell parse failed for $RelativePath : $detail"
    }
}

$launcherFiles = @(
    'RUN_CLIENT_AIRGAP.ps1',
    'RUN_ALL_LOCAL_LAB.ps1',
    'WATCH_CLIENT_NETWORK.ps1',
    'scripts\Run-Server.ps1',
    'scripts\Run-Client-Airgap.ps1',
    'scripts\Select-LocalLabJava.ps1',
    'scripts\Check-ExternalRuntime.ps1',
    'scripts\Build-V308LocalClients.ps1'
)

foreach ($file in $launcherFiles) {
    Assert-Parses $file
}

$client = Read-RepoFile 'RUN_CLIENT_AIRGAP.ps1'
$all = Read-RepoFile 'RUN_ALL_LOCAL_LAB.ps1'
$serverWrapper = Read-RepoFile 'scripts\Run-Server.ps1'
$clientWrapper = Read-RepoFile 'scripts\Run-Client-Airgap.ps1'
$selector = Read-RepoFile 'scripts\Select-LocalLabJava.ps1'
$ignore = Read-RepoFile '.gitignore'
$externalRuntime = Read-RepoFile 'scripts\Check-ExternalRuntime.ps1'
$runtimeImport = Read-RepoFile 'IMPORT_EXISTING_RUNTIME.ps1'
$runtimeBuilder = Read-RepoFile 'scripts\Build-V308LocalClients.ps1'
$v308Patcher = Read-RepoFile 'tools\runtime\build_v308_local_clients.py'

Assert-True ($client -match [regex]::Escape('scripts\Select-LocalLabJava.ps1')) 'Standalone airgap launcher does not use canonical Java selector.'
Assert-True ($client -match 'Set-LocalLabJava') 'Standalone airgap launcher does not invoke Set-LocalLabJava.'
Assert-True ($client -match '&\s+\$java\.Path\s+@javaArgs\s+-jar\s+\$jar') 'Standalone airgap launcher does not invoke the selected Java executable with the optional JVM argument vector.'
Assert-True ($client -match 'SPAWNPK_LOCALLAB_USER_HOME') 'Standalone airgap launcher does not expose the isolated LocalLab user.home environment contract.'
Assert-True ($client -match '-Duser\.home=\$resolvedHome') 'Standalone airgap launcher does not pass the isolated home to Java.'
Assert-True ($client -match 'LOCAL_LAB_CLIENT_HOME_ISOLATED') 'Standalone airgap launcher does not report isolated client-home authority.'
Assert-True ($client -match '\$cacheRoot\s*=\s*Join-Path\s+\$resolvedHome\s+''\.spawnpk''') 'Standalone airgap launcher does not derive the exact v308 cache root from isolated user.home.'
Assert-True ($client -match '\$dataRoot\s*=\s*Join-Path\s+\$resolvedHome\s+''\.spawnpk-data''') 'Standalone airgap launcher does not derive the exact v308 data root from isolated user.home.'
Assert-True ($client -match 'Refusing LocalLab isolated user\.home because it resolves to the real OS user home') 'Standalone airgap launcher does not reject the real OS user home.'
Assert-True ($client -match 'missing cache root') 'Standalone airgap launcher does not fail closed on an unseeded isolated cache root.'
Assert-True ($client -match 'LOCAL_LAB_CLIENT_HOME_DEFAULT') 'Standalone airgap launcher no longer preserves the ordinary non-isolated launch path.'
Assert-True ($clientWrapper -match 'LocalLabUserHome') 'Canonical airgap wrapper does not forward isolated client-home authority.'

Assert-True ($externalRuntime -match '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6') 'External runtime no longer pins exact v308 evidence client.'
Assert-True ($externalRuntime -match '83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33') 'External runtime no longer pins exact v308 airgap client.'
Assert-True ($externalRuntime -match '01c878a56ee25fb112dfe8b459dbd11ea26cfa8a92a7f287a4e5ee53f673cdbd') 'External runtime no longer pins exact v308 localhost client.'
Assert-True ($externalRuntime -match 'coherentTriplet=true') 'External runtime does not report coherent v308 triplet authority.'
Assert-True ($runtimeImport -match '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6') 'Runtime importer no longer requires exact v308 evidence client.'
Assert-True ($runtimeImport -match '83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33') 'Runtime importer no longer requires exact v308 airgap client.'
Assert-True ($runtimeImport -match '01c878a56ee25fb112dfe8b459dbd11ea26cfa8a92a7f287a4e5ee53f673cdbd') 'Runtime importer no longer requires exact v308 localhost client.'
Assert-True ($runtimeBuilder -match 'build_v308_local_clients\.py') 'PowerShell runtime builder does not invoke the deterministic v308 patcher.'
Assert-True ($runtimeBuilder -match 'Check-ExternalRuntime\.ps1') 'PowerShell runtime builder does not verify the rebuilt exact-v308 triplet.'
Assert-True ($runtimeBuilder -match 'V308_LOCAL_CLIENT_BUILD_AND_VERIFY_PASS') 'PowerShell runtime builder marker missing.'
Assert-True ($v308Patcher -match 'ZIP_STORED') 'v308 local-client patcher no longer uses compression-independent deterministic JAR entries.'
Assert-True ($v308Patcher -match 'wholeJarDeterminismIndependentOfZlib') 'v308 local-client manifest no longer records zlib-independent whole-JAR determinism.'
Assert-True ($v308Patcher -notmatch 'ZIP_DEFLATED') 'v308 local-client patcher reintroduced zlib-dependent output compression.'

Assert-True ($all -match [regex]::Escape('scripts\Select-LocalLabJava.ps1')) 'Multi-client launcher does not use canonical Java selector.'
Assert-True ($all -match [regex]::Escape('scripts\Run-Server.ps1')) 'Multi-client launcher does not target scripts/Run-Server.ps1.'
Assert-True ($all -match [regex]::Escape('scripts\Run-Client-Airgap.ps1')) 'Multi-client launcher does not target scripts/Run-Client-Airgap.ps1.'
Assert-True ($all -match [regex]::Escape('WATCH_CLIENT_NETWORK.ps1')) 'Multi-client launcher does not target WATCH_CLIENT_NETWORK.ps1.'
Assert-True ($all -match [regex]::Escape('scripts\Check-ExternalRuntime.ps1')) 'Multi-client launcher does not use current external-runtime preflight.'
$allTokens = $null
$allParseErrors = $null
[void][System.Management.Automation.Language.Parser]::ParseInput(
    $all,
    [ref]$allTokens,
    [ref]$allParseErrors
)
$sealedVerifierTokens = @(
    $allTokens | Where-Object {
        ([string]$_.Kind) -ne 'Comment' -and
        $_.Text -match 'VERIFY_OFFLINE_READY\.ps1'
    }
)
Assert-True ($sealedVerifierTokens.Count -eq 0) 'Multi-client launcher still invokes the sealed historical R8.5 verifier.'
Assert-True ($all -match [regex]::Escape('server\build\SpawnPKLocalServer.jar')) 'Multi-client launcher does not preflight the current built server JAR.'
Assert-True ($all -match 'existingAirgapPids') 'Multi-client launcher does not distinguish a newly started airgap client from pre-existing clients.'
Assert-True ($all -match 'AIRGAP_CLIENT_PROCESS_READY') 'Multi-client launcher does not prove a new airgap Java process started before reporting success.'
Assert-True ($all -match 'SERVER_PORTS_READY game=43594 aux=43595') 'Multi-client launcher does not require both game and AUX listeners before client launch.'
Assert-True ($all -match 'SERVER_PROCESS_READY') 'Multi-client launcher does not prove both listeners belong to an expected LocalLab Java process.'
Assert-True ($all -match 'readyOwnerPids.Count -eq 1') 'Multi-client launcher does not require one server process to own both startup listeners.'
Assert-True ($all -match 'stableOwnerPids.Count -ne 1') 'Multi-client launcher does not recheck singular server ownership after client startup.'
Assert-True ($all -match 'expectedOwner=\$serverOwnerPid') 'Multi-client launcher does not retain expected server listener ownership through stabilization.'
Assert-True ($all -match 'readyPortNumbers -contains 43595') 'Multi-client launcher does not gate client launch on AUX port 43595.'
Assert-True ($all -match 'AIRGAP_CLIENT_PROCESS_STABLE') 'Multi-client launcher does not require the new airgap Java process to survive stabilization.'
Assert-True ($all -match 'Start-Sleep -Seconds 2') 'Multi-client launcher does not retain the airgap client through the required stabilization dwell.'
Assert-True ($all -match 'stablePorts -notcontains 43595') 'Multi-client launcher does not recheck AUX listener survival after client startup.'
Assert-True ($all -match 'client-airgap\\\.jar') 'Multi-client launcher does not identify the airgap client process by client-airgap.jar.'
Assert-True ($all -notmatch 'RUN_SERVER_LOCAL_WORLD\.ps1') 'Stale RUN_SERVER_LOCAL_WORLD.ps1 target remains.'
Assert-True ($all -match "'-File'") 'Child launchers are not using explicit PowerShell -File execution.'
Assert-True ($all -match 'AddSeconds\(30\)') 'Server-ready deadline is not the required 30-second window.'
Assert-True ($all -match 'R85 JAVA11\+ AUTOSELECT BEGIN') 'Compatibility selector marker was removed.'

Assert-True ($serverWrapper -match 'Select-LocalLabJava\.ps1') 'Server wrapper is not using the canonical Java selector.'
Assert-True ($clientWrapper -match 'Select-LocalLabJava\.ps1') 'Client wrapper is not using the canonical Java selector.'
Assert-True ($selector -match 'Major -eq 17') 'Canonical selector no longer prefers the proven Java 17 runtime.'

Assert-True ($ignore -match '(?m)^\*\.log\s*$') '*.log is not ignored.'
Assert-True ($ignore -match '(?m)^\*\.lock\s*$') '*.lock is not ignored.'
Assert-True ($ignore -match '(?m)^\*\.pid\s*$') '*.pid is not ignored.'
Assert-True ($ignore -match '(?m)^runtime/locallab-user-home/\s*$') 'LocalLab isolated user.home runtime tree is not ignored.'

if (-not $SkipJavaProbe) {
    . (Join-Path $repo 'scripts\Select-LocalLabJava.ps1')
    $java = Set-LocalLabJava
    Assert-True ($null -ne $java) 'Canonical selector returned no Java runtime.'
    Assert-True ([int]$java.Major -ge 11) "Selected Java is below 11: $($java.Major)"
    Assert-True (Test-Path -LiteralPath $java.Path -PathType Leaf) "Selected Java path is missing: $($java.Path)"
}

Write-Host 'LOCALLAB_LAUNCHER_CONTRACT_PASS' -ForegroundColor Green
