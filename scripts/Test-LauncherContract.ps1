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

function Assert-ExactTextCount(
    [string]$Text,
    [string]$Needle,
    [int]$Expected,
    [string]$Message
) {
    $actual = ([regex]::Matches($Text, [regex]::Escape($Needle))).Count
    Assert-True ($actual -eq $Expected) "$Message expected=$Expected actual=$actual needle=$Needle"
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
    'RUN_SECOND_LOCAL_CLIENT.ps1',
    'RUN_CLIENT_LOCALHOST_NONAIRGAP.ps1',
    'RUN_ALL_LOCAL_LAB.ps1',
    'RUN_LOCAL_LAB.ps1',
    'RUN_CURRENT_RELEASE_ACCEPTANCE.ps1',
    'IMPORT_EXISTING_RUNTIME.ps1',
    'BOOTSTRAP.ps1',
    'scripts\Build-Server.ps1',
    'RUN_REPO_SELFTEST.ps1',
    'RUN_V5185_FULL_SELFTEST.ps1',
    'VERIFY_OFFLINE_READY.ps1',
    'scripts\Patch-LocalConfigs.ps1',
    'WATCH_CLIENT_NETWORK.ps1',
    'scripts\Run-Server.ps1',
    'scripts\Run-Client-Airgap.ps1',
    'scripts\Select-LocalLabJava.ps1',
    'scripts\Check-ExternalRuntime.ps1',
    'scripts\Build-V308LocalClients.ps1',
    'scripts\Run-R13AssetAcceptance.ps1',
    'scripts\Run-Chat1CumulativeCertification.ps1'
)

foreach ($file in $launcherFiles) {
    Assert-Parses $file
}

$client = Read-RepoFile 'RUN_CLIENT_AIRGAP.ps1'
$secondClient = Read-RepoFile 'RUN_SECOND_LOCAL_CLIENT.ps1'
$nonAirgap = Read-RepoFile 'RUN_CLIENT_LOCALHOST_NONAIRGAP.ps1'
$all = Read-RepoFile 'RUN_ALL_LOCAL_LAB.ps1'
$quick = Read-RepoFile 'RUN_LOCAL_LAB.ps1'
$bootstrap = Read-RepoFile 'BOOTSTRAP.ps1'
$buildServer = Read-RepoFile 'scripts\Build-Server.ps1'
$repoSelftest = Read-RepoFile 'RUN_REPO_SELFTEST.ps1'
$fullSelftest = Read-RepoFile 'RUN_V5185_FULL_SELFTEST.ps1'
$offlineReady = Read-RepoFile 'VERIFY_OFFLINE_READY.ps1'
$configPatch = Read-RepoFile 'scripts\Patch-LocalConfigs.ps1'
$serverWrapper = Read-RepoFile 'scripts\Run-Server.ps1'
$clientWrapper = Read-RepoFile 'scripts\Run-Client-Airgap.ps1'
$selector = Read-RepoFile 'scripts\Select-LocalLabJava.ps1'
$ignore = Read-RepoFile '.gitignore'
$externalRuntime = Read-RepoFile 'scripts\Check-ExternalRuntime.ps1'
$runtimeImport = Read-RepoFile 'IMPORT_EXISTING_RUNTIME.ps1'
$runtimeBuilder = Read-RepoFile 'scripts\Build-V308LocalClients.ps1'
$v308Patcher = Read-RepoFile 'tools\runtime\build_v308_local_clients.py'
$r13Acceptance = Read-RepoFile 'scripts\Run-R13AssetAcceptance.ps1'
$releaseAcceptance = Read-RepoFile 'RUN_CURRENT_RELEASE_ACCEPTANCE.ps1'

$r13UniqueAnchors = @(
    '$runtimeRoot = Join-Path $repo ''runtime\locallab-user-home''',
    'Remove-Item -LiteralPath $output -Recurse -Force',
    'R13_RUNTIME_ACCEPTANCE_READY automaticVisualPass=false',
    'function Assert-OutputNotOwnedByRunningAirgapClient',
    '=== R13 isolated profile build ==='
)

foreach ($uniqueAnchor in $r13UniqueAnchors) {
    $count =
        ([regex]::Matches(
            $r13Acceptance,
            [regex]::Escape(
                $uniqueAnchor
            )
        )).Count

    Assert-True (
        $count -eq 1
    ) (
        "R13 acceptance launcher structural anchor count drift: " +
        "expected=1 actual=$count anchor=$uniqueAnchor"
    )
}

Assert-True ($r13Acceptance -match '\$hadCallerJavaHome\s*=\s*Test-Path Env:JAVA_HOME') 'R13 acceptance does not record caller JAVA_HOME ownership.'
Assert-True ($r13Acceptance -match '\$callerJavaHome\s*=\s*\$env:JAVA_HOME') 'R13 acceptance does not snapshot caller JAVA_HOME.'
Assert-True ($r13Acceptance -match '\$callerPath\s*=\s*\$env:Path') 'R13 acceptance does not snapshot caller PATH.'
Assert-True ($r13Acceptance -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'R13 acceptance does not restore an originally absent JAVA_HOME.'
foreach ($entry in @(
    @('$hadCallerJavaHome = Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome = $env:JAVA_HOME', 1),
    @('$callerPath = $env:Path', 1),
    @('$env:Path = $callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1)
)) {
    Assert-ExactTextCount $r13Acceptance $entry[0] ([int]$entry[1]) 'R13 acceptance Java-environment ownership count drift.'
}
$r13EnvCaptureIndex = $r13Acceptance.IndexOf('$callerPath = $env:Path')
$r13JavaIndex = $r13Acceptance.IndexOf('$java = Set-LocalLabJava')
$r13ReadyIndex = $r13Acceptance.IndexOf('R13_RUNTIME_ACCEPTANCE_READY automaticVisualPass=false')
$r13FinalInstructionIndex = $r13Acceptance.IndexOf('Do not record R13 visual PASS until those observations are made on the real GUI session.')
$r13EnvRestoreIndex = $r13Acceptance.LastIndexOf('$env:Path = $callerPath')
Assert-True ($r13EnvCaptureIndex -ge 0 -and $r13EnvCaptureIndex -lt $r13JavaIndex) 'R13 acceptance caller environment is not captured before canonical Java selection.'
Assert-True ($r13ReadyIndex -gt $r13JavaIndex) 'R13 readiness marker precedes canonical Java selection/acceptance flow.'
Assert-True ($r13FinalInstructionIndex -gt $r13ReadyIndex) 'R13 final operator instruction no longer follows readiness marker.'
Assert-True ($r13EnvRestoreIndex -gt $r13FinalInstructionIndex) 'R13 acceptance restores caller Java environment before the complete operator flow ends.'
$r13BuildServerPathIndex = $r13Acceptance.IndexOf('$buildServer = Join-Path $repo ''scripts\Build-Server.ps1''')
$r13ProfileReadyIndex = $r13Acceptance.IndexOf('R13_PROFILE_READY home=')
$r13BuildServerInvokeIndex = $r13Acceptance.IndexOf('& $buildServer')
$r13ServerBuildReadyIndex = $r13Acceptance.IndexOf('R13_SERVER_BUILD_READY serverJar=true')
$r13RunAllInvokeIndex = $r13Acceptance.IndexOf('& $runAll')
Assert-True ($r13BuildServerPathIndex -ge 0) 'R13 acceptance does not bind the canonical Build-Server launcher.'
Assert-ExactTextCount $r13Acceptance '$buildServer = Join-Path $repo ''scripts\Build-Server.ps1''' 1 'R13 acceptance Build-Server binding count drift.'
Assert-ExactTextCount $r13Acceptance '& $buildServer' 1 'R13 acceptance Build-Server invocation count drift.'
Assert-ExactTextCount $r13Acceptance '$buildServer,' 1 'R13 acceptance does not require the canonical Build-Server component exactly once.'
Assert-ExactTextCount $r13Acceptance '$serverJar = Join-Path $repo ''server\build\SpawnPKLocalServer.jar''' 1 'R13 acceptance server-JAR binding count drift.'
Assert-ExactTextCount $r13Acceptance 'R13 LocalLab server build failed with code' 1 'R13 acceptance lost explicit Build-Server exit-code failure.'
Assert-ExactTextCount $r13Acceptance 'R13 LocalLab server build completed without expected JAR:' 1 'R13 acceptance lost post-build server-JAR existence failure.'
Assert-True ($r13Acceptance -match 'Test-Path -LiteralPath \$serverJar -PathType Leaf') 'R13 acceptance no longer proves the built server JAR is an ordinary file before launch.'
Assert-True ($r13BuildServerInvokeIndex -gt $r13ProfileReadyIndex) 'R13 acceptance builds the server before isolated profile readiness.'
Assert-True ($r13ServerBuildReadyIndex -gt $r13BuildServerInvokeIndex) 'R13 acceptance server-build readiness marker precedes the canonical build invocation.'
Assert-True ($r13RunAllInvokeIndex -gt $r13ServerBuildReadyIndex) 'R13 acceptance launches LocalLab before the canonical server build is proven ready.'

foreach ($entry in @(
    @('function Get-Sha256Hex', 1),
    @('[IO.File]::Open(', 3),
    @('[IO.FileShare]::Read', 2),
    @('[IO.FileShare]::None', 1),
    @('[IO.FileMode]::CreateNew', 1),
    @("'--client-jar', `$r13SnapshotClient", 1),
    @("'--client-jar', `$clientJar", 0),
    @('$r13ProfileFailure = $_', 1),
    @('R13ClientSnapshotCleanupFailure', 1),
    @('Remove-Item -LiteralPath $r13SnapshotRoot -Recurse -Force -ErrorAction Stop', 1)
)) {
    Assert-ExactTextCount $r13Acceptance $entry[0] ([int]$entry[1]) 'R13 exact-v308 profile input snapshot contract drift.'
}
Assert-True ($r13Acceptance -match '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6') 'R13 acceptance no longer pins exact v308 source SHA.'
Assert-True ($r13Acceptance -match 'R13 exact-v308 input snapshot cleanup failed') 'R13 acceptance does not surface cleanup-only failure.'

$r13ProfileBuildIndex = $r13Acceptance.IndexOf("Write-Host '=== R13 isolated profile build ==='")
$r13SnapshotRootIndex = $r13Acceptance.IndexOf('$r13SnapshotRoot = Join-Path', $r13ProfileBuildIndex)
$r13SourceOpenIndex = $r13Acceptance.IndexOf('[IO.File]::Open(', $r13SnapshotRootIndex)
$r13SourceHashIndex = $r13Acceptance.IndexOf('$r13SourceSha = Get-Sha256Hex -Stream $r13SourceGuard', $r13SourceOpenIndex)
$r13WriterOpenIndex = $r13Acceptance.IndexOf('[IO.File]::Open(', $r13SourceOpenIndex + 1)
$r13CopyIndex = $r13Acceptance.IndexOf('$r13SourceGuard.CopyTo($r13SnapshotWriter)', $r13WriterOpenIndex)
$r13PrivateOpenIndex = $r13Acceptance.IndexOf('[IO.File]::Open(', $r13WriterOpenIndex + 1)
$r13PrivateHashIndex = $r13Acceptance.IndexOf('$r13PrivateSha = Get-Sha256Hex -Stream $r13PrivateGuard', $r13PrivateOpenIndex)
$r13SourceReleaseIndex = $r13Acceptance.IndexOf('$r13SourceGuard.Dispose()', $r13PrivateHashIndex)
$r13PrivateArgIndex = $r13Acceptance.IndexOf("'--client-jar', `$r13SnapshotClient", $r13SourceReleaseIndex)
$r13PythonInvokeIndex = $r13Acceptance.IndexOf('& $python.Source @profileArgs', $r13PrivateArgIndex)
$r13PrivateReleaseIndex = $r13Acceptance.LastIndexOf('$r13PrivateGuard.Dispose()')
$r13SnapshotCleanupIndex = $r13Acceptance.LastIndexOf('Remove-Item -LiteralPath $r13SnapshotRoot -Recurse -Force -ErrorAction Stop')
$r13FailureReplayIndex = $r13Acceptance.IndexOf('throw $r13ProfileFailure')
$r13ManifestIndex = $r13Acceptance.IndexOf("`$manifest = Join-Path `$output 'R13_PROFILE_MANIFEST.json'")

Assert-True ($r13SnapshotRootIndex -gt $r13ProfileBuildIndex) 'R13 profile input snapshot is not invocation-owned by the profile build.'
Assert-True ($r13SourceOpenIndex -gt $r13SnapshotRootIndex) 'R13 acceptance opens canonical client before private snapshot ownership exists.'
Assert-True ($r13SourceHashIndex -gt $r13SourceOpenIndex) 'R13 acceptance does not hash the already-open canonical source identity.'
Assert-True ($r13WriterOpenIndex -gt $r13SourceHashIndex) 'R13 private writer opens before canonical SHA admission.'
Assert-True ($r13CopyIndex -gt $r13WriterOpenIndex) 'R13 private input copy precedes no-overwrite writer creation.'
Assert-True ($r13PrivateOpenIndex -gt $r13CopyIndex) 'R13 private guard is acquired before snapshot copy completes.'
Assert-True ($r13PrivateHashIndex -gt $r13PrivateOpenIndex) 'R13 acceptance does not independently hash the guarded private snapshot.'
Assert-True ($r13SourceReleaseIndex -gt $r13PrivateHashIndex) 'R13 canonical source guard is released before private identity equality is proven.'
Assert-True ($r13PrivateArgIndex -gt $r13SourceReleaseIndex) 'R13 profile builder arguments are formed before invocation-owned identity is proven.'
Assert-True ($r13PythonInvokeIndex -gt $r13PrivateArgIndex) 'R13 profile builder invocation precedes private snapshot argument selection.'
Assert-True ($r13PrivateReleaseIndex -gt $r13PythonInvokeIndex) 'R13 private read guard does not span the complete profile-builder process.'
Assert-True ($r13SnapshotCleanupIndex -gt $r13PrivateReleaseIndex) 'R13 private snapshot root is removed before the private guard is released.'
Assert-True ($r13FailureReplayIndex -gt $r13SnapshotCleanupIndex) 'R13 primary profile-builder failure may be replayed before private input cleanup completes.'
Assert-True ($r13ManifestIndex -gt $r13FailureReplayIndex) 'R13 manifest validation does not follow successful guarded profile-builder cleanup.'
Assert-True ($client -match [regex]::Escape('scripts\Check-ExternalRuntime.ps1')) 'Standalone airgap launcher does not verify current exact-v308 external-runtime authority.'
Assert-True ($client -match 'Missing LocalLab external-runtime verifier') 'Standalone airgap launcher does not fail closed when the runtime verifier is missing.'
Assert-True ($client -match [regex]::Escape('scripts\Select-LocalLabJava.ps1')) 'Standalone airgap launcher does not use canonical Java selector.'
Assert-True ($client -match 'Set-LocalLabJava') 'Standalone airgap launcher does not invoke Set-LocalLabJava.'
Assert-True ($client -match '&\s+\$java\.Path\s+@javaArgs\s+-jar\s+\$launchSnapshot') 'Standalone airgap launcher does not invoke selected Java with the guarded private snapshot.'
Assert-True ($client -notmatch '&\s+\$java\.Path\s+@javaArgs\s+-jar\s+\$jar') 'Standalone airgap launcher still executes the mutable canonical airgap JAR directly.'
Assert-True ($client -match '024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7') 'Standalone airgap launcher no longer independently pins exact airgap snapshot SHA-256.'
Assert-True ($client -match [regex]::Escape("`$launchSnapshot = Join-Path `$launchRoot 'client-airgap.jar'")) 'Standalone airgap private snapshot does not preserve semantic client-airgap.jar basename.'
Assert-True ($client -match [regex]::Escape('[IO.File]::Copy($jar, $launchSnapshot, $false)')) 'Standalone airgap launcher does not create a no-overwrite private snapshot.'
Assert-True ($client -match '\[IO\.File\]::Open\([\s\S]*\$launchSnapshot[\s\S]*\[IO\.FileAccess\]::Read[\s\S]*\[IO\.FileShare\]::Read') 'Standalone airgap launcher does not hold a read-only no-write/no-delete guard on the private snapshot.'
Assert-True ($client -match [regex]::Escape('Get-FileHash -InputStream $snapshotGuard -Algorithm SHA256')) 'Standalone airgap launcher does not hash the guarded snapshot identity.'
Assert-True ($client -match 'AIRGAP_CLIENT_LAUNCH_SNAPSHOT_VERIFIED') 'Standalone airgap launcher does not report guarded private-snapshot verification.'
Assert-True ($client -match [regex]::Escape('$primaryFailure = $_')) 'Standalone airgap launcher does not preserve the primary body failure.'
Assert-True ($client -match 'AIRGAP client launch failed; cleanup was also incomplete') 'Standalone airgap launcher does not preserve primary authority when cleanup also fails.'
Assert-True ($client -match [regex]::Escape('$snapshotGuard.Dispose()')) 'Standalone airgap launcher does not dispose snapshot guard during cleanup.'
Assert-True ($client -match [regex]::Escape('Remove-Item -LiteralPath $launchSnapshot -Force -ErrorAction Stop')) 'Standalone airgap launcher does not clean the exact snapshot leaf.'
Assert-True ($client -match [regex]::Escape('Remove-Item -LiteralPath $launchRoot -ErrorAction Stop')) 'Standalone airgap launcher does not non-recursively clean the invocation-owned snapshot directory.'
Assert-True ($client -notmatch 'Remove-Item\s+-LiteralPath\s+\$launchRoot[^\r\n]*-Recurse') 'Standalone airgap launcher reintroduced recursive snapshot-directory cleanup.'
Assert-True ($client -match 'SPAWNPK_LOCALLAB_USER_HOME') 'Standalone airgap launcher does not expose the isolated LocalLab user.home environment contract.'
Assert-True ($client -match '-Duser\.home=\$resolvedHome') 'Standalone airgap launcher does not pass the isolated home to Java.'
Assert-True ($client -match 'LOCAL_LAB_CLIENT_HOME_ISOLATED') 'Standalone airgap launcher does not report isolated client-home authority.'
Assert-True ($client -match '\$cacheRoot\s*=\s*Join-Path\s+\$resolvedHome\s+''\.spawnpk''') 'Standalone airgap launcher does not derive the exact v308 cache root from isolated user.home.'
Assert-True ($client -match '\$dataRoot\s*=\s*Join-Path\s+\$resolvedHome\s+''\.spawnpk-data''') 'Standalone airgap launcher does not derive the exact v308 data root from isolated user.home.'
Assert-True ($client -match 'Refusing LocalLab isolated user\.home because it resolves to the real OS user home') 'Standalone airgap launcher does not reject the real OS user home.'
Assert-True ($client -match 'missing cache root') 'Standalone airgap launcher does not fail closed on an unseeded isolated cache root.'
Assert-True ($client -match 'LOCAL_LAB_CLIENT_HOME_DEFAULT') 'Standalone airgap launcher no longer preserves the ordinary non-isolated launch path.'
Assert-True ($clientWrapper -match 'LocalLabUserHome') 'Canonical airgap wrapper does not forward isolated client-home authority.'
Assert-True ($client -match '\$hadCallerJavaHome\s*=\s*Test-Path Env:JAVA_HOME') 'Standalone airgap launcher does not record whether caller JAVA_HOME existed.'
Assert-True ($client -match '\$callerJavaHome\s*=\s*\$env:JAVA_HOME') 'Standalone airgap launcher does not snapshot caller JAVA_HOME.'
Assert-True ($client -match '\$callerPath\s*=\s*\$env:Path') 'Standalone airgap launcher does not snapshot caller PATH.'
Assert-True ($client -match 'finally\s*\{[\s\S]*\$env:Path\s*=\s*\$callerPath') 'Standalone airgap launcher does not restore caller PATH in finally.'
Assert-True ($client -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Standalone airgap launcher does not restore an originally absent JAVA_HOME.'
Assert-True ($client -notmatch '(?m)^Set-Location \$PSScriptRoot\s*$') 'Standalone airgap launcher retains an unowned top-level Set-Location.'
Assert-True ($client -match '\$callerLocationPushed\s*=\s*\$false') 'Standalone airgap launcher does not initialize caller-location ownership.'
Assert-True ($client -match 'Push-Location -LiteralPath \$PSScriptRoot') 'Standalone airgap launcher does not push repository working directory.'
Assert-True ($client -match '\$callerLocationPushed\s*=\s*\$true') 'Standalone airgap launcher does not record successful location push.'
Assert-True ($client -match 'if \(\$callerLocationPushed\)\s*\{\s*try\s*\{\s*Pop-Location') 'Standalone airgap launcher does not guarantee caller-location restoration through guarded cleanup.'
Assert-True ($clientWrapper -match '\$hadCallerJavaHome\s*=\s*Test-Path Env:JAVA_HOME') 'Canonical airgap wrapper does not record caller JAVA_HOME ownership.'
Assert-True ($clientWrapper -match 'finally\s*\{[\s\S]*\$env:Path\s*=\s*\$callerPath') 'Canonical airgap wrapper does not restore caller PATH in finally.'
Assert-True ($clientWrapper -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Canonical airgap wrapper does not restore an originally absent JAVA_HOME.'

foreach ($entry in @(
    @('$hadCallerJavaHome = Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome = $env:JAVA_HOME', 1),
    @('$callerPath = $env:Path', 1),
    @('$env:Path = $callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1),
    @('$callerLocationPushed = $false', 1),
    @('Push-Location -LiteralPath $PSScriptRoot', 1),
    @('$callerLocationPushed = $true', 1),
    @('Pop-Location', 1),
    @("`$launchSnapshot = Join-Path `$launchRoot 'client-airgap.jar'", 1),
    @('[IO.File]::Copy($jar, $launchSnapshot, $false)', 1),
    @('[IO.FileShare]::Read', 1),
    @('Get-FileHash -InputStream $snapshotGuard -Algorithm SHA256', 1),
    @('AIRGAP_CLIENT_LAUNCH_SNAPSHOT_VERIFIED', 1),
    @('& $java.Path @javaArgs -jar $launchSnapshot', 1),
    @('$snapshotGuard.Dispose()', 1),
    @('Remove-Item -LiteralPath $launchSnapshot -Force -ErrorAction Stop', 1),
    @('Remove-Item -LiteralPath $launchRoot -ErrorAction Stop', 1),
    @('$primaryFailure = $_', 1)
)) {
    Assert-ExactTextCount $client $entry[0] ([int]$entry[1]) 'Standalone airgap Java/snapshot ownership count drift.'
}

foreach ($entry in @(
    @('$hadCallerJavaHome = Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome = $env:JAVA_HOME', 1),
    @('$callerPath = $env:Path', 1),
    @('$env:Path = $callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1)
)) {
    Assert-ExactTextCount $clientWrapper $entry[0] ([int]$entry[1]) 'Canonical airgap wrapper Java-environment ownership count drift.'
}

$standaloneRuntimeCheckIndex = $client.IndexOf('& $runtimeCheck')
$standaloneJarIndex = $client.IndexOf('$jar = Join-Path $PSScriptRoot ''local-client\client-airgap.jar''')
$standaloneSnapshotRootIndex = $client.IndexOf("'SpawnPK-airgap-' + [Guid]::NewGuid().ToString('N')")
$standaloneSnapshotLeafIndex = $client.IndexOf("`$launchSnapshot = Join-Path `$launchRoot 'client-airgap.jar'")
$standaloneGuardIndex = $client.IndexOf('$snapshotGuard = [IO.File]::Open(')
$standaloneHashIndex = $client.IndexOf('Get-FileHash -InputStream $snapshotGuard -Algorithm SHA256')
$standaloneLaunchIndex = $client.IndexOf('& $java.Path @javaArgs -jar $launchSnapshot')
$standaloneGuardDisposeIndex = $client.IndexOf('$snapshotGuard.Dispose()')
$standaloneLeafCleanupIndex = $client.IndexOf('Remove-Item -LiteralPath $launchSnapshot -Force -ErrorAction Stop')
$standaloneDirectoryCleanupIndex = $client.IndexOf('Remove-Item -LiteralPath $launchRoot -ErrorAction Stop')
Assert-True ($standaloneRuntimeCheckIndex -ge 0) 'Standalone airgap external-runtime verification invocation not found.'
Assert-True ($standaloneJarIndex -gt $standaloneRuntimeCheckIndex) 'Standalone airgap canonical client path is admitted before coherent runtime verification.'
Assert-True ($standaloneSnapshotRootIndex -gt $standaloneJarIndex) 'Standalone airgap snapshot root is created before coherent runtime verification completes.'
Assert-True ($standaloneSnapshotLeafIndex -gt $standaloneSnapshotRootIndex) 'Standalone airgap semantic snapshot leaf is not inside the unique invocation root.'
Assert-True ($standaloneGuardIndex -gt $standaloneSnapshotLeafIndex) 'Standalone airgap snapshot guard is acquired before the snapshot leaf exists.'
Assert-True ($standaloneHashIndex -gt $standaloneGuardIndex) 'Standalone airgap private bytes are hashed before the no-write/no-delete guard is acquired.'
Assert-True ($standaloneLaunchIndex -gt $standaloneHashIndex) 'Standalone airgap Java launch occurs before guarded private snapshot hashing.'
Assert-True ($standaloneGuardDisposeIndex -gt $standaloneLaunchIndex) 'Standalone airgap snapshot guard is disposed before synchronous Java completion.'
Assert-True ($standaloneLeafCleanupIndex -gt $standaloneGuardDisposeIndex) 'Standalone airgap snapshot leaf cleanup occurs before guard disposal.'
Assert-True ($standaloneDirectoryCleanupIndex -gt $standaloneLeafCleanupIndex) 'Standalone airgap invocation directory cleanup occurs before snapshot leaf cleanup.'
$standaloneEnvCaptureIndex = $client.IndexOf('$callerPath = $env:Path')
$standaloneSelectorCallIndex = $client.IndexOf('$java = Set-LocalLabJava')
$standaloneEnvRestoreIndex = $client.LastIndexOf('$env:Path = $callerPath')
Assert-True ($standaloneEnvCaptureIndex -ge 0 -and $standaloneEnvCaptureIndex -lt $standaloneSelectorCallIndex) 'Standalone airgap caller environment is not captured before Java selection.'
Assert-True ($standaloneEnvRestoreIndex -gt $standaloneLaunchIndex) 'Standalone airgap caller environment is restored before the synchronous Java client finishes.'
$standaloneLocationPushIndex = $client.IndexOf('Push-Location -LiteralPath $PSScriptRoot')
$standaloneLocationPopIndex = $client.LastIndexOf('Pop-Location')
Assert-True ($standaloneLocationPushIndex -ge 0 -and $standaloneLocationPushIndex -lt $standaloneRuntimeCheckIndex) 'Standalone airgap repository location is not established before launcher work.'
Assert-True ($standaloneLocationPopIndex -gt $standaloneLaunchIndex) 'Standalone airgap caller location is restored before synchronous client completion.'
Assert-True ($quick -match 'client-airgap\\\.jar') 'Quick launcher process identity no longer recognizes semantic client-airgap.jar snapshot basename.'
Assert-True ($all -match 'client-airgap\\\.jar') 'Multi-client launcher process identity no longer recognizes semantic client-airgap.jar snapshot basename.'
Assert-True ($r13Acceptance -match 'client-airgap\\\.jar') 'R13 live-profile ownership no longer recognizes semantic client-airgap.jar snapshot basename.'

Assert-True ($secondClient -match '\[switch\]\$AllowNonAirgap') 'Second-client launcher does not require the explicit -AllowNonAirgap switch.'
Assert-True ($secondClient -match [regex]::Escape('scripts\Run-Client-Airgap.ps1')) 'Second-client default does not target the canonical airgap wrapper.'
Assert-True ($secondClient -match [regex]::Escape('RUN_CLIENT_LOCALHOST_NONAIRGAP.ps1')) 'Second-client explicit diagnostic path is missing.'
Assert-True ($secondClient -match [regex]::Escape('scripts\Check-ExternalRuntime.ps1')) 'Second-client launcher does not preflight the exact-v308 external runtime.'
Assert-True ($secondClient -match [regex]::Escape('$ports = 43594, 43595')) 'Second-client launcher does not require both game and AUX listener ports.'
Assert-True ($secondClient -match [regex]::Escape('$portNumbers -notcontains 43594')) 'Second-client launcher does not gate on game-port readiness.'
Assert-True ($secondClient -match [regex]::Escape('$portNumbers -notcontains 43595')) 'Second-client launcher does not gate on AUX-port readiness.'
Assert-True ($secondClient -match [regex]::Escape('$ownerPids.Count -ne 1')) 'Second-client launcher does not require one PID to own both LocalLab ports.'
Assert-True ($secondClient -match 'Get-CimInstance\s+Win32_Process') 'Second-client launcher does not verify the dual-port owner process.'
Assert-True ($secondClient -match 'SpawnPKLocalServer\|spk\\\.local\\\.Main\|SpawnPK-LocalLab') 'Second-client launcher does not require an expected SpawnPK LocalLab server owner.'
Assert-True ($secondClient -match 'SECOND_CLIENT_AIRGAP_DEFAULT') 'Second-client launcher does not report the canonical airgap default.'
Assert-True ($secondClient -match 'SECOND_CLIENT_NONAIRGAP_EXPLICIT') 'Second-client launcher does not visibly mark explicit nonairgap mode.'
Assert-True ($secondClient -notmatch 'foreach\s*\(\$candidate\s+in') 'Second-client launcher reintroduced first-existing-launcher fallback selection.'
Assert-True ($secondClient -match '&\s+\$launcher\s+-AllowExternalEndpoints') 'Parent nonairgap opt-in does not pass the required child external-endpoint opt-in.'
Assert-True ($secondClient -notmatch 'client-localhost\.jar') 'Second-client parent should inherit localhost snapshot authority by delegation, not duplicate client-localhost.jar byte handling.'

$secondDefaultIndex = $secondClient.IndexOf('$launcher = $airgapLauncher')
$secondOptInIndex = $secondClient.IndexOf('if ($AllowNonAirgap)')
$secondNonAirgapAssignIndex = $secondClient.IndexOf('$launcher = $nonAirgapLauncher')
$secondChildOptInIndex = $secondClient.IndexOf('& $launcher -AllowExternalEndpoints')
Assert-True ($secondDefaultIndex -ge 0) 'Second-client launcher has no explicit airgap default assignment.'
Assert-True ($secondOptInIndex -gt $secondDefaultIndex) 'Second-client nonairgap opt-in is not applied after the airgap default.'
Assert-True ($secondNonAirgapAssignIndex -gt $secondOptInIndex) 'Second-client nonairgap launcher is not confined to the explicit opt-in branch.'
Assert-True ($secondChildOptInIndex -gt $secondOptInIndex) 'Second-client parent does not pass child nonairgap consent only after explicit opt-in.'
Assert-True ($secondClient -notmatch 'Set-LocalLabJava') 'Second-client wrapper should not add an independent Java-selector mutation.'
Assert-True ($clientWrapper -match '\$env:Path\s*=\s*\$callerPath') 'Second-client airgap target does not restore caller Java PATH after inline execution.'
Assert-True ($nonAirgap -match '\$env:Path\s*=\s*\$callerPath') 'Second-client nonairgap target does not restore caller Java PATH after inline execution.'

Assert-True ($nonAirgap -match '\[switch\]\$AllowExternalEndpoints') 'Direct nonairgap launcher does not require explicit -AllowExternalEndpoints consent.'
Assert-True ($nonAirgap -match 'if\s*\(\s*-not\s+\$AllowExternalEndpoints\s*\)') 'Direct nonairgap launcher does not fail closed without external-endpoint consent.'
Assert-True ($nonAirgap -match [regex]::Escape('scripts\Select-LocalLabJava.ps1')) 'Explicit nonairgap launcher does not use the canonical Java selector.'
Assert-True ($nonAirgap -match [regex]::Escape('scripts\Check-ExternalRuntime.ps1')) 'Explicit nonairgap launcher does not verify current external-runtime authority.'
Assert-True ($nonAirgap -match 'Set-LocalLabJava') 'Explicit nonairgap launcher does not invoke canonical Java selection.'
Assert-True ($nonAirgap -match '&\s+\$java\.Path\s+-jar\s+\$launchSnapshot') 'Explicit nonairgap launcher does not invoke selected Java with the guarded private snapshot.'
Assert-True ($nonAirgap -notmatch '&\s+\$java\.Path\s+-jar\s+\$jar') 'Explicit nonairgap launcher still executes the mutable canonical localhost JAR directly.'
Assert-True ($nonAirgap -match '15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e') 'Explicit nonairgap launcher no longer independently pins exact localhost snapshot SHA-256.'
Assert-True ($nonAirgap -match [regex]::Escape("`$launchSnapshot = Join-Path `$launchRoot 'client-localhost.jar'")) 'Explicit nonairgap private snapshot does not preserve semantic client-localhost.jar basename.'
Assert-True ($nonAirgap -match [regex]::Escape('[IO.File]::Copy($jar, $launchSnapshot, $false)')) 'Explicit nonairgap launcher does not create a no-overwrite private snapshot.'
Assert-True ($nonAirgap -match '\[IO\.File\]::Open\([\s\S]*\$launchSnapshot[\s\S]*\[IO\.FileAccess\]::Read[\s\S]*\[IO\.FileShare\]::Read') 'Explicit nonairgap launcher does not hold a read-only no-write/no-delete guard on the private snapshot.'
Assert-True ($nonAirgap -match [regex]::Escape('Get-FileHash -InputStream $snapshotGuard -Algorithm SHA256')) 'Explicit nonairgap launcher does not hash the guarded snapshot identity.'
Assert-True ($nonAirgap -match 'NONAIRGAP_CLIENT_LAUNCH_SNAPSHOT_VERIFIED') 'Explicit nonairgap launcher does not report guarded private-snapshot verification.'
Assert-True ($nonAirgap -match [regex]::Escape('$primaryFailure = $_')) 'Explicit nonairgap launcher does not preserve the primary body failure.'
Assert-True ($nonAirgap -match 'NONAIRGAP diagnostic client launch failed; cleanup was also incomplete') 'Explicit nonairgap launcher does not preserve primary authority when cleanup also fails.'
Assert-True ($nonAirgap -match [regex]::Escape('$snapshotGuard.Dispose()')) 'Explicit nonairgap launcher does not dispose snapshot guard during cleanup.'
Assert-True ($nonAirgap -match [regex]::Escape('Remove-Item -LiteralPath $launchSnapshot -Force -ErrorAction Stop')) 'Explicit nonairgap launcher does not clean the exact snapshot leaf.'
Assert-True ($nonAirgap -match [regex]::Escape('Remove-Item -LiteralPath $launchRoot -ErrorAction Stop')) 'Explicit nonairgap launcher does not non-recursively clean the invocation-owned snapshot directory.'
Assert-True ($nonAirgap -notmatch 'Remove-Item\s+-LiteralPath\s+\$launchRoot[^\r\n]*-Recurse') 'Explicit nonairgap launcher reintroduced recursive snapshot-directory cleanup.'
Assert-True ($nonAirgap -match '\$clientExit\s*=\s*\$LASTEXITCODE') 'Explicit nonairgap launcher does not capture the native client exit code.'
Assert-True ($nonAirgap -match 'if\s*\(\s*\$clientExit\s+-ne\s+0\s*\)') 'Explicit nonairgap launcher does not fail on a captured nonzero client exit.'
Assert-True ($nonAirgap -match 'NONAIRGAP_DIAGNOSTIC_EXPLICIT') 'Explicit nonairgap launcher lost its external-endpoint warning marker.'
Assert-True ($nonAirgap -notmatch '&\s+java\s+-jar') 'Explicit nonairgap launcher reintroduced bare PATH Java execution.'
Assert-True ($nonAirgap -match '\$hadCallerJavaHome\s*=\s*Test-Path Env:JAVA_HOME') 'Explicit nonairgap launcher does not record caller JAVA_HOME ownership.'
Assert-True ($nonAirgap -match '\$callerJavaHome\s*=\s*\$env:JAVA_HOME') 'Explicit nonairgap launcher does not snapshot caller JAVA_HOME.'
Assert-True ($nonAirgap -match '\$callerPath\s*=\s*\$env:Path') 'Explicit nonairgap launcher does not snapshot caller PATH.'
Assert-True ($nonAirgap -match 'finally\s*\{[\s\S]*\$env:Path\s*=\s*\$callerPath') 'Explicit nonairgap launcher does not restore caller PATH in finally.'
Assert-True ($nonAirgap -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Explicit nonairgap launcher does not restore an originally absent JAVA_HOME.'
Assert-True ($nonAirgap -notmatch '(?m)^Set-Location \$PSScriptRoot\s*$') 'Explicit nonairgap launcher retains an unowned top-level Set-Location.'
Assert-True ($nonAirgap -match '\$callerLocationPushed\s*=\s*\$false') 'Explicit nonairgap launcher does not initialize caller-location ownership.'
Assert-True ($nonAirgap -match 'Push-Location -LiteralPath \$PSScriptRoot') 'Explicit nonairgap launcher does not push repository working directory.'
Assert-True ($nonAirgap -match '\$callerLocationPushed\s*=\s*\$true') 'Explicit nonairgap launcher does not record successful location push.'
Assert-True ($nonAirgap -match 'if \(\$callerLocationPushed\)\s*\{\s*try\s*\{\s*Pop-Location') 'Explicit nonairgap launcher does not guarantee caller-location restoration through guarded cleanup.'

foreach ($entry in @(
    @('$hadCallerJavaHome = Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome = $env:JAVA_HOME', 1),
    @('$callerPath = $env:Path', 1),
    @('$env:Path = $callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1),
    @('$callerLocationPushed = $false', 1),
    @('Push-Location -LiteralPath $PSScriptRoot', 1),
    @('$callerLocationPushed = $true', 1),
    @('Pop-Location', 1),
    @("`$launchSnapshot = Join-Path `$launchRoot 'client-localhost.jar'", 1),
    @('[IO.File]::Copy($jar, $launchSnapshot, $false)', 1),
    @('[IO.FileShare]::Read', 1),
    @('Get-FileHash -InputStream $snapshotGuard -Algorithm SHA256', 1),
    @('NONAIRGAP_CLIENT_LAUNCH_SNAPSHOT_VERIFIED', 1),
    @('& $java.Path -jar $launchSnapshot', 1),
    @('$clientExit = $LASTEXITCODE', 1),
    @('if ($clientExit -ne 0)', 1),
    @('$snapshotGuard.Dispose()', 1),
    @('Remove-Item -LiteralPath $launchSnapshot -Force -ErrorAction Stop', 1),
    @('Remove-Item -LiteralPath $launchRoot -ErrorAction Stop', 1),
    @('$primaryFailure = $_', 1)
)) {
    Assert-ExactTextCount $nonAirgap $entry[0] ([int]$entry[1]) 'Nonairgap Java/snapshot ownership count drift.'
}

$nonAirgapConsentIndex = $nonAirgap.IndexOf('if (-not $AllowExternalEndpoints)')
$nonAirgapSelectorIndex = $nonAirgap.IndexOf('$selector = Join-Path')
$nonAirgapRuntimeCheckIndex = $nonAirgap.IndexOf('& $runtimeCheck')
$nonAirgapSnapshotRootIndex = $nonAirgap.IndexOf("'SpawnPK-localhost-' + [Guid]::NewGuid().ToString('N')")
$nonAirgapSnapshotLeafIndex = $nonAirgap.IndexOf("`$launchSnapshot = Join-Path `$launchRoot 'client-localhost.jar'")
$nonAirgapGuardIndex = $nonAirgap.IndexOf('$snapshotGuard = [IO.File]::Open(')
$nonAirgapHashIndex = $nonAirgap.IndexOf('Get-FileHash -InputStream $snapshotGuard -Algorithm SHA256')
$nonAirgapLaunchIndex = $nonAirgap.IndexOf('& $java.Path -jar $launchSnapshot')
$nonAirgapGuardDisposeIndex = $nonAirgap.IndexOf('$snapshotGuard.Dispose()')
$nonAirgapLeafCleanupIndex = $nonAirgap.IndexOf('Remove-Item -LiteralPath $launchSnapshot -Force -ErrorAction Stop')
$nonAirgapDirectoryCleanupIndex = $nonAirgap.IndexOf('Remove-Item -LiteralPath $launchRoot -ErrorAction Stop')
Assert-True ($nonAirgapConsentIndex -ge 0) 'Direct nonairgap consent gate not found.'
Assert-True ($nonAirgapSelectorIndex -gt $nonAirgapConsentIndex) 'Direct nonairgap launcher performs setup before explicit consent.'
Assert-True ($nonAirgapRuntimeCheckIndex -gt $nonAirgapConsentIndex) 'Direct nonairgap coherent-runtime verification is not inside the explicitly consented flow.'
Assert-True ($nonAirgapSnapshotRootIndex -gt $nonAirgapRuntimeCheckIndex) 'Direct nonairgap snapshot is created before coherent runtime verification.'
Assert-True ($nonAirgapSnapshotLeafIndex -gt $nonAirgapSnapshotRootIndex) 'Direct nonairgap semantic snapshot leaf is not inside the unique invocation root.'
Assert-True ($nonAirgapGuardIndex -gt $nonAirgapSnapshotLeafIndex) 'Direct nonairgap snapshot guard is acquired before the snapshot leaf exists.'
Assert-True ($nonAirgapHashIndex -gt $nonAirgapGuardIndex) 'Direct nonairgap private bytes are hashed before the no-write/no-delete guard is acquired.'
Assert-True ($nonAirgapLaunchIndex -gt $nonAirgapHashIndex) 'Direct nonairgap Java launch occurs before guarded private snapshot hashing.'
Assert-True ($nonAirgapGuardDisposeIndex -gt $nonAirgapLaunchIndex) 'Direct nonairgap snapshot guard is disposed before synchronous Java completion.'
Assert-True ($nonAirgapLeafCleanupIndex -gt $nonAirgapGuardDisposeIndex) 'Direct nonairgap snapshot leaf cleanup occurs before guard disposal.'
Assert-True ($nonAirgapDirectoryCleanupIndex -gt $nonAirgapLeafCleanupIndex) 'Direct nonairgap invocation directory cleanup occurs before snapshot leaf cleanup.'
$nonAirgapEnvCaptureIndex = $nonAirgap.IndexOf('$callerPath = $env:Path')
$nonAirgapJavaIndex = $nonAirgap.IndexOf('$java = Set-LocalLabJava')
$nonAirgapEnvRestoreIndex = $nonAirgap.LastIndexOf('$env:Path = $callerPath')
Assert-True ($nonAirgapEnvCaptureIndex -gt $nonAirgapSelectorIndex -and $nonAirgapEnvCaptureIndex -lt $nonAirgapJavaIndex) 'Direct nonairgap caller environment is not captured before Java selection.'
Assert-True ($nonAirgapEnvRestoreIndex -gt $nonAirgapLaunchIndex) 'Direct nonairgap caller environment is restored before the synchronous Java client finishes.'
$nonAirgapLocationPushIndex = $nonAirgap.IndexOf('Push-Location -LiteralPath $PSScriptRoot')
$nonAirgapLocationPopIndex = $nonAirgap.LastIndexOf('Pop-Location')
Assert-True ($nonAirgapLocationPushIndex -gt $nonAirgapConsentIndex -and $nonAirgapLocationPushIndex -lt $nonAirgapJavaIndex) 'Direct nonairgap repository location is not established inside the consented owned flow.'
Assert-True ($nonAirgapLocationPopIndex -gt $nonAirgapLaunchIndex) 'Direct nonairgap caller location is restored before synchronous client completion.'

Assert-True ($quick -match [regex]::Escape('scripts\Check-ExternalRuntime.ps1')) 'Quick-start launcher does not use current external-runtime preflight.'
Assert-True ($quick -match [regex]::Escape('server\build\SpawnPKLocalServer.jar')) 'Quick-start launcher does not preflight the current built server JAR.'
Assert-True ($quick -match [regex]::Escape('scripts\Run-Server.ps1')) 'Quick-start launcher does not use the canonical server wrapper.'
Assert-True ($quick -match [regex]::Escape('scripts\Run-Client-Airgap.ps1')) 'Quick-start launcher does not use the canonical airgap client wrapper.'
Assert-True ($quick -match [regex]::Escape('$ports = 43594, 43595')) 'Quick-start launcher does not track both game and AUX ports.'
Assert-True ($quick -match [regex]::Escape('$readyPorts -contains 43594')) 'Quick-start launcher does not require game-port readiness.'
Assert-True ($quick -match [regex]::Escape('$readyPorts -contains 43595')) 'Quick-start launcher does not require AUX-port readiness.'
Assert-True ($quick -match [regex]::Escape('$readyOwnerPids.Count -gt 1')) 'Quick-start launcher does not fail closed on multiple listener owners.'
Assert-True ($quick -match 'Get-CimInstance\s+Win32_Process') 'Quick-start launcher does not inspect process identity.'
Assert-True ($quick -match 'SpawnPKLocalServer\|spk\\\.local\\\.Main\|SpawnPK-LocalLab') 'Quick-start launcher does not require expected SpawnPK LocalLab server identity.'
Assert-True ($quick -match 'function\s+Get-LauncherOwnedProcessIds') 'Quick-start launcher lacks deterministic descendant ownership resolution.'
Assert-True ($quick -match 'ParentProcessId') 'Quick-start launcher does not derive owned descendants from Windows parent-process identity.'
Assert-True ($quick -match '\[switch\]\$IncludeExitedRoots') 'Quick-start ownership helper cannot preserve proven ancestry after a child root exits.'
Assert-True ($quick -match '\$recordedRoots') 'Quick-start ownership helper does not retain recorded root process authority.'
Assert-True ($quick -match '\$liveRoots') 'Quick-start ownership helper no longer distinguishes live roots from recorded roots.'
Assert-True ($quick -match '\$exitedRoots') 'Quick-start ownership helper does not identify exited recorded roots.'
Assert-True ($quick -match '\$rootStart\s*=\s*\[DateTime\]\$exitedRoot\.StartTime') 'Quick-start cleanup does not bind exited-root ancestry to recorded root start time.'
Assert-True ($quick -match '\$rootExit\s*=\s*\[DateTime\]\$exitedRoot\.ExitTime') 'Quick-start cleanup does not bind exited-root ancestry to recorded root exit time.'
Assert-True ($quick -match '\$childCreated\s*=\s*\[DateTime\]\$directChild\.CreationDate') 'Quick-start cleanup does not inspect direct-child creation time.'
Assert-True ($quick -match '\$childCreated -lt \$rootStart -or\s+\$childCreated -ge \$rootExit') 'Quick-start cleanup does not require the first exited-root edge to exist within the recorded root lifetime.'
Assert-True ($quick -match 'cannot prove exited-root lifetime') 'Quick-start cleanup does not fail closed when recorded root lifetime cannot be read.'
Assert-True ($quick -match 'cannot prove creation time for exited-root child') 'Quick-start cleanup does not fail closed when child creation time cannot be read.'
Assert-True ($quick -match 'refused ambiguous exited-root PID reuse') 'Quick-start cleanup does not fail closed when an exited recorded PID is live again.'
Assert-True ($quick -match 'Get-LauncherOwnedProcessRecords -Roots \$Roots -Label \$Label -IncludeExitedRoots') 'Quick-start failure cleanup does not opt into lifetime-bound exited-root traversal.'
Assert-True ($quick -match '\$candidatePid\s+-notin\s+\$serverOwnedPids') 'Quick-start launcher does not bind ready server PID to the recorded server window.'
Assert-True ($quick -match '\$_\.ProcessId\s+-in\s+\$clientOwnedPids') 'Quick-start launcher does not bind airgap Java discovery to the recorded client window.'
Assert-True ($quick -match '\$stableClient\.ProcessId\s+-notin\s+\$stableClientOwnedPids') 'Quick-start client stability does not retain recorded client-window ancestry.'
Assert-True ($quick -match 'QUICKSTART_LOCAL_LAB_HEALTHY') 'Quick-start launcher lacks an explicit final healthy commit marker.'
Assert-True ($quick -match 'Throw-LauncherFailureWithCleanup') 'Quick-start launcher does not surface combined launch/cleanup failure.'
Assert-True ($quick -match 'cleanup was incomplete') 'Quick-start launcher does not report incomplete owned cleanup in the thrown result.'
Assert-True ($quick -notmatch 'Write-Warning.*owned-process cleanup') 'Quick-start launcher still downgrades owned cleanup failure to a warning.'
Assert-True ($quick -notmatch 'Stop-Process\s+-Name') 'Quick-start launcher reintroduced broad name-based process cleanup.'

foreach ($entry in @(
    @('function Get-NormalizedProcessLifetimeStamp', 1),
    @('function Get-LauncherOwnedProcessRecords', 1),
    @('function Get-LauncherOwnedProcessIds', 1),
    @('function Stop-LauncherOwnedProcessTree', 1),
    @('function Throw-LauncherFailureWithCleanup', 1),
    @('[switch]$IncludeExitedRoots', 2),
    @('Get-LauncherOwnedProcessRecords -Roots $Roots -Label $Label -IncludeExitedRoots', 2),
    @('$recordedRoots = @(', 1),
    @('$recordedRootPids = @(', 1),
    @('$liveRoots = @(', 1),
    @('$exitedRoots = @(', 1),
    @('$rootStart = [DateTime]$exitedRoot.StartTime', 1),
    @('$rootExit = [DateTime]$exitedRoot.ExitTime', 1),
    @('$childCreated = [DateTime]$directChild.CreationDate', 1),
    @('refused ambiguous exited-root PID reuse', 1),
    @('$ready = $false', 1),
    @('$airgapClient = $null', 1),
    @('$ownedChildren = @()', 1),
    @('$serverWindow = Start-Process powershell.exe', 1),
    @('$clientWindow = Start-Process powershell.exe', 1),
    @('$ownedChildren += $serverWindow', 1),
    @('$ownedChildren += $clientWindow', 1),
    @('QUICKSTART_LOCAL_LAB_HEALTHY', 1),
    @('Stop-LauncherOwnedProcessTree -Roots $ownedChildren', 1)
)) {
    Assert-ExactTextCount $quick $entry[0] ([int]$entry[1]) 'Quick-start structural anchor count drift.'
}

$quickServerJarIndex = $quick.IndexOf('$serverJar = Join-Path')
$quickServerSpawnIndex = $quick.IndexOf('$serverWindow = Start-Process powershell.exe')
$quickDualReadyIndex = $quick.IndexOf('$readyPorts -contains 43595')
$quickClientSpawnIndex = $quick.IndexOf('$clientWindow = Start-Process powershell.exe')
$quickClientOwnedIndex = $quick.IndexOf('Get-LauncherOwnedProcessIds -Roots @($clientWindow)')
$quickHealthyIndex = $quick.IndexOf('QUICKSTART_LOCAL_LAB_HEALTHY')
$quickCleanupIndex = $quick.IndexOf('Stop-LauncherOwnedProcessTree -Roots $ownedChildren')
$quickCombinedThrowIndex = $quick.LastIndexOf('Throw-LauncherFailureWithCleanup -PrimaryFailure')
Assert-True ($quickServerJarIndex -ge 0 -and $quickServerJarIndex -lt $quickServerSpawnIndex) 'Quick-start server-JAR preflight is not established before child launch.'
Assert-True ($quickServerSpawnIndex -ge 0) 'Quick-start owned server launch not found.'
Assert-True ($quickDualReadyIndex -gt $quickServerSpawnIndex) 'Quick-start dual-port proof does not follow server launch.'
Assert-True ($quickClientSpawnIndex -gt $quickDualReadyIndex) 'Quick-start client starts before dual-port server readiness.'
Assert-True ($quickClientOwnedIndex -gt $quickClientSpawnIndex) 'Quick-start client ancestry is checked before its exact root is recorded.'
Assert-True ($quickHealthyIndex -gt $quickClientOwnedIndex) 'Quick-start healthy marker precedes owned client proof.'
Assert-True ($quickCleanupIndex -gt $quickHealthyIndex) 'Quick-start cleanup catch is not structurally after the healthy-path body.'
Assert-True ($quickCombinedThrowIndex -gt $quickCleanupIndex) 'Quick-start does not surface cleanup outcome after cleanup attempt.'
Assert-True ($externalRuntime -match '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6') 'External runtime no longer pins exact v308 evidence client.'
Assert-True ($externalRuntime -match '024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7') 'External runtime no longer pins exact v308 airgap client.'
Assert-True ($externalRuntime -match '15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e') 'External runtime no longer pins exact v308 localhost client.'
Assert-True ($externalRuntime -match 'coherentTriplet=true') 'External runtime does not report coherent v308 triplet authority.'
Assert-True ($runtimeImport -match '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6') 'Runtime importer no longer requires exact v308 evidence client.'
Assert-True ($runtimeImport -match '024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7') 'Runtime importer no longer requires exact v308 airgap client.'
Assert-True ($runtimeImport -match '15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e') 'Runtime importer no longer requires exact v308 localhost client.'

Assert-True ($runtimeImport -match 'EXTERNAL_RUNTIME_IMPORT_PREFLIGHT_PASS') 'Runtime importer does not prove the full source triplet before publication.'
Assert-True ($runtimeImport -match 'RUNTIME_IMPORT_STAGE_VERIFIED') 'Runtime importer does not verify transaction-owned staging.'
Assert-True ($runtimeImport -match 'RUNTIME_IMPORT_BACKUP_READY') 'Runtime importer does not preserve existing destination state before publication.'
Assert-True ($runtimeImport -match 'RUNTIME_IMPORT_ROLLBACK_COMPLETE') 'Runtime importer has no explicit rollback-completion path.'
Assert-True ($runtimeImport -match 'RUNTIME_IMPORT_FINAL_VERIFY_PASS') 'Runtime importer does not keep final whole-triplet verification inside transaction ownership.'
Assert-True ($runtimeImport -match 'atomicPublish=true') 'Runtime importer success marker does not state atomic publication ownership.'
Assert-True ($runtimeImport -match '\[IO\.Path\]::GetTempPath\(\)') 'Runtime importer does not stage outside the tracked repository.'
Assert-True ($runtimeImport -match '\$touched\.Add\(\$record\)') 'Runtime importer does not acquire rollback ownership before destination writes.'
Assert-True ($runtimeImport -match '\[array\]::Reverse\(\$rollbackTargets\)') 'Runtime importer does not restore touched destinations in reverse publication order.'
Assert-True ($runtimeImport -match 'Get-ExactSha256') 'Runtime importer lost exact SHA-256 verification helpers.'
Assert-True ($runtimeImport -match 'function Assert-NoReparsePathComponents') 'Runtime importer does not reject reparse-point destination ancestry.'
Assert-True ($runtimeImport -match 'function Assert-RegularDestinationOrMissing') 'Runtime importer does not reject non-file/reparse destination collisions.'
Assert-True ($runtimeImport -match 'function Assert-DestinationSnapshotStillOwned') 'Runtime importer does not revalidate exact destination ownership immediately before publication.'
Assert-True ($runtimeImport -match 'destination appeared after rollback snapshot') 'Runtime importer can overwrite a newly appeared destination outside transaction ownership.'
Assert-True ($runtimeImport -match 'destination changed after rollback snapshot') 'Runtime importer can overwrite an existing destination changed after backup ownership was frozen.'
Assert-True ($runtimeImport -match '\$destinationDirectories\s*=\s*@\(\)') 'Runtime importer does not own a unique destination-directory set.'
Assert-True ($runtimeImport -match 'RUNTIME_IMPORT_DESTINATION_DIRECTORIES_READY') 'Runtime importer does not prove destination-directory ownership before publication.'
Assert-True ($runtimeImport -match 'restored destination hash mismatch') 'Runtime importer does not verify restored destination bytes against rollback backup.'
Assert-True ($runtimeImport -match 'Sort-Object \{ \$_\.Path\.Length \} -Descending') 'Runtime importer does not clean transaction-created destination directories deepest-first.'
Assert-True ($runtimeImport -match 'Rollback-owned destination directory is not empty') 'Runtime importer does not fail closed instead of deleting unrelated destination-directory material.'
Assert-True ($runtimeImport -notmatch 'Remove-Item[^\r\n]*(evidence|local-client)[^\r\n]*-Recurse') 'Runtime importer reintroduced recursive deletion authority over canonical destination directories.'
Assert-True ($runtimeImport -match 'pathConfinement=true') 'Runtime importer success/preflight markers do not expose path-confinement authority.'
Assert-True ($runtimeImport -match 'function New-SameDirectoryLeafPath') 'Runtime importer lacks transaction-owned same-directory leaf allocation.'
Assert-True ($runtimeImport -match 'leaf collision') 'Runtime importer does not fail closed when a generated same-directory leaf path already exists.'
Assert-True ($runtimeImport -match 'function New-VerifiedSameDirectoryLeaf') 'Runtime importer lacks verified same-directory publication/restore leaves.'
Assert-True ($runtimeImport -match 'function Assert-VerifiedOwnedLeaf') 'Runtime importer lacks exact private-leaf ownership revalidation.'
Assert-True ($runtimeImport -match '\[IO\.FileMode\]::CreateNew') 'Runtime importer publication leaf is not create-new/no-overwrite.'
Assert-True ($runtimeImport -match '\$leafStream\.Flush\(\$true\)') 'Runtime importer does not durably flush a private leaf before hash verification.'
Assert-True ($runtimeImport -match '\$leafComplete\s*=\s*\$false') 'Runtime importer does not track private-leaf completion before exposing the leaf path.'
Assert-True ($runtimeImport -match '\$leafCreated\s*=\s*\$false') 'Runtime importer does not distinguish a transaction-created leaf from a create-new collision.'
Assert-True ($runtimeImport -match '\$leafCreated\s*=\s*\$true') 'Runtime importer never acquires explicit ownership after successful create-new leaf creation.'
Assert-True ($runtimeImport -match 'if \(\$leafCreated -and \(Test-Path -LiteralPath \$leaf\)\)') 'Runtime importer can delete a failed private-leaf path without proving this transaction created it.'
Assert-True ($runtimeImport -match 'if \(-not \$leafComplete\)') 'Runtime importer does not clean failed/partial create-new leaves before returning control.'
Assert-True ($runtimeImport -match '\[IO\.File\]::Replace\(') 'Runtime importer does not use an atomic replace primitive for existing canonical destinations.'
Assert-True ($runtimeImport -match '\[IO\.File\]::Move\(') 'Runtime importer does not use same-directory move for absent canonical destinations.'
Assert-True ($runtimeImport -match '\$record\.Committed\s*=\s*\$true') 'Runtime importer does not distinguish a completed discrete canonical transition.'
Assert-True ($runtimeImport -match '\$record\.PublishedSha256\s*=\s*\$record\.ExpectedSha256') 'Runtime importer does not retain exact transaction-published byte authority.'
Assert-True ($runtimeImport -match 'destination changed during atomic commit') 'Runtime importer does not verify the actual destination preimage displaced by File.Replace.'
Assert-True ($runtimeImport -match 'rollback ownership changed during atomic restore') 'Runtime importer does not compensate when rollback-time destination identity changes.'
Assert-True ($runtimeImport -notmatch 'Copy-Item -LiteralPath \$record\.Stage -Destination \$record\.Destination -Force') 'Runtime importer reintroduced streaming staged bytes directly into the canonical destination.'
Assert-True ($runtimeImport -notmatch 'Copy-Item -LiteralPath \$record\.Backup -Destination \$record\.Destination -Force') 'Runtime importer reintroduced streaming rollback bytes directly into the canonical destination.'

foreach ($entry in @(
    @('function Assert-NoReparsePathComponents', 1),
    @('function Assert-RegularDestinationOrMissing', 1),
    @('function Assert-DestinationSnapshotStillOwned', 1),
    @('destination appeared after rollback snapshot', 1),
    @('destination changed after rollback snapshot', 1),
    @('$destinationDirectories = @()', 1),
    @('RUNTIME_IMPORT_DESTINATION_DIRECTORIES_READY', 1),
    @('restored destination hash mismatch', 1),
    @('Rollback-owned destination directory is not empty', 1),
    @('function New-SameDirectoryLeafPath', 1),
    @('function New-VerifiedSameDirectoryLeaf', 1),
    @('function Assert-VerifiedOwnedLeaf', 1),
    @('function Remove-VerifiedOwnedLeaf', 1),
    @('[IO.FileMode]::CreateNew', 1),
    @('$leafStream.Flush($true)', 1),
    @('[IO.File]::Replace(', 4),
    @('[IO.File]::Move(', 3),
    @('$record.Committed = $true', 2),
    @('$record.PublishedSha256 = $record.ExpectedSha256', 2),
    @('destination changed during atomic commit', 1),
    @('rollback ownership changed during atomic restore', 1)
)) {
    Assert-ExactTextCount $runtimeImport $entry[0] ([int]$entry[1]) 'Runtime importer rollback/path-safety structural count drift.'
}


$runtimeImportPreflightIndex = $runtimeImport.IndexOf('EXTERNAL_RUNTIME_IMPORT_PREFLIGHT_PASS')
$runtimeImportStageIndex = $runtimeImport.IndexOf('RUNTIME_IMPORT_STAGE_VERIFIED')
$runtimeImportBackupIndex = $runtimeImport.IndexOf('RUNTIME_IMPORT_BACKUP_READY')
$runtimeImportDirectoryReadyIndex = $runtimeImport.IndexOf('RUNTIME_IMPORT_DESTINATION_DIRECTORIES_READY')
$runtimeImportLeafBuildIndex = $runtimeImport.IndexOf('$record.PublishLeaf =')
$runtimeImportSnapshotRecheckIndex = $runtimeImport.IndexOf('Assert-DestinationSnapshotStillOwned $record', $runtimeImportLeafBuildIndex)
$runtimeImportLeafRecheckIndex = $runtimeImport.IndexOf('Assert-VerifiedOwnedLeaf', $runtimeImportSnapshotRecheckIndex)
$runtimeImportTouchIndex = $runtimeImport.IndexOf('$touched.Add($record)', $runtimeImportLeafRecheckIndex)
$runtimeImportReplaceIndex = $runtimeImport.IndexOf('[IO.File]::Replace(', $runtimeImportTouchIndex)
$runtimeImportExistingCommitIndex = $runtimeImport.IndexOf('$record.Committed = $true', $runtimeImportReplaceIndex)
$runtimeImportExistingPublishedShaIndex = $runtimeImport.IndexOf('$record.PublishedSha256 = $record.ExpectedSha256', $runtimeImportExistingCommitIndex)
$runtimeImportMoveIndex = $runtimeImport.IndexOf('[IO.File]::Move(', $runtimeImportExistingPublishedShaIndex)
$runtimeImportAbsentCommitIndex = $runtimeImport.IndexOf('$record.Committed = $true', $runtimeImportMoveIndex)
$runtimeImportAbsentPublishedShaIndex = $runtimeImport.IndexOf('$record.PublishedSha256 = $record.ExpectedSha256', $runtimeImportAbsentCommitIndex)
$runtimeImportVerifyIndex = $runtimeImport.IndexOf('RUNTIME_IMPORT_FINAL_VERIFY_PASS')
$runtimeImportCatchIndex = $runtimeImport.IndexOf('catch {', $runtimeImportVerifyIndex)
$runtimeImportFinalIndex = $runtimeImport.IndexOf('EXTERNAL_RUNTIME_IMPORT_PASS')
Assert-True ($runtimeImportPreflightIndex -ge 0) 'Runtime importer source preflight marker not found.'
Assert-True ($runtimeImportStageIndex -gt $runtimeImportPreflightIndex) 'Runtime importer stages before proving the complete source triplet.'
Assert-True ($runtimeImportBackupIndex -gt $runtimeImportStageIndex) 'Runtime importer snapshots destinations before all staged artifacts are verified.'
Assert-True ($runtimeImportDirectoryReadyIndex -gt $runtimeImportBackupIndex) 'Runtime importer creates/adopts destination directories before backup authority is frozen.'
Assert-True ($runtimeImportLeafBuildIndex -gt $runtimeImportDirectoryReadyIndex) 'Runtime importer builds same-directory publication bytes before destination-directory ownership is proven.'
Assert-True ($runtimeImportSnapshotRecheckIndex -gt $runtimeImportLeafBuildIndex) 'Runtime importer does not revalidate the exact canonical snapshot after the private publication leaf is fully verified.'
Assert-True ($runtimeImportLeafRecheckIndex -gt $runtimeImportSnapshotRecheckIndex) 'Runtime importer does not revalidate private publication-leaf identity immediately after canonical snapshot revalidation.'
Assert-True ($runtimeImportTouchIndex -gt $runtimeImportLeafRecheckIndex) 'Runtime importer acquires rollback tracking before both sides of the atomic transition are revalidated.'
Assert-True ($runtimeImportReplaceIndex -gt $runtimeImportTouchIndex) 'Runtime importer can atomically replace an existing destination before rollback tracking.'
Assert-True ($runtimeImportExistingCommitIndex -gt $runtimeImportReplaceIndex) 'Runtime importer marks existing-destination publication committed before File.Replace completes.'
Assert-True ($runtimeImportExistingPublishedShaIndex -gt $runtimeImportExistingCommitIndex) 'Runtime importer records existing-destination published bytes before commit state.'
Assert-True ($runtimeImportMoveIndex -gt $runtimeImportExistingPublishedShaIndex) 'Runtime importer absent-destination branch ordering no longer follows the existing-destination transition.'
Assert-True ($runtimeImportAbsentCommitIndex -gt $runtimeImportMoveIndex) 'Runtime importer marks absent-destination publication committed before File.Move completes.'
Assert-True ($runtimeImportAbsentPublishedShaIndex -gt $runtimeImportAbsentCommitIndex) 'Runtime importer records absent-destination published bytes before commit state.'
Assert-True ($runtimeImportVerifyIndex -gt $runtimeImportAbsentPublishedShaIndex) 'Runtime importer final verification does not follow both committed canonical publication paths.'
Assert-True ($runtimeImportCatchIndex -gt $runtimeImportVerifyIndex) 'Runtime importer final verification escaped rollback ownership.'
Assert-True ($runtimeImportFinalIndex -gt $runtimeImportVerifyIndex) 'Runtime importer reports success before final whole-triplet verification.'
Assert-True ($runtimeBuilder -match 'build_v308_local_clients\.py') 'PowerShell runtime builder does not invoke the deterministic v308 patcher.'
Assert-True ($runtimeBuilder -match 'Check-ExternalRuntime\.ps1') 'PowerShell runtime builder does not verify the rebuilt exact-v308 triplet.'
Assert-True ($runtimeBuilder -match 'V308_LOCAL_CLIENT_BUILD_AND_VERIFY_PASS') 'PowerShell runtime builder marker missing.'
Assert-True ($runtimeBuilder -match 'OutputDirectory must be the canonical LocalLab runtime directory') 'PowerShell runtime builder accepts an unverifiable noncanonical output directory.'
Assert-True ($runtimeBuilder -match 'ClientJar must be the canonical evidence path') 'PowerShell runtime builder accepts a client path that permanent runtime verification cannot prove.'
Assert-True ($runtimeBuilder -match '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6') 'PowerShell runtime builder no longer independently gates the exact v308 input SHA.'
Assert-True ($runtimeBuilder -match 'function Get-Sha256Hex') 'PowerShell runtime builder lacks opened-stream SHA authority.'
Assert-True ($runtimeBuilder -match '\[IO\.File\]::Open\(') 'PowerShell runtime builder lacks guarded exact-v308 input admission.'
Assert-True ($runtimeBuilder -match '\$sourceGuard\.CopyTo\(\$snapshotWriter\)') 'PowerShell runtime builder does not copy from the admitted opened source identity.'
Assert-True ($runtimeBuilder -match '\$privateClientSha\s*=\s*Get-Sha256Hex -Stream \$privateGuard') 'PowerShell runtime builder does not independently hash the guarded private input snapshot.'
Assert-True ($runtimeBuilder -match 'invocationOwnedClient=true') 'PowerShell runtime builder preflight does not declare invocation-owned client authority.'
Assert-True ($runtimeBuilder -match 'V308InputSnapshotCleanupFailure') 'PowerShell runtime builder does not preserve subordinate snapshot-cleanup evidence on primary patcher failure.'
Assert-True ($runtimeBuilder -match 'private guard dispose:') 'PowerShell runtime builder does not retain private-guard cleanup evidence.'
Assert-True ($runtimeBuilder -match 'source guard dispose:') 'PowerShell runtime builder does not retain source-guard cleanup evidence.'
Assert-True ($runtimeBuilder -match 'snapshot root cleanup:') 'PowerShell runtime builder does not retain private-root cleanup evidence.'
Assert-True ($runtimeBuilder -match '\$cleanupFailures\.Count -ne 0') 'PowerShell runtime builder does not aggregate best-effort snapshot cleanup failures.'
Assert-True ($runtimeBuilder -match 'V308_LOCAL_CLIENT_BUILD_PREFLIGHT_PASS') 'PowerShell runtime builder does not report complete fail-before-canonical-mutation admission.'
Assert-True ($runtimeBuilder -match '\$canonicalEvidence') 'PowerShell runtime builder no longer resolves canonical evidence-client authority before mutation.'
Assert-True ($runtimeBuilder -match '\$canonicalOutput') 'PowerShell runtime builder no longer resolves canonical output authority before mutation.'
Assert-True ($runtimeBuilder -match 'function Assert-CanonicalOutputPathSafe') 'PowerShell runtime builder does not reject reparse aliases on canonical local-client publication path.'
Assert-True ($runtimeBuilder -match 'Canonical local-client path must not traverse a reparse point') 'PowerShell runtime builder has no existing-component reparse rejection.'
Assert-True ($runtimeBuilder -match 'Canonical local-client output exists but is not a directory') 'PowerShell runtime builder does not reject non-directory canonical output collisions.'
Assert-True ($runtimeBuilder -match 'Canonical local-client output must not be a reparse point') 'PowerShell runtime builder does not recheck the final output directory for reparse aliasing.'
Assert-True ($runtimeBuilder -notmatch 'New-Item -ItemType Directory[^\r\n]*\$output') 'PowerShell runtime builder must not create canonical local-client; Python owns absent-directory transaction state.'
Assert-ExactTextCount $runtimeBuilder 'Assert-CanonicalOutputPathSafe $output' 2 'PowerShell runtime builder must prove canonical output path safety at admission and immediately before the Python boundary.'
Assert-ExactTextCount $runtimeBuilder '[IO.File]::Open(' 3 'PowerShell runtime builder guarded input open count drift.'
Assert-ExactTextCount $runtimeBuilder '[IO.FileShare]::Read' 2 'PowerShell runtime builder read-guard share count drift.'
Assert-ExactTextCount $runtimeBuilder '[IO.FileShare]::None' 1 'PowerShell runtime builder private-writer share count drift.'
Assert-ExactTextCount $runtimeBuilder '[IO.FileMode]::CreateNew' 1 'PowerShell runtime builder private-writer creation mode drift.'
Assert-ExactTextCount $runtimeBuilder '& $python.Source $patcher $snapshotClient $output' 1 'PowerShell runtime builder must invoke the patcher exactly once with the guarded private snapshot.'
Assert-ExactTextCount $runtimeBuilder '& $python.Source $patcher $client $output' 0 'PowerShell runtime builder must never pass the mutable canonical evidence path to Python.'

$runtimeBuilderCanonicalClientIndex = $runtimeBuilder.IndexOf('ClientJar must be the canonical evidence path')
$runtimeBuilderOutputIndex = $runtimeBuilder.IndexOf('OutputDirectory must be the canonical LocalLab runtime directory')
$runtimeBuilderPathFenceIndex = $runtimeBuilder.IndexOf('$output = Assert-CanonicalOutputPathSafe $output')
$runtimeBuilderPythonIndex = $runtimeBuilder.IndexOf("Get-Command python")
$runtimeBuilderSourceOpenIndex = $runtimeBuilder.IndexOf('[IO.File]::Open(')
$runtimeBuilderSourceHashIndex = $runtimeBuilder.IndexOf('$actualClient = Get-Sha256Hex -Stream $sourceGuard')
$runtimeBuilderWriterOpenIndex = $runtimeBuilder.IndexOf('[IO.File]::Open(', $runtimeBuilderSourceOpenIndex + 1)
$runtimeBuilderCopyIndex = $runtimeBuilder.IndexOf('$sourceGuard.CopyTo($snapshotWriter)')
$runtimeBuilderPrivateOpenIndex = $runtimeBuilder.IndexOf('[IO.File]::Open(', $runtimeBuilderWriterOpenIndex + 1)
$runtimeBuilderPrivateHashIndex = $runtimeBuilder.IndexOf('$privateClientSha = Get-Sha256Hex -Stream $privateGuard')
$runtimeBuilderSourceReleaseIndex = $runtimeBuilder.IndexOf('$sourceGuard.Dispose()')
$runtimeBuilderPreflightPassIndex = $runtimeBuilder.IndexOf('V308_LOCAL_CLIENT_BUILD_PREFLIGHT_PASS')
$runtimeBuilderPathRecheckIndex = $runtimeBuilder.LastIndexOf('$output = Assert-CanonicalOutputPathSafe $output')
$runtimeBuilderPatchIndex = $runtimeBuilder.IndexOf('& $python.Source $patcher $snapshotClient $output')
$runtimeBuilderPrivateReleaseIndex = $runtimeBuilder.LastIndexOf('$privateGuard.Dispose()')
$runtimeBuilderSnapshotCleanupIndex = $runtimeBuilder.LastIndexOf('Remove-Item -LiteralPath $snapshotRoot -Recurse -Force')
$runtimeBuilderFailureReplayIndex = $runtimeBuilder.IndexOf('throw $patcherFailure')
$runtimeBuilderFinalVerifyIndex = $runtimeBuilder.IndexOf("& (Join-Path `$PSScriptRoot 'Check-ExternalRuntime.ps1')")
Assert-True ($runtimeBuilderCanonicalClientIndex -ge 0) 'PowerShell runtime builder canonical-client admission check not found.'
Assert-True ($runtimeBuilderOutputIndex -gt $runtimeBuilderCanonicalClientIndex) 'PowerShell runtime builder validates canonical output before canonical client admission.'
Assert-True ($runtimeBuilderPathFenceIndex -gt $runtimeBuilderOutputIndex) 'PowerShell runtime builder checks path confinement before canonical lexical admission completes.'
Assert-True ($runtimeBuilderPythonIndex -gt $runtimeBuilderPathFenceIndex) 'PowerShell runtime builder probes Python before canonical output reparse admission.'
Assert-True ($runtimeBuilderSourceOpenIndex -gt $runtimeBuilderPythonIndex) 'PowerShell runtime builder opens exact-v308 source before static prerequisites are proven.'
Assert-True ($runtimeBuilderSourceHashIndex -gt $runtimeBuilderSourceOpenIndex) 'PowerShell runtime builder does not hash the already-open source identity.'
Assert-True ($runtimeBuilderWriterOpenIndex -gt $runtimeBuilderSourceHashIndex) 'PowerShell runtime builder opens private writer before source SHA admission.'
Assert-True ($runtimeBuilderCopyIndex -gt $runtimeBuilderWriterOpenIndex) 'PowerShell runtime builder copies before private writer creation.'
Assert-True ($runtimeBuilderPrivateOpenIndex -gt $runtimeBuilderCopyIndex) 'PowerShell runtime builder opens private read guard before snapshot copy completes.'
Assert-True ($runtimeBuilderPrivateHashIndex -gt $runtimeBuilderPrivateOpenIndex) 'PowerShell runtime builder does not hash the already-open private snapshot.'
Assert-True ($runtimeBuilderSourceReleaseIndex -gt $runtimeBuilderPrivateHashIndex) 'PowerShell runtime builder releases source guard before private snapshot identity is proven.'
Assert-True ($runtimeBuilderPreflightPassIndex -gt $runtimeBuilderSourceReleaseIndex) 'PowerShell runtime builder reports preflight before invocation-owned identity is complete.'
Assert-True ($runtimeBuilderPathRecheckIndex -gt $runtimeBuilderPreflightPassIndex) 'PowerShell runtime builder does not re-prove canonical path safety immediately before the Python boundary.'
Assert-True ($runtimeBuilderPatchIndex -gt $runtimeBuilderPathRecheckIndex) 'PowerShell runtime builder invokes patcher before final wrapper-side reparse proof.'
Assert-True ($runtimeBuilderPrivateReleaseIndex -gt $runtimeBuilderPatchIndex) 'PowerShell runtime builder releases private input guard before Python patcher completes.'
Assert-True ($runtimeBuilderSnapshotCleanupIndex -gt $runtimeBuilderPrivateReleaseIndex) 'PowerShell runtime builder removes private input snapshot before releasing its guard.'
Assert-True ($runtimeBuilderFailureReplayIndex -gt $runtimeBuilderSnapshotCleanupIndex) 'PowerShell runtime builder may replay primary patcher failure before private input cleanup finishes.'
Assert-True ($runtimeBuilderFinalVerifyIndex -gt $runtimeBuilderFailureReplayIndex) 'PowerShell runtime builder final triplet verification does not follow successful guarded patcher cleanup.'
Assert-True ($v308Patcher -match 'ZIP_STORED') 'v308 local-client patcher no longer uses compression-independent deterministic JAR entries.'
Assert-True ($v308Patcher -match 'wholeJarDeterminismIndependentOfZlib') 'v308 local-client manifest no longer records zlib-independent whole-JAR determinism.'
Assert-True ($v308Patcher -notmatch 'ZIP_DEFLATED') 'v308 local-client patcher reintroduced zlib-dependent output compression.'
Assert-True ($v308Patcher -match 'unchangedEntryPayloadIdentity') 'v308 local-client patcher no longer proves unrelated entry payload identity.'
Assert-True ($v308Patcher -match 'entryInventoryAndOrderPreserved') 'v308 local-client patcher no longer proves entry inventory/order preservation.'
Assert-True ($v308Patcher -match 'manifestPayloadPreserved') 'v308 local-client patcher no longer proves manifest preservation.'

Assert-True ($v308Patcher -match '(?m)^import stat\r?$') 'v308 local-client publisher lacks stat-mode authority for non-following path checks.'
Assert-True ($v308Patcher -match 'def lexical_absolute') 'v308 local-client publisher does not preserve lexical output identity.'
Assert-True ($v308Patcher -match 'def lstat_or_none') 'v308 local-client publisher does not use non-following lstat path inspection.'
Assert-True ($v308Patcher -match 'def is_reparse_or_symlink') 'v308 local-client publisher does not classify Windows reparse/symlink entries.'
Assert-True ($v308Patcher -match 'FILE_ATTRIBUTE_REPARSE_POINT') 'v308 local-client publisher does not test Windows reparse-point file attributes.'
Assert-True ($v308Patcher -match 'def assert_ordinary_directory') 'v308 local-client publisher does not require ordinary canonical directories.'
Assert-True ($v308Patcher -match 'def assert_regular_file_or_absent') 'v308 local-client publisher does not require ordinary canonical destination leaves or absence.'
Assert-True ($v308Patcher -match 'def assert_canonical_output_path') 'v308 local-client publisher lacks independent canonical output path authority.'
Assert-True ($v308Patcher -match 'output = assert_canonical_output_path\(args\.output_directory\)') 'v308 local-client publisher does not apply its own canonical lexical path proof.'
Assert-True ($v308Patcher -notmatch 'output = args\.output_directory\.resolve\(\)') 'v308 local-client publisher reintroduced path-following resolve() as canonical output authority.'
Assert-True ($v308Patcher -match 'output_created = False') 'v308 local-client publisher does not explicitly own absent canonical directory creation.'
Assert-True ($v308Patcher -match 'os\.mkdir\(output\)') 'v308 local-client publisher does not create an absent canonical directory inside transaction ownership.'
Assert-True ($v308Patcher -match 'transaction-created canonical local-client') 'v308 local-client publisher does not re-prove the directory it created.'
Assert-True ($v308Patcher -match 'snapshot_identity') 'v308 local-client publisher does not retain non-following canonical destination identity.'
Assert-True ($v308Patcher -match 'file_identity\(destination_state\)') 'v308 local-client publisher does not freeze canonical destination lifetime identity at backup snapshot.'
Assert-True ($v308Patcher -match 'canonical destination identity changed during backup') 'v308 local-client publisher does not detect destination replacement during backup.'
Assert-True ($v308Patcher -match 'def restore_moved_entry_without_follow') 'v308 local-client publisher cannot compensate an atomically moved ambiguous entry without following it.'
Assert-True ($v308Patcher -match 'moved_identity != record\["snapshot_identity"\]') 'v308 local-client publisher does not revalidate moved commit preimage identity before hashing.'
Assert-True ($v308Patcher -match 'rollback quarantine identity drift') 'v308 local-client publisher does not revalidate moved rollback quarantine identity before hashing.'
Assert-True ($v308Patcher -match '\"published_identity\": None') 'v308 local-client publisher does not reserve exact committed file identity in transaction state.'
Assert-True ($v308Patcher -match 'record\[\"published_identity\"\] = publish_identity') 'v308 local-client publisher does not retain exact canonical identity after no-overwrite publication.'
Assert-True ($v308Patcher -match 'final canonical identity drift') 'v308 local-client final verification does not require exact committed file identity.'
Assert-True ($v308Patcher -match 'rollback lifetime identity lost') 'v308 local-client rollback does not require exact committed file identity before quarantine.'
Assert-True ($v308Patcher -match 'restored destination identity does not match source leaf') 'v308 local-client restore does not prove no-overwrite hard-link identity before hashing restored bytes.'
Assert-True ($v308Patcher -match 'os\.path\.lexists\(record\["destination"\]\)') 'v308 local-client rollback still uses path-following existence for canonical destination preservation.'
Assert-True ($v308Patcher -notmatch 'destination\.is_file\(\)') 'v308 local-client publisher reintroduced path-following destination.is_file() canonical authority.'
Assert-True ($v308Patcher -notmatch 'destination\.exists\(\)') 'v308 local-client publisher reintroduced path-following destination.exists() canonical authority.'
Assert-True ($v308Patcher -notmatch 'record\["destination"\]\.exists\(\)') 'v308 local-client publisher reintroduced path-following record destination exists authority.'
Assert-True ($v308Patcher -match 'os\.rmdir\(output\)') 'v308 local-client rollback does not remove transaction-created canonical output directory.'
Assert-True ($v308Patcher -match 'canonical local-client directory cleanup') 'v308 local-client rollback hides failure to retire its transaction-created directory.'
Assert-True ($v308Patcher -match 'tempfile\.TemporaryDirectory') 'v308 local-client builder does not use transaction-owned staging.'
Assert-True ($v308Patcher -match 'V308_LOCAL_CLIENT_STAGE_VERIFY_PASS') 'v308 local-client builder does not prove staged artifacts before publication.'
Assert-True ($v308Patcher -match 'V308_LOCAL_CLIENT_BACKUP_READY') 'v308 local-client builder does not snapshot canonical generated outputs before publication.'
Assert-True ($v308Patcher -match 'V308_LOCAL_CLIENT_FINAL_VERIFY_PASS') 'v308 local-client builder does not verify the complete published generated set under rollback ownership.'
Assert-True ($v308Patcher -match 'V308_LOCAL_CLIENT_ROLLBACK_COMPLETE') 'v308 local-client builder has no explicit clean rollback marker.'
Assert-True ($v308Patcher -match 'transactionalPublication') 'v308 local-client manifest no longer records transactional publication ownership.'
Assert-True ($v308Patcher -match 'touched\.append\(record\)') 'v308 local-client builder does not acquire rollback ownership before canonical mutation.'
Assert-True ($v308Patcher -match 'for record in reversed\(touched\)') 'v308 local-client builder does not restore touched outputs in reverse publication order.'
Assert-True ($v308Patcher -match 'def new_same_directory_leaf_path') 'v308 local-client builder lacks same-directory private-leaf authority.'
Assert-True ($v308Patcher -match 'def copy_verified_same_directory_leaf') 'v308 local-client builder lacks verified private publication leaves.'
Assert-True ($v308Patcher -match 'tempfile\.mkstemp\(') 'v308 local-client builder private publication leaf is not exclusively created.'
Assert-True ($v308Patcher -match 'os\.fsync\(') 'v308 local-client builder does not flush private publication bytes before verification.'
Assert-True ($v308Patcher -match 'def assert_destination_snapshot_owned') 'v308 local-client builder does not revalidate canonical preimage ownership immediately before mutation.'
Assert-True ($v308Patcher -match 'os\.link\(') 'v308 local-client builder lacks no-overwrite canonical directory-entry publication.'
Assert-True ($v308Patcher -match 'os\.replace\(') 'v308 local-client builder lacks atomic same-directory preimage/quarantine transitions.'
Assert-True ($v308Patcher -match 'record\["committed"\]\s*=\s*True') 'v308 local-client builder does not distinguish committed canonical publication.'
Assert-True ($v308Patcher -match 'record\["published_sha"\]\s*=\s*record\["expected_sha"\]') 'v308 local-client builder does not retain exact published-byte rollback authority.'
Assert-True ($v308Patcher -match 'rollback ownership lost') 'v308 local-client rollback does not refuse changed canonical bytes.'
Assert-True ($v308Patcher -match 'preimage preserved after') 'v308 local-client rollback can delete the preserved pre-transaction file after ownership loss.'
Assert-True ($v308Patcher -notmatch 'shutil\.copyfile\(record\["stage"\], record\["destination"\]\)') 'v308 local-client builder reintroduced direct staged copy into canonical output.'
Assert-True ($v308Patcher -notmatch 'shutil\.copyfile\(\s*record\["backup"\],\s*record\["destination"\],?\s*\)') 'v308 local-client builder reintroduced direct rollback copy into canonical output.'
Assert-True ($v308Patcher -notmatch 'build_variant\(source, output / "client-localhost\.jar"') 'v308 local-client builder reintroduced direct localhost generation into canonical output.'
Assert-True ($v308Patcher -notmatch 'build_variant\(source, output / "client-airgap\.jar"') 'v308 local-client builder reintroduced direct airgap generation into canonical output.'

foreach ($entry in @(
    @('def lexical_absolute', 1),
    @('def lstat_or_none', 1),
    @('def is_reparse_or_symlink', 1),
    @('def assert_ordinary_directory', 1),
    @('def assert_regular_file_or_absent', 1),
    @('def assert_canonical_output_path', 1),
    @('def restore_moved_entry_without_follow', 1),
    @('output = assert_canonical_output_path(args.output_directory)', 1),
    @('output_created = False', 1),
    @('os.mkdir(output)', 1),
    @('os.rmdir(output)', 1),
    @('canonical destination identity changed during backup', 1),
    @('moved commit preimage identity drift', 1),
    @('rollback quarantine identity drift', 1),
    @('"published_identity": None', 1),
    @('record["published_identity"] = publish_identity', 1),
    @('final canonical identity drift', 1),
    @('rollback lifetime identity lost', 1)
)) {
    Assert-ExactTextCount $v308Patcher $entry[0] ([int]$entry[1]) 'v308 publisher path-confinement structural count drift.'
}
$v308StageIndex = $v308Patcher.IndexOf('V308_LOCAL_CLIENT_STAGE_VERIFY_PASS')
$v308BackupIndex = $v308Patcher.IndexOf('V308_LOCAL_CLIENT_BACKUP_READY')
$v308OutputCreateIndex = $v308Patcher.IndexOf('os.mkdir(output)')
$v308DestinationSnapshotIndex = $v308Patcher.IndexOf('destination_state = assert_regular_file_or_absent(')
$v308LeafIndex = $v308Patcher.IndexOf('record["publish_leaf"] = copy_verified_same_directory_leaf')
$v308SnapshotRecheckIndex = $v308Patcher.IndexOf('assert_destination_snapshot_owned(record)', $v308LeafIndex)
$v308LeafRecheckIndex = $v308Patcher.IndexOf('assert_verified_leaf(', $v308SnapshotRecheckIndex)
$v308TouchIndex = $v308Patcher.IndexOf('touched.append(record)', $v308LeafRecheckIndex)
$v308PreimageTransitionIndex = $v308Patcher.IndexOf('os.replace(', $v308TouchIndex)
$v308PublishIndex = $v308Patcher.IndexOf('os.link(', $v308TouchIndex)
$v308PublishedIdentityIndex = $v308Patcher.IndexOf('record["published_identity"] = publish_identity', $v308PublishIndex)
$v308CommitIndex = $v308Patcher.IndexOf('record["committed"] = True', $v308PublishIndex)
$v308PublishedShaIndex = $v308Patcher.IndexOf('record["published_sha"] = record["expected_sha"]', $v308CommitIndex)
$v308FinalVerifyIndex = $v308Patcher.IndexOf('V308_LOCAL_CLIENT_FINAL_VERIFY_PASS')
$v308ExceptIndex = $v308Patcher.IndexOf('except BaseException as publish_error', $v308FinalVerifyIndex)
$v308SuccessIndex = $v308Patcher.IndexOf('V308_LOCAL_CLIENT_PATCH_PASS')
Assert-True ($v308StageIndex -ge 0) 'v308 local-client staged verification marker not found.'
Assert-True ($v308BackupIndex -gt $v308StageIndex) 'v308 local-client builder snapshots canonical outputs before staged verification.'
Assert-True ($v308OutputCreateIndex -gt $v308StageIndex) 'v308 local-client publisher creates canonical output before all staged bytes are verified.'
Assert-True ($v308DestinationSnapshotIndex -gt $v308OutputCreateIndex) 'v308 local-client publisher snapshots canonical destination leaves before transaction-owned output directory admission completes.'
Assert-True ($v308LeafIndex -gt $v308BackupIndex) 'v308 local-client builder creates a canonical-directory publication leaf before all backups exist.'
Assert-True ($v308SnapshotRecheckIndex -gt $v308LeafIndex) 'v308 local-client builder does not revalidate canonical ownership after private leaf verification.'
Assert-True ($v308LeafRecheckIndex -gt $v308SnapshotRecheckIndex) 'v308 local-client builder does not revalidate private publication bytes immediately before mutation.'
Assert-True ($v308TouchIndex -gt $v308LeafRecheckIndex) 'v308 local-client builder acquires mutation ownership before both sides of publication are revalidated.'
Assert-True ($v308PreimageTransitionIndex -gt $v308TouchIndex) 'v308 local-client builder can capture an existing canonical preimage before rollback ownership.'
Assert-True ($v308PublishIndex -gt $v308TouchIndex) 'v308 local-client builder can publish a canonical directory entry before rollback ownership.'
Assert-True ($v308PublishedIdentityIndex -gt $v308PublishIndex) 'v308 local-client publisher does not bind canonical lifetime identity after no-overwrite publication.'
Assert-True ($v308CommitIndex -gt $v308PublishIndex) 'v308 local-client builder marks publication committed before no-overwrite canonical publication succeeds.'
Assert-True ($v308PublishedShaIndex -gt $v308CommitIndex) 'v308 local-client builder records published-byte authority before commit state.'
Assert-True ($v308FinalVerifyIndex -gt $v308PublishedShaIndex) 'v308 local-client final verification does not follow committed publication.'
Assert-True ($v308ExceptIndex -gt $v308FinalVerifyIndex) 'v308 local-client final verification escaped rollback ownership.'
Assert-True ($v308SuccessIndex -gt $v308FinalVerifyIndex) 'v308 local-client builder reports success before final published-set verification.'

Assert-True ($releaseAcceptance -match [regex]::Escape('scripts\Select-LocalLabJava.ps1')) 'Current release acceptance does not use canonical runtime Java selector.'
Assert-True ($releaseAcceptance -match 'Set-LocalLabJava') 'Current release acceptance does not resolve canonical LocalLab runtime Java.'
Assert-True ($releaseAcceptance -match '\$processInfo\.FileName\s*=\s*\$runtimeJava\.Path') 'Current release loopback smoke does not launch with selected canonical Java path.'
Assert-True ($releaseAcceptance -notmatch 'Get-Command\s+java(?:\.exe)?') 'Current release acceptance reintroduced arbitrary PATH Java selection.'
Assert-True ($releaseAcceptance -match 'Run-Chat1CumulativeCertification\.ps1') 'Current release acceptance lost canonical cumulative certification wrapper.'
Assert-True ($releaseAcceptance -match 'CURRENT_RELEASE_CUMULATIVE_CERTIFICATION_PASS') 'Current release acceptance lost cumulative certification success boundary.'
Assert-True ($releaseAcceptance -match '\$hadCallerJavaHome\s*=\s*Test-Path Env:JAVA_HOME') 'Current release acceptance does not record whether caller JAVA_HOME existed.'
Assert-True ($releaseAcceptance -match '\$callerJavaHome\s*=\s*\$env:JAVA_HOME') 'Current release acceptance does not snapshot caller JAVA_HOME.'
Assert-True ($releaseAcceptance -match '\$callerPath\s*=\s*\$env:Path') 'Current release acceptance does not snapshot caller PATH.'
Assert-True ($releaseAcceptance -match 'finally\s*\{[\s\S]*\$env:Path\s*=\s*\$callerPath') 'Current release acceptance does not restore caller PATH in outer finally.'
Assert-True ($releaseAcceptance -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Current release acceptance does not restore an originally absent JAVA_HOME.'

foreach ($entry in @(
    @('$hadCallerJavaHome = Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome = $env:JAVA_HOME', 1),
    @('$callerPath = $env:Path', 1),
    @('$env:Path = $callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1)
)) {
    Assert-ExactTextCount $releaseAcceptance $entry[0] ([int]$entry[1]) 'Current release Java-environment ownership count drift.'
}

$releaseSelectorIndex = $releaseAcceptance.IndexOf('$runtimeJavaSelector = Join-Path')
$releaseJavaIndex = $releaseAcceptance.IndexOf('$runtimeJava = Set-LocalLabJava')
$releaseCumulativeIndex = $releaseAcceptance.IndexOf('& $cumulativeWrapper -ClientJar $client')
$releaseSmokeFunctionIndex = $releaseAcceptance.IndexOf('function Invoke-CurrentServerLoopbackSmoke')
$releaseSmokeIndex = $releaseAcceptance.LastIndexOf('Invoke-CurrentServerLoopbackSmoke')
$releaseStartProcessIndex = $releaseAcceptance.IndexOf('$processInfo.FileName = $runtimeJava.Path', $releaseSmokeFunctionIndex)
Assert-True ($releaseSelectorIndex -ge 0) 'Current release runtime Java selector path not found.'
Assert-True ($releaseJavaIndex -gt $releaseSelectorIndex) 'Current release resolves runtime Java before selector authority is established.'
Assert-True ($releaseCumulativeIndex -gt $releaseJavaIndex) 'Current release cumulative certification does not follow canonical Java selection.'
Assert-True ($releaseSmokeFunctionIndex -ge 0) 'Current release loopback smoke function definition not found.'
Assert-True ($releaseStartProcessIndex -gt $releaseSmokeFunctionIndex) 'Current release smoke function does not use Java selected by canonical policy.'
Assert-True ($releaseSmokeIndex -gt $releaseCumulativeIndex) 'Current release loopback smoke invocation starts before cumulative certification.'
$releaseEnvCaptureIndex = $releaseAcceptance.IndexOf('$callerPath = $env:Path')
$releaseEnvRestoreIndex = $releaseAcceptance.LastIndexOf('$env:Path = $callerPath')
Assert-True ($releaseEnvCaptureIndex -gt $releaseSelectorIndex -and $releaseEnvCaptureIndex -lt $releaseJavaIndex) 'Current release caller Java environment is not captured after selector-path admission and before selector mutation.'
Assert-True ($releaseEnvRestoreIndex -gt $releaseSmokeIndex) 'Current release caller Java environment is restored before the final loopback smoke completes.'

# Current release acceptance must bind source identity before executing any
# repository selector/regression/build path and retain it through final smoke.
Assert-True ($releaseAcceptance -match 'function Get-ReleaseExactGitHead') 'Current release acceptance lacks exact Git HEAD authority.'
Assert-True ($releaseAcceptance -match 'git -C \$repo status --porcelain --untracked-files=normal') 'Current release acceptance does not include untracked non-ignored worktree state.'
Assert-True ($releaseAcceptance -match 'CURRENT_RELEASE_SOURCE_IDENTITY_PASS') 'Current release acceptance does not report source-identity evidence.'
Assert-True ($releaseAcceptance -match 'CURRENT_RELEASE_ACCEPTANCE_PASS') 'Current release acceptance lacks a final whole-flow PASS boundary.'
Assert-True ($releaseAcceptance -match 'CURRENT_RELEASE_SERVER_SNAPSHOT_VERIFIED') 'Current release smoke is not bound to a private verified server artifact.'
Assert-True ($releaseAcceptance -match '\[IO\.File\]::Open\(') 'Current release smoke does not hold guarded server-file identity.'
Assert-True ($releaseAcceptance -match 'Get-FileHash -InputStream \$sourceGuard') 'Current release smoke does not hash the guarded cumulative-certified server identity.'
Assert-True ($releaseAcceptance -match 'Get-FileHash -InputStream \$privateGuard') 'Current release smoke does not verify the guarded private server artifact.'
Assert-True ($releaseAcceptance -match 'SpawnPK-current-release-smoke-') 'Current release smoke does not use invocation-owned private artifact scope.'
Assert-True ($releaseAcceptance -match 'Get-NetTCPConnection -State Listen -ErrorAction Stop') 'Current release smoke does not use authoritative listener ownership enumeration.'
Assert-True ($releaseAcceptance -match 'Assert-ExactSmokeListenerOwnership') 'Current release smoke does not bind both listeners to exact spawned PID.'
Assert-True ($releaseAcceptance -match '\[System\.Diagnostics\.Process\]\$ExpectedProcess') 'Current release listener ownership is not bound to the exact spawned process object.'
Assert-True ($releaseAcceptance -match '\$ExpectedProcess\.Refresh\(\)') 'Current release listener ownership does not refresh exact process lifetime before socket proof.'
Assert-True ($releaseAcceptance -match '\$ExpectedProcess\.HasExited') 'Current release listener ownership does not fail closed when exact spawned process has exited.'
Assert-True ($releaseAcceptance -match '\$spawnedPid\s*=\s*\[int\]\$process\.Id') 'Current release smoke does not retain exact spawned PID.'
Assert-True ($releaseAcceptance -match '\$process\.Kill\(\)') 'Current release smoke cleanup does not terminate through exact spawned process handle.'
Assert-True ($releaseAcceptance -match '\$process\.WaitForExit\(5000\)') 'Current release smoke cleanup does not require bounded exact-process termination.'
Assert-True ($releaseAcceptance -notmatch 'Stop-Process\s+-Name') 'Current release smoke introduced broad name-based cleanup.'
Assert-True ($releaseAcceptance -notmatch 'Stop-Process -Id \$process\.Id -Force -ErrorAction SilentlyContinue') 'Current release smoke still suppresses authoritative process cleanup failure.'
Assert-True ($releaseAcceptance -match 'Current release smoke failed and cleanup was incomplete') 'Current release smoke does not combine primary and cleanup failure authority.'

# Current release smoke evidence must remain inside one ordinary repository
# build/release-smoke ancestry and bind every published leaf to an owned
# CreateNew handle. Child stdout/stderr authority must come from process pipes,
# never mutable redirect paths.
Assert-True ($releaseAcceptance -match 'function Assert-ReleaseOrdinaryDirectory') 'Current release smoke lacks ordinary-directory validation.'
Assert-True ($releaseAcceptance -match 'function Assert-ReleaseSmokeAncestry') 'Current release smoke lacks exact ancestry validation.'
Assert-True ($releaseAcceptance -match 'function Initialize-ReleaseSmokeDirectory') 'Current release smoke lacks guarded one-level directory initialization.'
Assert-True ($releaseAcceptance -match 'function Remove-ReleaseSmokeEvidenceLeafSafely') 'Current release smoke lacks bounded ordinary-file cleanup.'
Assert-True ($releaseAcceptance -match 'function Write-ReleaseSmokeEvidenceTextOwned') 'Current release smoke lacks owned evidence publication.'
Assert-True ($releaseAcceptance -match '\$smokeBuildRoot\s*=\s*Join-Path\s+\$server\s+"build"') 'Current release smoke build authority is not derived directly from server root.'
Assert-True ($releaseAcceptance -match '\$smokeDir\s*=\s*Join-Path\s+\$smokeBuildRoot\s+"release-smoke"') 'Current release smoke evidence authority is not derived from exact build root.'
Assert-True ($releaseAcceptance -match '\[IO\.FileAttributes\]::ReparsePoint') 'Current release smoke does not reject reparse roots/leaves.'
Assert-True ($releaseAcceptance -match '\[IO\.FileMode\]::CreateNew') 'Current release smoke evidence publication is not fail-closed on pre-existing leaves.'
Assert-True ($releaseAcceptance -match '\[IO\.FileShare\]::None') 'Current release smoke evidence publication does not retain exclusive leaf ownership while writing.'
Assert-True ($releaseAcceptance -match '\$evidenceOwned\s*=\s*\$false') 'Current release smoke evidence writer does not track create-new leaf ownership.'
Assert-True ($releaseAcceptance -match '\$evidenceOwned\s*=\s*\$true') 'Current release smoke evidence writer never acquires leaf ownership after CreateNew.'
Assert-True ($releaseAcceptance -match 'Remove-ReleaseSmokeEvidenceLeafSafely -BuildRoot \$BuildRoot -SmokeRoot \$SmokeRoot -Path \$Path') 'Current release smoke evidence publication failure does not attempt bounded owned-leaf cleanup.'
Assert-True ($releaseAcceptance -match 'evidence publication failed and cleanup was unsafe/incomplete') 'Current release smoke evidence publication can lose cleanup-failure evidence.'
Assert-True ($releaseAcceptance -match '\$processInfo\.RedirectStandardOutput\s*=\s*\$true') 'Current release smoke stdout is not process-pipe owned.'
Assert-True ($releaseAcceptance -match '\$processInfo\.RedirectStandardError\s*=\s*\$true') 'Current release smoke stderr is not process-pipe owned.'
Assert-True ($releaseAcceptance -match '\$process\.StandardOutput\.ReadToEndAsync\(\)') 'Current release smoke does not asynchronously drain stdout pipe.'
Assert-True ($releaseAcceptance -match '\$process\.StandardError\.ReadToEndAsync\(\)') 'Current release smoke does not asynchronously drain stderr pipe.'
Assert-True ($releaseAcceptance -match '\$stdoutTask\.GetAwaiter\(\)\.GetResult\(\)') 'Current release smoke does not consume invocation-owned stdout pipe bytes.'
Assert-True ($releaseAcceptance -match '\$stderrTask\.GetAwaiter\(\)\.GetResult\(\)') 'Current release smoke does not consume invocation-owned stderr pipe bytes.'
Assert-True ($releaseAcceptance -match 'Current release smoke evidence cleanup refuses directory/reparse substitution') 'Current release smoke cleanup does not reject directory/reparse leaf substitution.'
Assert-True ($releaseAcceptance -notmatch 'New-Item\s+-ItemType\s+Directory\s+-Force\s+-Path\s+\$smokeDir') 'Current release smoke reintroduced force-creating the evidence directory.'
Assert-True ($releaseAcceptance -notmatch 'Remove-Item\s+-LiteralPath\s+\$stdout,\s*\$stderr,\s*\$versionsPath,\s*\$shaPath[^\r\n]*SilentlyContinue') 'Current release smoke reintroduced silent multi-path evidence cleanup.'
Assert-True ($releaseAcceptance -notmatch 'Remove-Item[^\r\n]*-Recurse[^\r\n]*\$smoke') 'Current release smoke introduced recursive evidence cleanup.'
Assert-True ($releaseAcceptance -notmatch 'Set-Content\s+-LiteralPath\s+\$shaPath') 'Current release smoke reintroduced path-open server-SHA evidence publication.'
Assert-True ($releaseAcceptance -notmatch 'Set-Content\s+-LiteralPath\s+\$versionsPath') 'Current release smoke reintroduced path-open versions evidence publication.'
Assert-True ($releaseAcceptance -notmatch '-RedirectStandardOutput\s+\$stdout') 'Current release smoke reintroduced mutable stdout redirect path authority.'
Assert-True ($releaseAcceptance -notmatch '-RedirectStandardError\s+\$stderr') 'Current release smoke reintroduced mutable stderr redirect path authority.'
Assert-True ($releaseAcceptance -notmatch 'Get-Content\s+-LiteralPath\s+\$stdout') 'Current release smoke reintroduced mutable stdout path read authority.'
Assert-True ($releaseAcceptance -notmatch 'Get-Content\s+-LiteralPath\s+\$stderr') 'Current release smoke reintroduced mutable stderr path read authority.'

foreach ($entry in @(
    @('function Get-ReleaseExactGitHead', 1),
    @('function Get-ReleaseWorktreeChanges', 1),
    @('function Assert-ReleaseSourceIdentity', 1),
    @('function Assert-ExactSmokeListenerOwnership', 1),
    @('Current release exact smoke process exited after listener snapshot', 1),
    @('function Invoke-CurrentServerLoopbackSmoke', 1),
    @('$cumulativeEvidenceName = (', 1),
    @('Tee-Object -Variable cumulativeOutput', 1),
    @('spawnpk-chat1-cumulative-result-v1', 1),
    @('$cumulativeResult = $cumulativeResults[0]', 1),
    @('certifiedServerJarSha256', 2),
    @('evidenceSha256', 1),
    @('Get-FileHash -InputStream $cumulativeEvidenceGuard -Algorithm SHA256', 1),
    @('Canonical cumulative certification evidence does not match the in-memory wrapper result.', 1),
    @('CURRENT_RELEASE_SERVER_SNAPSHOT_VERIFIED', 1),
    @('CURRENT_RELEASE_SERVER_LOOPBACK_PASS', 1),
    @('CURRENT_RELEASE_ACCEPTANCE_PASS', 1),
    @('$process.Kill()', 1),
    @('$process.WaitForExit(5000)', 1),
    @('function Assert-ReleaseOrdinaryDirectory', 1),
    @('function Assert-ReleaseSmokeAncestry', 1),
    @('function Initialize-ReleaseSmokeDirectory', 1),
    @('function Remove-ReleaseSmokeEvidenceLeafSafely', 1),
    @('function Write-ReleaseSmokeEvidenceTextOwned', 1),
    @('$smokeBuildRoot = Join-Path $server "build"', 1),
    @('$smokeDir = Join-Path $smokeBuildRoot "release-smoke"', 1),
    @('Initialize-ReleaseSmokeDirectory -BuildRoot $smokeBuildRoot -SmokeRoot $smokeDir', 1),
    @('Remove-ReleaseSmokeEvidenceLeafSafely -BuildRoot $smokeBuildRoot -SmokeRoot $smokeDir -Path $evidenceLeaf', 1),
    @('$processInfo.RedirectStandardOutput = $true', 1),
    @('$processInfo.RedirectStandardError = $true', 1),
    @('$stdoutTask = $process.StandardOutput.ReadToEndAsync()', 1),
    @('$stderrTask = $process.StandardError.ReadToEndAsync()', 1),
    @('$outText = [string]$stdoutTask.GetAwaiter().GetResult()', 1),
    @('$errText = [string]$stderrTask.GetAwaiter().GetResult()', 1),
    @("-Label 'server-sha'", 1),
    @("-Label 'versions'", 1),
    @("-Label 'stdout'", 1),
    @("-Label 'stderr'", 1)
)) {
    Assert-ExactTextCount $releaseAcceptance $entry[0] ([int]$entry[1]) 'Current release identity/smoke structural count drift.'
}

$releaseSmokeBuildIndex = $releaseAcceptance.IndexOf('$smokeBuildRoot = Join-Path $server "build"')
$releaseSmokeRootIndex = $releaseAcceptance.IndexOf('$smokeDir = Join-Path $smokeBuildRoot "release-smoke"')
$releaseSmokeInitializeIndex = $releaseAcceptance.IndexOf('Initialize-ReleaseSmokeDirectory -BuildRoot $smokeBuildRoot -SmokeRoot $smokeDir')
$releaseSmokeCleanupIndex = $releaseAcceptance.IndexOf('Remove-ReleaseSmokeEvidenceLeafSafely -BuildRoot $smokeBuildRoot -SmokeRoot $smokeDir -Path $evidenceLeaf')
$releaseSmokeShaPublishIndex = $releaseAcceptance.IndexOf("-Label 'server-sha'", $releaseSmokeCleanupIndex)
$releaseSmokeProcessInfoIndex = $releaseAcceptance.IndexOf('$processInfo = New-Object System.Diagnostics.ProcessStartInfo', $releaseSmokeShaPublishIndex)
$releaseSmokeProcessStartIndex = $releaseAcceptance.IndexOf('if (-not $process.Start())', $releaseSmokeProcessInfoIndex)
$releaseSmokeStdoutPipeIndex = $releaseAcceptance.IndexOf('$stdoutTask = $process.StandardOutput.ReadToEndAsync()', $releaseSmokeProcessStartIndex)
$releaseSmokeStderrPipeIndex = $releaseAcceptance.IndexOf('$stderrTask = $process.StandardError.ReadToEndAsync()', $releaseSmokeStdoutPipeIndex)
$releaseSmokeVersionsPublishIndex = $releaseAcceptance.IndexOf("-Label 'versions'", $releaseSmokeStderrPipeIndex)
$releaseSmokePipeResultIndex = $releaseAcceptance.IndexOf('$outText = [string]$stdoutTask.GetAwaiter().GetResult()', $releaseSmokeVersionsPublishIndex)
$releaseSmokeStdoutPublishIndex = $releaseAcceptance.IndexOf("-Label 'stdout'", $releaseSmokePipeResultIndex)
$releaseSmokeStderrPublishIndex = $releaseAcceptance.IndexOf("-Label 'stderr'", $releaseSmokeStdoutPublishIndex)
$releaseSmokeCombinedLogIndex = $releaseAcceptance.IndexOf('$combinedLog = $outText + "`n" + $errText', $releaseSmokeStderrPublishIndex)
$releaseHeadCaptureIndex = $releaseAcceptance.IndexOf('$releaseHead = Get-ReleaseExactGitHead')
$releasePreflightIdentityIndex = $releaseAcceptance.IndexOf('Assert-ReleaseSourceIdentity -ExpectedHead $releaseHead -Phase "preflight"')
$releaseSelectorInvokeIndex = $releaseAcceptance.IndexOf('. $runtimeJavaSelector')
$releaseLauncherInvokeIndex = $releaseAcceptance.IndexOf('& $launcherContract -SkipJavaProbe')
$releaseFirstGradleIndex = $releaseAcceptance.IndexOf('& $gradle clean build')
$releasePostBuildIdentityIndex = $releaseAcceptance.IndexOf('Assert-ReleaseSourceIdentity -ExpectedHead $releaseHead -Phase "post-build"')
$releaseCumulativeInvokeIndex = $releaseAcceptance.IndexOf('& $cumulativeWrapper -ClientJar $client')
$releaseCumulativeResultIndex = $releaseAcceptance.IndexOf('$cumulativeResult = $cumulativeResults[0]')
$releaseCertifiedServerShaIndex = $releaseAcceptance.IndexOf('$certifiedServerSha = [string]$cumulativeResult.certifiedServerJarSha256')
$releaseExpectedEvidenceShaIndex = $releaseAcceptance.IndexOf('$expectedEvidenceSha = [string]$cumulativeResult.evidenceSha256')
$releaseEvidenceGuardIndex = $releaseAcceptance.IndexOf('$cumulativeEvidenceGuard =', $releaseExpectedEvidenceShaIndex)
$releaseEvidenceHashIndex = $releaseAcceptance.IndexOf('Get-FileHash -InputStream $cumulativeEvidenceGuard -Algorithm SHA256')
$releaseEvidenceParseIndex = $releaseAcceptance.IndexOf('$cumulativeEvidence = $cumulativeEvidenceText | ConvertFrom-Json')
$releaseEvidenceServerMatchIndex = $releaseAcceptance.IndexOf('$cumulativeEvidence.certifiedServerJarSha256 -ne $certifiedServerSha')
$releasePostCumulativeIdentityIndex = $releaseAcceptance.IndexOf('Assert-ReleaseSourceIdentity -ExpectedHead $releaseHead -Phase "post-cumulative"')
$releaseCertifiedJarIndex = $releaseAcceptance.IndexOf('$certifiedJar = Join-Path $server "build\SpawnPKLocalServer.jar"')
$releaseSmokeInvokeIndex = $releaseAcceptance.LastIndexOf('Invoke-CurrentServerLoopbackSmoke -CanonicalJar $certifiedJar')
$releaseSmokeExpectedShaIndex = $releaseAcceptance.LastIndexOf('-ExpectedServerSha256 $certifiedServerSha')
$releasePostSmokeIdentityIndex = $releaseAcceptance.IndexOf('Assert-ReleaseSourceIdentity -ExpectedHead $releaseHead -Phase "post-smoke"')
$releaseFinalPassIndex = $releaseAcceptance.IndexOf('CURRENT_RELEASE_ACCEPTANCE_PASS')

Assert-True ($releaseSmokeBuildIndex -ge 0 -and $releaseSmokeRootIndex -gt $releaseSmokeBuildIndex) 'Current release smoke root is not derived from exact build authority.'
Assert-True ($releaseSmokeInitializeIndex -gt $releaseSmokeRootIndex) 'Current release smoke ancestry is not initialized after exact lexical authority is derived.'
Assert-True ($releaseSmokeCleanupIndex -gt $releaseSmokeInitializeIndex) 'Current release smoke evidence cleanup can run before guarded ancestry initialization.'
Assert-True ($releaseSmokeShaPublishIndex -gt $releaseSmokeCleanupIndex) 'Current release server-SHA evidence is published before bounded stale-leaf cleanup.'
Assert-True ($releaseSmokeProcessInfoIndex -gt $releaseSmokeShaPublishIndex -and $releaseSmokeProcessStartIndex -gt $releaseSmokeProcessInfoIndex) 'Current release exact server process setup does not follow owned SHA evidence publication.'
Assert-True ($releaseSmokeStdoutPipeIndex -gt $releaseSmokeProcessStartIndex -and $releaseSmokeStderrPipeIndex -gt $releaseSmokeStdoutPipeIndex) 'Current release stdout/stderr pipes are not acquired after exact process start.'
Assert-True ($releaseSmokeVersionsPublishIndex -gt $releaseSmokeStderrPipeIndex) 'Current release versions evidence publication precedes invocation-owned process output capture.'
Assert-True ($releaseSmokePipeResultIndex -gt $releaseSmokeVersionsPublishIndex) 'Current release consumes child pipe output before AUX/version semantics complete.'
Assert-True ($releaseSmokeStdoutPublishIndex -gt $releaseSmokePipeResultIndex -and $releaseSmokeStderrPublishIndex -gt $releaseSmokeStdoutPublishIndex) 'Current release durable stdout/stderr evidence is not published from captured process-pipe bytes.'
Assert-True ($releaseSmokeCombinedLogIndex -gt $releaseSmokeStderrPublishIndex) 'Current release log assertions do not operate on invocation-owned in-memory pipe bytes.'
Assert-True ($releaseHeadCaptureIndex -ge 0 -and $releasePreflightIdentityIndex -gt $releaseHeadCaptureIndex) 'Current release does not bind one exact Git head at preflight.'
Assert-True ($releaseSelectorInvokeIndex -gt $releasePreflightIdentityIndex) 'Current release executes repository Java-selector code before source identity preflight.'
Assert-True ($releaseLauncherInvokeIndex -gt $releasePreflightIdentityIndex) 'Current release executes launcher regression before source identity preflight.'
Assert-True ($releaseFirstGradleIndex -gt $releaseLauncherInvokeIndex) 'Current release Gradle build ordering drifted before launcher regression.'
Assert-True ($releasePostBuildIdentityIndex -gt $releaseFirstGradleIndex) 'Current release does not recheck source identity after ordinary build.'
Assert-True ($releaseCumulativeInvokeIndex -gt $releasePostBuildIdentityIndex) 'Current cumulative certification does not follow post-build source recheck.'
Assert-True ($releaseCumulativeResultIndex -gt $releaseCumulativeInvokeIndex) 'Current release does not consume the structured result from the exact wrapper invocation.'
Assert-True ($releaseCertifiedServerShaIndex -gt $releaseCumulativeResultIndex) 'Current release does not derive server SHA authority from the in-memory wrapper result.'
Assert-True ($releaseExpectedEvidenceShaIndex -gt $releaseCertifiedServerShaIndex) 'Current release does not bind durable evidence identity to the in-memory wrapper result.'
Assert-True ($releaseEvidenceGuardIndex -gt $releaseExpectedEvidenceShaIndex) 'Current release opens durable evidence before in-memory authority is established.'
Assert-True ($releaseEvidenceHashIndex -gt $releaseEvidenceGuardIndex) 'Current release does not hash the guarded durable evidence identity.'
Assert-True ($releaseEvidenceParseIndex -gt $releaseEvidenceHashIndex) 'Current release parses durable evidence before verifying its in-memory-bound SHA.'
Assert-True ($releaseEvidenceServerMatchIndex -gt $releaseEvidenceParseIndex) 'Current release does not cross-check durable JSON server SHA against in-memory authority.'
Assert-True ($releasePostCumulativeIdentityIndex -gt $releaseEvidenceServerMatchIndex) 'Current release does not recheck source identity after cumulative evidence cross-check.'
Assert-True ($releaseCertifiedJarIndex -gt $releasePostCumulativeIdentityIndex) 'Current release selects cumulative-certified server JAR before post-cumulative source proof.'
Assert-True ($releaseSmokeInvokeIndex -gt $releaseCertifiedJarIndex) 'Current release smoke invocation does not follow cumulative-certified JAR selection.'
Assert-True ($releaseSmokeExpectedShaIndex -gt $releaseCertifiedJarIndex) 'Current release smoke does not receive the cumulative-certified server SHA.'
Assert-True ($releasePostSmokeIdentityIndex -gt $releaseSmokeInvokeIndex) 'Current release final source identity check does not follow completed smoke invocation.'
Assert-True ($releaseFinalPassIndex -gt $releasePostSmokeIdentityIndex) 'Current release whole-flow PASS precedes final clean exact-head proof.'

# Validate ordering inside the smoke function independently from top-level flow.
$releaseListenerHelperIndex = $releaseAcceptance.IndexOf('function Assert-ExactSmokeListenerOwnership')
$releaseListenerSnapshotIndex = $releaseAcceptance.IndexOf('$connections = @(Get-ReleasePortListeners)', $releaseListenerHelperIndex)
$releaseListenerMismatchIndex = $releaseAcceptance.IndexOf('Current release smoke listener ownership mismatch', $releaseListenerHelperIndex)
$releaseListenerFirstRefreshIndex = $releaseAcceptance.IndexOf('$ExpectedProcess.Refresh()', $releaseListenerHelperIndex)
$releaseListenerSecondRefreshIndex = $releaseAcceptance.IndexOf('$ExpectedProcess.Refresh()', $releaseListenerFirstRefreshIndex + 1)
$releaseListenerPostSnapshotExitIndex = $releaseAcceptance.IndexOf('Current release exact smoke process exited after listener snapshot', $releaseListenerHelperIndex)

Assert-True ($releaseListenerHelperIndex -ge 0) 'Current release exact listener-ownership helper not found.'
Assert-True ($releaseListenerFirstRefreshIndex -gt $releaseListenerHelperIndex -and $releaseListenerFirstRefreshIndex -lt $releaseListenerSnapshotIndex) 'Current release listener proof does not preflight the retained Process lifetime before the listener snapshot.'
Assert-True ($releaseListenerMismatchIndex -gt $releaseListenerSnapshotIndex) 'Current release listener snapshot is not evaluated before lifetime revalidation.'
Assert-True ($releaseListenerSecondRefreshIndex -gt $releaseListenerMismatchIndex) 'Current release listener proof does not refresh the same retained Process after evaluating the listener snapshot.'
Assert-True ($releaseListenerPostSnapshotExitIndex -gt $releaseListenerSecondRefreshIndex) 'Current release listener proof does not fail closed when the retained Process exits after the listener snapshot.'

$releaseSmokeFunctionIndex = $releaseAcceptance.IndexOf('function Invoke-CurrentServerLoopbackSmoke')
$releaseSourceGuardIndex = $releaseAcceptance.IndexOf('$sourceGuard = [IO.File]::Open(', $releaseSmokeFunctionIndex)
$releaseSourceHashIndex = $releaseAcceptance.IndexOf('Get-FileHash -InputStream $sourceGuard', $releaseSmokeFunctionIndex)
$releasePrivateGuardIndex = $releaseAcceptance.IndexOf('$privateGuard = [IO.File]::Open(', $releaseSmokeFunctionIndex)
$releasePrivateHashIndex = $releaseAcceptance.IndexOf('Get-FileHash -InputStream $privateGuard', $releaseSmokeFunctionIndex)
$releaseExpectedShaCompareIndex = $releaseAcceptance.IndexOf('$certifiedSha -ne $ExpectedServerSha256', $releaseSmokeFunctionIndex)
$releasePrivateExpectedShaCompareIndex = $releaseAcceptance.IndexOf('$privateSha -ne $ExpectedServerSha256', $releaseSmokeFunctionIndex)
$releasePrivateVerifyIndex = $releaseAcceptance.IndexOf('CURRENT_RELEASE_SERVER_SNAPSHOT_VERIFIED', $releaseSmokeFunctionIndex)
$releaseProcessSpawnIndex = $releaseAcceptance.IndexOf('if (-not $process.Start())', $releaseSmokeFunctionIndex)
$releaseFirstOwnershipIndex = $releaseAcceptance.IndexOf('Assert-ExactSmokeListenerOwnership', $releaseSmokeFunctionIndex)
$releaseAuxIndex = $releaseAcceptance.IndexOf('Invoke-WebRequest', $releaseSmokeFunctionIndex)
$releaseFinalOwnershipIndex = $releaseAcceptance.LastIndexOf('Assert-ExactSmokeListenerOwnership')
$releaseKillIndex = $releaseAcceptance.IndexOf('$process.Kill()', $releaseSmokeFunctionIndex)
$releaseWaitExitIndex = $releaseAcceptance.IndexOf('$process.WaitForExit(5000)', $releaseSmokeFunctionIndex)
$releaseSmokePassIndex = $releaseAcceptance.IndexOf('CURRENT_RELEASE_SERVER_LOOPBACK_PASS', $releaseSmokeFunctionIndex)

Assert-True ($releaseSmokeFunctionIndex -ge 0) 'Current release smoke function not found.'
Assert-True ($releaseSourceGuardIndex -gt $releaseSmokeFunctionIndex) 'Current release does not acquire certified-JAR guard inside smoke ownership.'
Assert-True ($releaseSourceHashIndex -gt $releaseSourceGuardIndex) 'Current release hashes cumulative-certified bytes before acquiring the source guard.'
Assert-True ($releasePrivateGuardIndex -gt $releaseSourceHashIndex) 'Current release private guard is established before source identity is hashed/copied.'
Assert-True ($releasePrivateHashIndex -gt $releasePrivateGuardIndex) 'Current release hashes private smoke bytes before acquiring the private guard.'
Assert-True ($releaseExpectedShaCompareIndex -gt $releasePrivateHashIndex) 'Current release does not compare guarded build-path bytes to cumulative-certified server SHA.'
Assert-True ($releasePrivateExpectedShaCompareIndex -gt $releaseExpectedShaCompareIndex) 'Current release does not compare private smoke bytes to cumulative-certified server SHA.'
Assert-True ($releasePrivateVerifyIndex -gt $releasePrivateExpectedShaCompareIndex) 'Current release reports private snapshot verification before cumulative-certified SHA equality.'
Assert-True ($releaseProcessSpawnIndex -gt $releasePrivateVerifyIndex) 'Current release server process starts before private artifact verification.'
Assert-True ($releaseFirstOwnershipIndex -gt $releaseProcessSpawnIndex) 'Current release listener ownership is checked before exact process spawn.'
Assert-True ($releaseAuxIndex -gt $releaseFirstOwnershipIndex) 'Current release AUX semantic check precedes exact spawned-PID listener ownership.'
Assert-True ($releaseFinalOwnershipIndex -gt $releaseAuxIndex) 'Current release lacks final exact spawned-PID ownership recheck after AUX semantics.'
Assert-True ($releaseKillIndex -gt $releaseFinalOwnershipIndex) 'Current release cleanup begins before final listener ownership proof.'
Assert-True ($releaseWaitExitIndex -gt $releaseKillIndex) 'Current release does not wait for exact process after termination request.'
Assert-True ($releaseSmokePassIndex -gt $releaseWaitExitIndex) 'Current release smoke PASS can precede authoritative process cleanup.'


Assert-True ($r13Acceptance -match 'runtime\\locallab-user-home\\r13') 'R13 acceptance launcher default output is outside the ignored LocalLab runtime subtree.'
Assert-True ($r13Acceptance -match 'Assert-UnderRuntimeRoot') 'R13 acceptance launcher lacks a reusable destructive-cleanup containment fence.'
Assert-True ($r13Acceptance -match 'Assert-NoReparsePointAncestors') 'R13 acceptance launcher does not reject junction/symlink traversal in the output path.'
Assert-True ($r13Acceptance -match 'Assert-DisjointDirectories') 'R13 acceptance launcher does not enforce source/output disjointness.'
Assert-True ($r13Acceptance -match 'Assert-NoNestedReparsePoints') 'R13 acceptance launcher does not inspect nested reset-tree reparse points.'
Assert-True ($r13Acceptance -match 'Assert-NoReparsePointsInExistingPath') 'R13 acceptance launcher does not reject BaseSpawnpk reparse aliases.'
Assert-True ($r13Acceptance -match 'Assert-OutputNotOwnedByRunningAirgapClient') 'R13 acceptance launcher does not reject a reset tree owned by a live airgap client.'
Assert-True ($r13Acceptance -match 'Get-CimInstance\s+Win32_Process\s+-ErrorAction\s+Stop') 'R13 live-client ownership check does not fail closed when process enumeration is unavailable.'
Assert-True ($r13Acceptance -match '\[regex\]::Escape\(\$homeArgument\)') 'R13 live-client ownership matcher no longer escapes the exact user.home argument.'
Assert-True ($r13Acceptance -match '\(\?=\$\|\[\\s"\]\)') 'R13 live-client ownership matcher no longer requires an argument boundary after user.home.'
Assert-True ($r13Acceptance -match 'Get-ChildItem\s+-LiteralPath\s+\$directory\s+-Force') 'R13 acceptance nested reparse scan no longer walks one directory level at a time.'

$r13RemoveIndex = $r13Acceptance.IndexOf('Remove-Item -LiteralPath $output -Recurse -Force')
$r13DisjointIndex = $r13Acceptance.IndexOf('Assert-DisjointDirectories $base $output')
$r13NestedReparseIndex = $r13Acceptance.IndexOf('Assert-NoNestedReparsePoints $output')
$r13BaseReparseIndex = $r13Acceptance.IndexOf('Assert-NoReparsePointsInExistingPath $base')
$r13RequiredComponentsIndex = $r13Acceptance.IndexOf('foreach ($required in @(')
$r13JavaIndex = $r13Acceptance.IndexOf('$java = Set-LocalLabJava')
$r13JavacIndex = $r13Acceptance.IndexOf('$javac = Join-Path $javaBin')
$r13PythonIndex = $r13Acceptance.IndexOf('$python = Get-Command python.exe')
$r13RuntimeBuildIndex = $r13Acceptance.IndexOf('& $runtimeBuilder')
$r13LiveOwnerIndex = $r13Acceptance.IndexOf('Assert-OutputNotOwnedByRunningAirgapClient $output')
Assert-True ($r13RemoveIndex -ge 0) 'R13 acceptance launcher reset command not found.'
Assert-True ($r13DisjointIndex -ge 0 -and $r13DisjointIndex -lt $r13RemoveIndex) 'R13 source/output disjointness is not proven before destructive reset.'
Assert-True ($r13NestedReparseIndex -ge 0 -and $r13NestedReparseIndex -lt $r13RemoveIndex) 'R13 nested reparse scan is not proven before destructive reset.'
Assert-True ($r13BaseReparseIndex -ge 0 -and $r13BaseReparseIndex -lt $r13RemoveIndex) 'R13 BaseSpawnpk reparse-alias rejection is not proven before destructive reset.'
Assert-True ($r13RequiredComponentsIndex -ge 0 -and $r13RequiredComponentsIndex -lt $r13RemoveIndex) 'R13 required-component preflight occurs after destructive reset.'
Assert-True ($r13JavaIndex -ge 0 -and $r13JavaIndex -lt $r13RemoveIndex) 'R13 Java selection occurs after destructive reset.'
Assert-True ($r13JavacIndex -ge 0 -and $r13JavacIndex -lt $r13RemoveIndex) 'R13 javac preflight occurs after destructive reset.'
Assert-True ($r13PythonIndex -ge 0 -and $r13PythonIndex -lt $r13RemoveIndex) 'R13 Python preflight occurs after destructive reset.'
Assert-True ($r13RuntimeBuildIndex -ge 0 -and $r13RuntimeBuildIndex -lt $r13RemoveIndex) 'R13 exact-v308 runtime rebuild occurs after destructive reset.'
Assert-True ($r13LiveOwnerIndex -ge 0 -and $r13LiveOwnerIndex -lt $r13RemoveIndex) 'R13 live-airgap ownership check occurs after destructive reset.'
Assert-True ($r13Acceptance -match 'FileAttributes\]::ReparsePoint') 'R13 acceptance launcher lost the Windows reparse-point guard.'
Assert-True ($r13Acceptance -match 'Remove-Item\s+-LiteralPath\s+\$output\s+-Recurse\s+-Force') 'R13 acceptance launcher does not keep cleanup scoped to the validated output path.'
Assert-True ($r13Acceptance -match 'Build-V308LocalClients\.ps1') 'R13 acceptance launcher does not rebuild the coherent exact-v308 runtime.'
Assert-True ($r13Acceptance -match 'build_r13_isolated_profile\.py') 'R13 acceptance launcher does not build the exact isolated R13 profile.'
Assert-True ($r13Acceptance -match 'R13_PROFILE_MANIFEST\.json') 'R13 acceptance launcher does not verify the generated profile manifest.'
Assert-True ($r13Acceptance -match 'SPAWNPK_LOCALLAB_USER_HOME\s*=\s*\$output') 'R13 acceptance launcher does not bind the generated isolated profile into the client launch environment.'
Assert-True ($r13Acceptance -match 'RUN_ALL_LOCAL_LAB\.ps1') 'R13 acceptance launcher bypasses the canonical LocalLab multi-process launcher.'
Assert-True ($r13Acceptance -match 'automaticVisualPass=false') 'R13 acceptance launcher does not explicitly withhold automatic visual certification.'
Assert-True ($r13Acceptance -match 'item 29999') 'R13 acceptance launcher visual checklist lost item 29999 authority.'
Assert-True ($r13Acceptance -match 'Model 79999') 'R13 acceptance launcher visual checklist lost model 79999 authority.'
Assert-True ($r13Acceptance -match 'texture 278') 'R13 acceptance launcher visual checklist lost texture 278 authority.'
Assert-True ($r13Acceptance -match 'hadOldHome') 'R13 acceptance launcher does not preserve caller environment state.'
Assert-True ($r13Acceptance -match 'Remove-Item Env:SPAWNPK_LOCALLAB_USER_HOME') 'R13 acceptance launcher does not restore an originally absent isolated-home environment variable.'

Assert-True ($all -match [regex]::Escape('scripts\Select-LocalLabJava.ps1')) 'Multi-client launcher does not use canonical Java selector.'
Assert-True ($all -match [regex]::Escape('scripts\Run-Server.ps1')) 'Multi-client launcher does not target scripts/Run-Server.ps1.'
Assert-True ($all -match [regex]::Escape('scripts\Run-Client-Airgap.ps1')) 'Multi-client launcher does not target scripts/Run-Client-Airgap.ps1.'
Assert-True ($all -match [regex]::Escape('WATCH_CLIENT_NETWORK.ps1')) 'Multi-client launcher does not target WATCH_CLIENT_NETWORK.ps1.'
Assert-True ($all -match [regex]::Escape('scripts\Check-ExternalRuntime.ps1')) 'Multi-client launcher does not use current external-runtime preflight.'
$allTokens = $null
$allParseErrors = $null
[void][System.Management.Automation.Language.Parser]::ParseInput($all,[ref]$allTokens,[ref]$allParseErrors)
$sealedVerifierTokens = @($allTokens | Where-Object { ([string]$_.Kind) -ne 'Comment' -and $_.Text -match 'VERIFY_OFFLINE_READY\.ps1' })
Assert-True ($sealedVerifierTokens.Count -eq 0) 'Multi-client launcher still invokes the sealed historical R8.5 verifier.'
Assert-True ($all -match [regex]::Escape('server\build\SpawnPKLocalServer.jar')) 'Multi-client launcher does not preflight the current built server JAR.'
Assert-True ($all -match 'LOCAL_LAB_REPLACEMENT_PREFLIGHT_PASS') 'Multi-client launcher does not report complete replacement preflight before process termination.'
Assert-True ($all -match '\$admittedConflictStamp\s*=') 'Multi-client replacement does not retain admitted conflicting-process lifetime identity.'
Assert-True ($all -match 'Get-NormalizedProcessLifetimeStamp -Timestamp \(\[DateTime\]\$p\.CreationDate\)') 'Multi-client replacement does not normalize admitted CIM CreationDate.'
Assert-True ($all -match '\$currentConflict\s*=\s*Get-Process -Id \$ownerPid') 'Multi-client replacement does not re-resolve the current conflict process immediately before termination.'
Assert-True ($all -match 'Get-NormalizedProcessLifetimeStamp -Timestamp \(\[DateTime\]\$currentConflict\.StartTime\)') 'Multi-client replacement does not normalize the current conflict Process.StartTime.'
Assert-True ($all -match '\$currentConflictStamp\s+-ne\s+\$admittedConflictStamp') 'Multi-client replacement does not reject PID lifetime reuse.'
Assert-True ($all -match 'Stop-Process -InputObject \$currentConflict -Force') 'Multi-client replacement does not terminate the exact revalidated process object.'
Assert-True ($all -notmatch 'Stop-Process -Id \$ownerPid') 'Multi-client replacement still grants destructive authority to a bare listener PID.'
Assert-True ($all -match 'function\s+Get-LauncherOwnedProcessIds') 'Multi-client launcher lacks deterministic descendant ownership resolution.'
Assert-True ($all -match 'ParentProcessId') 'Multi-client launcher does not derive child authority from parent-process identity.'
Assert-True ($all -match '\[switch\]\$IncludeExitedRoots') 'Multi-client ownership helper cannot preserve proven ancestry after a child root exits.'
Assert-True ($all -match '\$recordedRoots') 'Multi-client ownership helper does not retain recorded root process authority.'
Assert-True ($all -match '\$liveRoots') 'Multi-client ownership helper no longer distinguishes live roots from recorded roots.'
Assert-True ($all -match '\$exitedRoots') 'Multi-client ownership helper does not identify exited recorded roots.'
Assert-True ($all -match '\$rootStart\s*=\s*\[DateTime\]\$exitedRoot\.StartTime') 'Multi-client cleanup does not bind exited-root ancestry to recorded root start time.'
Assert-True ($all -match '\$rootExit\s*=\s*\[DateTime\]\$exitedRoot\.ExitTime') 'Multi-client cleanup does not bind exited-root ancestry to recorded root exit time.'
Assert-True ($all -match '\$childCreated\s*=\s*\[DateTime\]\$directChild\.CreationDate') 'Multi-client cleanup does not inspect direct-child creation time.'
Assert-True ($all -match '\$childCreated -lt \$rootStart -or\s+\$childCreated -ge \$rootExit') 'Multi-client cleanup does not require the first exited-root edge to exist within the recorded root lifetime.'
Assert-True ($all -match 'cannot prove exited-root lifetime') 'Multi-client cleanup does not fail closed when recorded root lifetime cannot be read.'
Assert-True ($all -match 'cannot prove creation time for exited-root child') 'Multi-client cleanup does not fail closed when child creation time cannot be read.'
Assert-True ($all -match 'refused ambiguous exited-root PID reuse') 'Multi-client cleanup does not fail closed when an exited recorded PID is live again.'
Assert-True ($all -match 'Get-LauncherOwnedProcessRecords -Roots \$Roots -Label \$Label -IncludeExitedRoots') 'Multi-client failure cleanup does not opt into lifetime-bound exited-root traversal.'
Assert-True ($all -match '\$candidateServerPid\s+-in\s+\$serverOwnedPids') 'Multi-client launcher does not bind server readiness to the recorded server window.'
Assert-True ($all -match '\$_\.ProcessId\s+-in\s+\$clientOwnedPids') 'Multi-client launcher does not bind client discovery to the recorded client window.'
Assert-True ($all -match '\$stableAirgapClient\.ProcessId\s+-notin\s+\$stableClientOwnedPids') 'Multi-client client stability does not retain recorded client-window ancestry.'
Assert-True ($all -match '\$serverOwnerPid\s+-notin\s+\$stableServerOwnedPids') 'Multi-client server stability does not retain recorded server-window ancestry.'
Assert-True ($all -notmatch 'existingAirgapPids') 'Multi-client launcher still relies on PID-not-preexisting inference instead of exact client-root ancestry.'
Assert-True ($all -match 'AIRGAP_CLIENT_PROCESS_READY') 'Multi-client launcher does not prove the owned airgap Java process started.'
Assert-True ($all -match 'SERVER_PORTS_READY game=43594 aux=43595') 'Multi-client launcher does not require both game and AUX listeners before client launch.'
Assert-True ($all -match 'SERVER_PROCESS_READY') 'Multi-client launcher does not prove both listeners belong to the launcher-owned LocalLab server.'
Assert-True ($all -match 'AIRGAP_CLIENT_PROCESS_STABLE') 'Multi-client launcher does not require owned client stabilization.'
Assert-True ($all -match 'Start-Sleep -Seconds 2') 'Multi-client launcher lost the stabilization dwell.'
Assert-True ($all -match 'LOCAL_LAB_WINDOWS_STARTED_V521') 'Multi-client launcher lost its final healthy commit marker.'
Assert-True ($all -match 'Throw-LauncherFailureWithCleanup') 'Multi-client launcher does not surface combined launch/cleanup failure.'
Assert-True ($all -match 'cleanup was incomplete') 'Multi-client launcher does not report incomplete owned cleanup in the thrown result.'
Assert-True ($all -notmatch 'Write-Warning.*owned-process cleanup') 'Multi-client launcher still downgrades owned cleanup failure to a warning.'
Assert-True ($all -notmatch 'Stop-Process\s+-Name') 'Multi-client launcher reintroduced broad name-based process cleanup.'
Assert-True ($all -notmatch 'RUN_SERVER_LOCAL_WORLD\.ps1') 'Stale RUN_SERVER_LOCAL_WORLD.ps1 target remains.'
Assert-True ($all -match "'-File'") 'Child launchers are not using explicit PowerShell -File execution.'
Assert-True ($all -match 'AddSeconds\(30\)') 'Server-ready deadline is not the required 30-second window.'
Assert-True ($all -match 'R85 JAVA11\+ AUTOSELECT BEGIN') 'Compatibility selector marker was removed.'
Assert-True ($all -match '\$hadCallerJavaHome\s*=\s*Test-Path Env:JAVA_HOME') 'Multi-client launcher does not record caller JAVA_HOME ownership before selector mutation.'
Assert-True ($all -match '\$callerJavaHome\s*=\s*\$env:JAVA_HOME') 'Multi-client launcher does not snapshot caller JAVA_HOME.'
Assert-True ($all -match '\$callerPath\s*=\s*\$env:Path') 'Multi-client launcher does not snapshot caller PATH.'
Assert-True ($all -match 'finally\s*\{[\s\S]*\$env:Path\s*=\s*\$callerPath') 'Multi-client launcher does not restore caller PATH in outer finally.'
Assert-True ($all -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Multi-client launcher does not restore an originally absent JAVA_HOME.'
Assert-True ($all -notmatch '(?m)^Set-Location \$PSScriptRoot\s*$') 'Multi-client launcher retains an unowned top-level Set-Location.'
Assert-True ($all -match '\$callerLocationPushed\s*=\s*\$false') 'Multi-client launcher does not initialize caller-location ownership.'
Assert-True ($all -match 'Push-Location -LiteralPath \$PSScriptRoot') 'Multi-client launcher does not push repository working directory.'
Assert-True ($all -match '\$callerLocationPushed\s*=\s*\$true') 'Multi-client launcher does not record successful location push.'
Assert-True ($all -match 'if \(\$callerLocationPushed\)\s*\{\s*Pop-Location') 'Multi-client launcher does not guarantee caller-location restoration.'

foreach ($entry in @(
    @('function Get-NormalizedProcessLifetimeStamp', 1),
    @('function Get-LauncherOwnedProcessRecords', 1),
    @('function Get-LauncherOwnedProcessIds', 1),
    @('function Stop-LauncherOwnedProcessTree', 1),
    @('function Throw-LauncherFailureWithCleanup', 1),
    @('[switch]$IncludeExitedRoots', 2),
    @('Get-LauncherOwnedProcessRecords -Roots $Roots -Label $Label -IncludeExitedRoots', 2),
    @('$recordedRoots = @(', 1),
    @('$recordedRootPids = @(', 1),
    @('$liveRoots = @(', 1),
    @('$exitedRoots = @(', 1),
    @('$rootStart = [DateTime]$exitedRoot.StartTime', 1),
    @('$rootExit = [DateTime]$exitedRoot.ExitTime', 1),
    @('$childCreated = [DateTime]$directChild.CreationDate', 1),
    @('refused ambiguous exited-root PID reuse', 1),
    @('$hadCallerJavaHome = Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome = $env:JAVA_HOME', 1),
    @('$callerPath = $env:Path', 1),
    @('$env:Path = $callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1),
    @('$ready = $false', 1),
    @('$airgapClient = $null', 1),
    @('$ownedChildren = @()', 1),
    @('$serverWindow = Start-Process powershell.exe', 1),
    @('$watcherWindow = Start-Process powershell.exe', 1),
    @('$clientWindow = Start-Process powershell.exe', 1),
    @('$ownedChildren += $serverWindow', 1),
    @('$ownedChildren += $watcherWindow', 1),
    @('$ownedChildren += $clientWindow', 1),
    @('LOCAL_LAB_WINDOWS_STARTED_V521', 1),
    @('$admittedConflictStamp =', 1),
    @('$currentConflict = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue', 1),
    @('$currentConflictStamp =', 1),
    @('$currentConflictStamp -ne $admittedConflictStamp', 1),
    @('Stop-Process -InputObject $currentConflict -Force -ErrorAction Stop', 1),
    @('Stop-LauncherOwnedProcessTree -Roots $ownedChildren', 1),
    @('$callerLocationPushed = $false', 1),
    @('Push-Location -LiteralPath $PSScriptRoot', 1),
    @('$callerLocationPushed = $true', 1),
    @('Pop-Location', 1)
)) {
    Assert-ExactTextCount $all $entry[0] ([int]$entry[1]) 'Multi-client structural anchor count drift.'
}

$allRuntimeCheckIndex = $all.IndexOf('& $runtimeCheck')
$allServerJarPreflightIndex = $all.IndexOf('if (-not (Test-Path -LiteralPath $serverJar -PathType Leaf))')
$allLauncherPreflightIndex = $all.IndexOf('foreach ($required in @($serverScript,$watcherScript,$clientScript))')
$allReplacementPreflightPassIndex = $all.IndexOf('LOCAL_LAB_REPLACEMENT_PREFLIGHT_PASS')
$allConflictAdmittedIndex = $all.IndexOf('$admittedConflictStamp =')
$allConflictCurrentIndex = $all.IndexOf('$currentConflict = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue')
$allConflictMismatchIndex = $all.IndexOf('if ($currentConflictStamp -ne $admittedConflictStamp)')
$allStopIndex = $all.IndexOf('Stop-Process -InputObject $currentConflict -Force -ErrorAction Stop')
$allServerSpawnIndex = $all.IndexOf('$serverWindow = Start-Process powershell.exe')
$allWatcherSpawnIndex = $all.IndexOf('$watcherWindow = Start-Process powershell.exe')
$allClientSpawnIndex = $all.IndexOf('$clientWindow = Start-Process powershell.exe')
$allClientOwnedIndex = $all.IndexOf('Get-LauncherOwnedProcessIds -Roots @($clientWindow)')
$allHealthyIndex = $all.IndexOf('LOCAL_LAB_WINDOWS_STARTED_V521')
$allCleanupIndex = $all.IndexOf('Stop-LauncherOwnedProcessTree -Roots $ownedChildren')
$allCombinedThrowIndex = $all.LastIndexOf('Throw-LauncherFailureWithCleanup -PrimaryFailure')
Assert-True ($allRuntimeCheckIndex -ge 0) 'Multi-client external-runtime preflight invocation not found.'
Assert-True ($allServerJarPreflightIndex -gt $allRuntimeCheckIndex) 'Multi-client replacement server JAR is checked before external-runtime authority.'
Assert-True ($allLauncherPreflightIndex -gt $allServerJarPreflightIndex) 'Multi-client launcher components are not checked after server-JAR preflight.'
Assert-True ($allReplacementPreflightPassIndex -gt $allLauncherPreflightIndex) 'Multi-client replacement preflight marker is emitted before required launcher checks.'
Assert-True ($allConflictAdmittedIndex -gt $allReplacementPreflightPassIndex) 'Multi-client replacement admits conflicting-process lifetime before complete replacement preflight.'
Assert-True ($allConflictCurrentIndex -gt $allConflictAdmittedIndex) 'Multi-client replacement resolves current conflict process before admitted lifetime authority.'
Assert-True ($allConflictMismatchIndex -gt $allConflictCurrentIndex) 'Multi-client replacement lifetime mismatch fence precedes current process resolution.'
Assert-True ($allStopIndex -gt $allConflictMismatchIndex) 'Multi-client launcher may terminate a conflicting LocalLab before exact lifetime revalidation.'
Assert-True ($allServerSpawnIndex -gt $allStopIndex) 'Multi-client owned server starts before replacement conflict cleanup completes.'
Assert-True ($allWatcherSpawnIndex -gt $allServerSpawnIndex) 'Multi-client watcher spawn ordering is malformed.'
Assert-True ($allClientSpawnIndex -gt $allWatcherSpawnIndex) 'Multi-client client spawn ordering is malformed.'
Assert-True ($allClientOwnedIndex -gt $allClientSpawnIndex) 'Multi-client client ancestry is checked before its exact root is recorded.'
Assert-True ($allHealthyIndex -gt $allClientOwnedIndex) 'Multi-client healthy marker precedes owned client proof.'
Assert-True ($allCleanupIndex -gt $allHealthyIndex) 'Multi-client cleanup catch is not structurally after the healthy-path body.'
Assert-True ($allCombinedThrowIndex -gt $allCleanupIndex) 'Multi-client does not surface cleanup outcome after cleanup attempt.'
$allEnvCaptureIndex = $all.IndexOf('$callerPath = $env:Path')
$allJavaSelectIndex = $all.IndexOf('$__r85JavaInfo = Set-LocalLabJava')
$allEnvRestoreIndex = $all.LastIndexOf('$env:Path = $callerPath')
Assert-True ($allEnvCaptureIndex -ge 0 -and $allEnvCaptureIndex -lt $allJavaSelectIndex) 'Multi-client caller environment is not captured before canonical Java selection.'
Assert-True ($allEnvRestoreIndex -gt $allCombinedThrowIndex) 'Multi-client caller environment restoration does not structurally cover the full launcher flow.'
$allLocationPushIndex = $all.IndexOf('Push-Location -LiteralPath $PSScriptRoot')
$allLocationPopIndex = $all.LastIndexOf('Pop-Location')
Assert-True ($allLocationPushIndex -ge 0 -and $allLocationPushIndex -lt $allJavaSelectIndex) 'Multi-client repository location is not established before canonical Java selection.'
Assert-True ($allLocationPopIndex -gt $allCombinedThrowIndex) 'Multi-client caller location restoration does not structurally cover the complete launcher flow.'

foreach ($launcherSource in @($quick, $all)) {
    Assert-True ($launcherSource -match 'function Get-NormalizedProcessLifetimeStamp') 'Launcher has no shared deterministic lifetime normalization helper.'
    Assert-True ($launcherSource -match '\$ticksPerMicrosecond\s*=\s*10L') 'Launcher lifetime normalization is not pinned to CIM microsecond common precision.'
    Assert-True ($launcherSource -match '\$recordedStartUtc\s*=\s*\(\[DateTime\]\$liveRoot\.StartTime\)\.ToUniversalTime\(\)') 'Launcher live-root ownership does not bind to recorded Process.StartTime.'
    Assert-True ($launcherSource -match '\$currentMatches\s*=\s*@\(') 'Launcher live-root ownership does not resolve current CIM PID identity.'
    Assert-True ($launcherSource -match '\$currentMatches\.Count\s+-ne\s+1') 'Launcher live-root ownership does not require exactly one current PID match.'
    Assert-True ($launcherSource -match '\$currentCreatedUtc\s*=\s*\(\[DateTime\]\$currentMatches\[0\]\.CreationDate\)\.ToUniversalTime\(\)') 'Launcher live-root ownership does not bind current CIM CreationDate.'
    Assert-True ($launcherSource -match '\$recordedStartStamp\s*=') 'Launcher live-root ownership does not normalize recorded start identity.'
    Assert-True ($launcherSource -match '\$currentCreatedStamp\s*=') 'Launcher live-root ownership does not normalize current creation identity.'
    Assert-True ($launcherSource -match '\$recordedStartStamp\s+-ne\s+\$currentCreatedStamp') 'Launcher live-root ownership does not require exact normalized lifetime equality.'
    Assert-True ($launcherSource -notmatch 'liveRootStartDeltaSeconds') 'Launcher live-root ownership reintroduced generic time-delta identity.'
    Assert-True ($launcherSource -notmatch '-gt\s+2\.0') 'Launcher live-root ownership reintroduced the rejected two-second tolerance.'
    Assert-True ($launcherSource -match 'refused live-root PID lifetime mismatch') 'Launcher live-root ownership does not fail closed on PID lifetime mismatch.'
    Assert-True ($launcherSource -match '\$lifetimeStampByPid\[\$liveRootPid\]') 'Launcher does not retain normalized live-root lifetime authority.'

    foreach ($entry in @(
        @('function Get-NormalizedProcessLifetimeStamp', 1),
        @('$ticksPerMicrosecond = 10L', 1),
        @('$recordedStartUtc = ([DateTime]$liveRoot.StartTime).ToUniversalTime()', 1),
        @('$currentMatches = @(', 1),
        @('$currentMatches.Count -ne 1', 1),
        @('$currentCreatedUtc = ([DateTime]$currentMatches[0].CreationDate).ToUniversalTime()', 1),
        @('$recordedStartStamp =', 1),
        @('$currentCreatedStamp =', 1),
        @('$recordedStartStamp -ne $currentCreatedStamp', 1),
        @('refused live-root PID lifetime mismatch', 1),
        @('$depthByPid[$liveRootPid] = 0', 1),
        @('$lifetimeStampByPid[$liveRootPid] = [long]$currentCreatedStamp', 1)
    )) {
        Assert-ExactTextCount $launcherSource $entry[0] ([int]$entry[1]) 'Launcher live-root lifetime structural count drift.'
    }

    $liveRootStartIndex = $launcherSource.IndexOf('$recordedStartUtc = ([DateTime]$liveRoot.StartTime).ToUniversalTime()')
    $liveRootCurrentIndex = $launcherSource.IndexOf('$currentMatches = @(')
    $liveRootCreationIndex = $launcherSource.IndexOf('$currentCreatedUtc = ([DateTime]$currentMatches[0].CreationDate).ToUniversalTime()')
    $liveRootRecordedStampIndex = $launcherSource.IndexOf('$recordedStartStamp =')
    $liveRootCurrentStampIndex = $launcherSource.IndexOf('$currentCreatedStamp =')
    $liveRootMismatchIndex = $launcherSource.IndexOf('if ($recordedStartStamp -ne $currentCreatedStamp)')
    $liveRootSeedIndex = $launcherSource.IndexOf('$depthByPid[$liveRootPid] = 0')
    $liveRootLifetimeSeedIndex = $launcherSource.IndexOf('$lifetimeStampByPid[$liveRootPid] = [long]$currentCreatedStamp')
    Assert-True ($liveRootStartIndex -ge 0) 'Launcher recorded live-root start-time proof not found.'
    Assert-True ($liveRootCurrentIndex -gt $liveRootStartIndex) 'Launcher queries current live-root PID before recorded start identity.'
    Assert-True ($liveRootCreationIndex -gt $liveRootCurrentIndex) 'Launcher reads current CreationDate before unique current PID proof.'
    Assert-True ($liveRootRecordedStampIndex -gt $liveRootCreationIndex) 'Launcher normalizes recorded lifetime before current CreationDate is proven.'
    Assert-True ($liveRootCurrentStampIndex -gt $liveRootRecordedStampIndex) 'Launcher current lifetime normalization ordering is malformed.'
    Assert-True ($liveRootMismatchIndex -gt $liveRootCurrentStampIndex) 'Launcher lifetime mismatch fence precedes exact normalized identity construction.'
    Assert-True ($liveRootSeedIndex -gt $liveRootMismatchIndex) 'Launcher grants depth-0 ancestry authority before exact live-root lifetime identity is proven.'
    Assert-True ($liveRootLifetimeSeedIndex -gt $liveRootSeedIndex) 'Launcher does not persist live-root lifetime authority with ancestry authority.'
}

foreach ($launcherSource in @($quick, $all)) {
    Assert-True ($launcherSource -match '\$cleanupDeadline\s*=\s*\(Get-Date\)\.AddSeconds\(5\)') 'Launcher cleanup lost bounded five-second convergence deadline.'
    Assert-True ($launcherSource -match '\$cleanupMaxPasses\s*=\s*8') 'Launcher cleanup lost bounded eight-pass convergence limit.'
    Assert-True ($launcherSource -match 'while \(\$cleanupPass -lt \$cleanupMaxPasses') 'Launcher cleanup does not repeatedly rescan owned process authority.'
    Assert-True ($launcherSource -match 'Get-LauncherOwnedProcessRecords -Roots \$Roots -Label \$Label -IncludeExitedRoots') 'Launcher destructive cleanup does not consume lifetime-bearing ownership records.'
    Assert-True ($launcherSource -match '\$ownedProcess\.LifetimeStamp') 'Launcher destructive cleanup does not carry discovered lifetime identity.'
    Assert-True ($launcherSource -match 'Get-NormalizedProcessLifetimeStamp -Timestamp \(\[DateTime\]\$live\.StartTime\)') 'Launcher cleanup does not re-read current process lifetime immediately before termination.'
    Assert-True ($launcherSource -match '\$liveStartStamp\s+-ne\s+\[long\]\$ownedProcess\.LifetimeStamp') 'Launcher cleanup does not reject stop-time PID lifetime reuse.'
    Assert-True ($launcherSource -match 'Stop-Process -InputObject \$live -Force') 'Launcher cleanup does not terminate the exact revalidated process object.'
    Assert-True ($launcherSource -notmatch 'Stop-Process -Id \$ownedPid') 'Launcher cleanup still grants destructive authority to a bare PID.'
    Assert-True ($launcherSource -match '\$ownedProcesses\.Count\s+-eq\s+0') 'Launcher cleanup has no zero-owned convergence condition.'
    Assert-True ($launcherSource -match '\$remainingLive\.Count\s+-ne\s+0') 'Launcher cleanup does not fail after bounded exhaustion with remaining exact-lifetime processes.'
    Assert-True ($launcherSource -match 'launcher-owned cleanup did not converge') 'Launcher cleanup does not surface bounded convergence failure.'
    Assert-True ($launcherSource -notmatch 'Stop-Process\s+-Name') 'Launcher convergent cleanup reintroduced broad name-based termination.'

    foreach ($entry in @(
        @('$cleanupDeadline = (Get-Date).AddSeconds(5)', 1),
        @('$cleanupMaxPasses = 8', 1),
        @('$cleanupPass++', 1),
        @('$ownedProcesses = @(', 1),
        @('$remainingOwnedProcesses = @(', 1),
        @('Get-LauncherOwnedProcessRecords -Roots $Roots -Label $Label -IncludeExitedRoots', 2),
        @('Stop-Process -InputObject $live -Force -ErrorAction Stop', 1),
        @('refused cleanup PID lifetime mismatch', 1),
        @('refused final cleanup PID lifetime mismatch', 1),
        @('launcher-owned cleanup did not converge', 1),
        @('Start-Sleep -Milliseconds 100', 1)
    )) {
        Assert-ExactTextCount $launcherSource $entry[0] ([int]$entry[1]) 'Launcher cleanup convergence/lifetime structural count drift.'
    }

    $cleanupLoopIndex = $launcherSource.IndexOf('while ($cleanupPass -lt $cleanupMaxPasses')
    $cleanupRescanIndex = $launcherSource.IndexOf('Get-LauncherOwnedProcessRecords -Roots $Roots -Label $Label -IncludeExitedRoots', $cleanupLoopIndex)
    $cleanupLifetimeReadIndex = $launcherSource.IndexOf('Get-NormalizedProcessLifetimeStamp -Timestamp ([DateTime]$live.StartTime)', $cleanupRescanIndex)
    $cleanupLifetimeMismatchIndex = $launcherSource.IndexOf('if ($liveStartStamp -ne [long]$ownedProcess.LifetimeStamp)', $cleanupLifetimeReadIndex)
    $cleanupStopIndex = $launcherSource.IndexOf('Stop-Process -InputObject $live -Force -ErrorAction Stop', $cleanupLifetimeMismatchIndex)
    $cleanupFinalRescanIndex = $launcherSource.LastIndexOf('Get-LauncherOwnedProcessRecords -Roots $Roots -Label $Label -IncludeExitedRoots')
    $cleanupRemainingCheckIndex = $launcherSource.IndexOf('if ($remainingLive.Count -ne 0)')
    $cleanupCompleteIndex = $launcherSource.LastIndexOf('LOCALLAB_OWNED_PROCESS_CLEANUP_COMPLETE')
    Assert-True ($cleanupLoopIndex -ge 0) 'Launcher cleanup convergence loop not found.'
    Assert-True ($cleanupRescanIndex -gt $cleanupLoopIndex) 'Launcher cleanup does not rescan lifetime-bearing ownership inside bounded loop.'
    Assert-True ($cleanupLifetimeReadIndex -gt $cleanupRescanIndex) 'Launcher cleanup does not re-read current lifetime after ownership discovery.'
    Assert-True ($cleanupLifetimeMismatchIndex -gt $cleanupLifetimeReadIndex) 'Launcher cleanup stop-time lifetime mismatch fence is ordered before current lifetime read.'
    Assert-True ($cleanupStopIndex -gt $cleanupLifetimeMismatchIndex) 'Launcher cleanup can terminate before exact lifetime revalidation.'
    Assert-True ($cleanupFinalRescanIndex -gt $cleanupStopIndex) 'Launcher cleanup has no final ownership rescan after bounded termination passes.'
    Assert-True ($cleanupRemainingCheckIndex -gt $cleanupFinalRescanIndex) 'Launcher cleanup checks exhaustion before final lifetime-bound ownership rescan.'
    Assert-True ($cleanupCompleteIndex -gt $cleanupRemainingCheckIndex) 'Launcher cleanup can emit final completion before zero-owned exhaustion proof.'
}

Assert-True ($bootstrap -match '\[switch\]\$SkipConfigPatch') 'Bootstrap no longer preserves the legacy -SkipConfigPatch compatibility switch.'
Assert-True ($bootstrap -match 'BOOTSTRAP_CONFIG_PATCH_RETIRED') 'Bootstrap does not state that live config mutation is retired.'
Assert-True ($bootstrap -match 'isolatedCachePipelineRequired=true') 'Bootstrap does not point custom-cache work to isolated authority.'
Assert-True ($bootstrap -notmatch [regex]::Escape('scripts\Patch-LocalConfigs.ps1')) 'Bootstrap reintroduced the retired live config patch script.'
Assert-True ($bootstrap -match '\$hadCallerJavaHome\s*=\s*Test-Path Env:JAVA_HOME') 'Bootstrap does not record whether caller JAVA_HOME existed.'
Assert-True ($bootstrap -match '\$callerJavaHome\s*=\s*\$env:JAVA_HOME') 'Bootstrap does not snapshot caller JAVA_HOME.'
Assert-True ($bootstrap -match '\$callerPath\s*=\s*\$env:Path') 'Bootstrap does not snapshot caller PATH.'
Assert-True ($bootstrap -match 'finally\s*\{[\s\S]*\$env:Path\s*=\s*\$callerPath') 'Bootstrap does not restore caller PATH in outer finally.'
Assert-True ($bootstrap -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Bootstrap does not restore an originally absent JAVA_HOME.'
Assert-True ($bootstrap -match [regex]::Escape('scripts\Build-Server.ps1')) 'Bootstrap no longer invokes the canonical build wrapper.'
Assert-True ($bootstrap -match 'RUN_REPO_SELFTEST\.ps1') 'Bootstrap no longer invokes the repository selftest path when enabled.'

foreach ($entry in @(
    @('$hadCallerJavaHome = Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome = $env:JAVA_HOME', 1),
    @('$callerPath = $env:Path', 1),
    @('$env:Path = $callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1)
)) {
    Assert-ExactTextCount $bootstrap $entry[0] ([int]$entry[1]) 'Bootstrap Java-environment ownership count drift.'
}

$bootstrapEnvCaptureIndex = $bootstrap.IndexOf('$callerPath = $env:Path')
$bootstrapJavaIndex = $bootstrap.IndexOf('$java = Set-LocalLabJava')
$bootstrapBuildIndex = $bootstrap.IndexOf("scripts\Build-Server.ps1")
$bootstrapSelftestIndex = $bootstrap.IndexOf('RUN_REPO_SELFTEST.ps1')
$bootstrapCompleteIndex = $bootstrap.IndexOf('BOOTSTRAP_COMPLETE')
$bootstrapEnvRestoreIndex = $bootstrap.LastIndexOf('$env:Path = $callerPath')
Assert-True ($bootstrapEnvCaptureIndex -ge 0 -and $bootstrapEnvCaptureIndex -lt $bootstrapJavaIndex) 'Bootstrap caller environment is not captured before runtime Java selection.'
Assert-True ($bootstrapBuildIndex -gt $bootstrapJavaIndex) 'Bootstrap build does not execute under the selected runtime environment boundary.'
Assert-True ($bootstrapSelftestIndex -gt $bootstrapJavaIndex) 'Bootstrap selftest path is not structurally inside the selected runtime environment boundary.'
Assert-True ($bootstrapCompleteIndex -gt $bootstrapJavaIndex) 'Bootstrap completion marker precedes runtime Java selection.'
Assert-True ($bootstrapEnvRestoreIndex -gt $bootstrapCompleteIndex) 'Bootstrap caller environment is restored before the complete bootstrap/selftest flow ends.'

Assert-True ($configPatch -match 'LOCALLAB_CONFIG_PATCH_RETIRED') 'Retired config-patch shim lost its fail-closed marker.'
Assert-True ($configPatch -match 'isolatedCachePipelineRequired=true') 'Retired config-patch shim does not require isolated cache authority.'
Assert-True ($configPatch -match [regex]::Escape('scripts\Run-R13AssetAcceptance.ps1')) 'Retired config-patch shim does not point to canonical R13 acceptance tooling.'
Assert-True ($configPatch -match 'build_r13_isolated_profile\.py') 'Retired config-patch shim does not point to the isolated profile builder.'
Assert-True ($configPatch -notmatch 'VoidglassR3ConfigPatchTool') 'Retired config-patch shim still invokes the obsolete Java mutator.'
Assert-True ($configPatch -notmatch 'Copy-Item') 'Retired config-patch shim still copies live config bytes.'
Assert-True ($configPatch -notmatch '&\s+\$java\.Path') 'Retired config-patch shim still launches Java mutation tooling.'

Assert-True ($buildServer -match 'Select-LocalLabBuildJava\.ps1') 'Build-Server does not use canonical JDK-21 selector.'
Assert-True ($buildServer -match 'Set-LocalLabBuildJava') 'Build-Server does not invoke canonical build-JDK selection.'
Assert-True ($buildServer -match '\$hadCallerJavaHome\s*=\s*Test-Path Env:JAVA_HOME') 'Build-Server does not record caller JAVA_HOME ownership.'
Assert-True ($buildServer -match '\$callerJavaHome\s*=\s*\$env:JAVA_HOME') 'Build-Server does not snapshot caller JAVA_HOME.'
Assert-True ($buildServer -match '\$callerPath\s*=\s*\$env:Path') 'Build-Server does not snapshot caller PATH.'
Assert-True ($buildServer -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Build-Server does not restore an originally absent JAVA_HOME.'
Assert-True ($buildServer -match 'SERVER_BUILD_OK') 'Build-Server lost its final build artifact/SHA success boundary.'

foreach ($entry in @(
    @('$hadCallerJavaHome = Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome = $env:JAVA_HOME', 1),
    @('$callerPath = $env:Path', 1),
    @('$env:Path = $callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1)
)) {
    Assert-ExactTextCount $buildServer $entry[0] ([int]$entry[1]) 'Build-Server Java-environment ownership count drift.'
}

$buildEnvCaptureIndex = $buildServer.IndexOf('$callerPath = $env:Path')
$buildJavaIndex = $buildServer.IndexOf('$buildJava = Set-LocalLabBuildJava')
$buildInvokeIndex = $buildServer.IndexOf('& $build')
$buildSuccessIndex = $buildServer.IndexOf('SERVER_BUILD_OK')
$buildEnvRestoreIndex = $buildServer.LastIndexOf('$env:Path = $callerPath')
Assert-True ($buildEnvCaptureIndex -ge 0 -and $buildEnvCaptureIndex -lt $buildJavaIndex) 'Build-Server caller environment is not captured before JDK-21 selection.'
Assert-True ($buildInvokeIndex -gt $buildJavaIndex) 'Build-Server actual build does not execute under selected JDK-21 environment.'
Assert-True ($buildSuccessIndex -gt $buildInvokeIndex) 'Build-Server success boundary precedes actual build.'
Assert-True ($buildEnvRestoreIndex -gt $buildSuccessIndex) 'Build-Server restores caller Java environment before artifact/SHA validation completes.'

Assert-True ($repoSelftest -match 'RUN_V5185_FULL_SELFTEST\.ps1') 'Repository selftest no longer delegates to the historical full selftest harness.'
Assert-True ($repoSelftest -notmatch 'Set-R85Java11Plus') 'Repository selftest should not add an independent historical Java selector mutation.'
Assert-True ($fullSelftest -match 'R85_SelectJava11Plus\.ps1') 'Historical full selftest lost its Java-11+ selector.'
Assert-True ($fullSelftest -match 'Set-R85Java11Plus') 'Historical full selftest does not invoke the Java-11+ selector.'
Assert-True ($fullSelftest -match '\$hadCallerJavaHome=Test-Path Env:JAVA_HOME') 'Historical full selftest does not record caller JAVA_HOME ownership.'
Assert-True ($fullSelftest -match '\$callerJavaHome=\$env:JAVA_HOME') 'Historical full selftest does not snapshot caller JAVA_HOME.'
Assert-True ($fullSelftest -match '\$callerPath=\$env:Path') 'Historical full selftest does not snapshot caller PATH.'
Assert-True ($fullSelftest -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Historical full selftest does not restore an originally absent JAVA_HOME.'
Assert-True ($fullSelftest -match 'V5185_FULL_SELFTEST_PASS count=179') 'Historical full selftest lost the exact 179-test success boundary.'

foreach ($entry in @(
    @('$hadCallerJavaHome=Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome=$env:JAVA_HOME', 1),
    @('$callerPath=$env:Path', 1),
    @('$env:Path=$callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1)
)) {
    Assert-ExactTextCount $fullSelftest $entry[0] ([int]$entry[1]) 'Historical selftest Java-environment ownership count drift.'
}

$selftestEnvCaptureIndex = $fullSelftest.IndexOf('$callerPath=$env:Path')
$selftestJavaIndex = $fullSelftest.IndexOf('$javaInfo=Set-R85Java11Plus')
$selftestPassIndex = $fullSelftest.IndexOf('V5185_FULL_SELFTEST_PASS count=179')
$selftestEnvRestoreIndex = $fullSelftest.LastIndexOf('$env:Path=$callerPath')
Assert-True ($selftestEnvCaptureIndex -ge 0 -and $selftestEnvCaptureIndex -lt $selftestJavaIndex) 'Historical selftest caller environment is not captured before Java-11+ selection.'
Assert-True ($selftestPassIndex -gt $selftestJavaIndex) 'Historical selftest success boundary precedes Java selection/test execution.'
Assert-True ($selftestEnvRestoreIndex -gt $selftestPassIndex) 'Historical selftest restores caller Java environment before the complete 179-test run ends.'

Assert-True ($offlineReady -match 'R85_SelectJava11Plus\.ps1') 'Historical offline verifier lost its Java-11+ selector.'
Assert-True ($offlineReady -match 'Set-R85Java11Plus') 'Historical offline verifier does not invoke the Java-11+ selector.'
Assert-True ($offlineReady -match '\$hadCallerJavaHome=Test-Path Env:JAVA_HOME') 'Historical offline verifier does not record caller JAVA_HOME ownership.'
Assert-True ($offlineReady -match '\$callerJavaHome=\$env:JAVA_HOME') 'Historical offline verifier does not snapshot caller JAVA_HOME.'
Assert-True ($offlineReady -match '\$callerPath=\$env:Path') 'Historical offline verifier does not snapshot caller PATH.'
Assert-True ($offlineReady -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Historical offline verifier does not restore an originally absent JAVA_HOME.'
Assert-True ($offlineReady -match 'V5185_ENGINE_R85_OFFLINE_READY_PASS') 'Historical offline verifier lost its final readiness boundary.'
foreach ($entry in @(
    @('$hadCallerJavaHome=Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome=$env:JAVA_HOME', 1),
    @('$callerPath=$env:Path', 1),
    @('$env:Path=$callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1)
)) {
    Assert-ExactTextCount $offlineReady $entry[0] ([int]$entry[1]) 'Historical offline verifier Java-environment ownership count drift.'
}
$offlineEnvCaptureIndex = $offlineReady.IndexOf('$callerPath=$env:Path')
$offlineJavaIndex = $offlineReady.IndexOf('$javaInfo=Set-R85Java11Plus')
$offlinePassIndex = $offlineReady.IndexOf('V5185_ENGINE_R85_OFFLINE_READY_PASS')
$offlineEnvRestoreIndex = $offlineReady.LastIndexOf('$env:Path=$callerPath')
Assert-True ($offlineEnvCaptureIndex -ge 0 -and $offlineEnvCaptureIndex -lt $offlineJavaIndex) 'Historical offline verifier caller environment is not captured before Java-11+ selection.'
Assert-True ($offlinePassIndex -gt $offlineJavaIndex) 'Historical offline verifier readiness boundary precedes Java-dependent verification.'
Assert-True ($offlineEnvRestoreIndex -gt $offlinePassIndex) 'Historical offline verifier restores caller Java environment before readiness reporting completes.'
Assert-True ($serverWrapper -match 'Select-LocalLabJava\.ps1') 'Server wrapper is not using the canonical Java selector.'
Assert-True ($serverWrapper -match [regex]::Escape('server\build\SpawnPKLocalServer.jar')) 'Server wrapper does not target the current built server JAR.'
Assert-True ($serverWrapper -match 'Test-Path\s+-LiteralPath\s+\$required\s+-PathType\s+Leaf') 'Server wrapper does not fail closed on missing launch components.'
Assert-True ($serverWrapper -match '&\s+\$java\.Path\s+-jar\s+\$jar\s+--bootstrap\s+--movement') 'Server wrapper does not launch through the selected Java path with canonical server arguments.'
Assert-True ($serverWrapper -match '\$serverExit\s*=\s*\$LASTEXITCODE') 'Server wrapper does not capture the native Java exit code immediately.'
Assert-True ($serverWrapper -match '\$serverExit\s+-ne\s+0') 'Server wrapper does not fail on nonzero Java exit.'
Assert-True ($serverWrapper -match '\$hadCallerJavaHome\s*=\s*Test-Path Env:JAVA_HOME') 'Server wrapper does not record caller JAVA_HOME ownership.'
Assert-True ($serverWrapper -match '\$callerJavaHome\s*=\s*\$env:JAVA_HOME') 'Server wrapper does not snapshot caller JAVA_HOME.'
Assert-True ($serverWrapper -match '\$callerPath\s*=\s*\$env:Path') 'Server wrapper does not snapshot caller PATH.'
Assert-True ($serverWrapper -match 'Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue') 'Server wrapper does not restore an originally absent JAVA_HOME.'
foreach ($entry in @(
    @('$hadCallerJavaHome = Test-Path Env:JAVA_HOME', 1),
    @('$callerJavaHome = $env:JAVA_HOME', 1),
    @('$callerPath = $env:Path', 1),
    @('$env:Path = $callerPath', 1),
    @('Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue', 1)
)) {
    Assert-ExactTextCount $serverWrapper $entry[0] ([int]$entry[1]) 'Server wrapper Java-environment ownership count drift.'
}


$serverEnvCaptureIndex = $serverWrapper.IndexOf('$callerPath = $env:Path')
$serverJavaIndex = $serverWrapper.IndexOf('$java = Set-LocalLabJava')
$serverLaunchIndex = $serverWrapper.IndexOf('& $java.Path -jar $jar --bootstrap --movement')
$serverExitCaptureIndex = $serverWrapper.IndexOf('$serverExit = $LASTEXITCODE')
$serverExitCheckIndex = $serverWrapper.IndexOf('if ($serverExit -ne 0)')
$serverEnvRestoreIndex = $serverWrapper.LastIndexOf('$env:Path = $callerPath')
Assert-True ($serverEnvCaptureIndex -ge 0 -and $serverEnvCaptureIndex -lt $serverJavaIndex) 'Server wrapper caller environment is not captured before canonical Java selection.'
Assert-True ($serverLaunchIndex -gt $serverJavaIndex) 'Server wrapper native launch does not execute under selected canonical Java.'
Assert-True ($serverExitCaptureIndex -gt $serverLaunchIndex) 'Server wrapper does not capture native exit after launch.'
Assert-True ($serverExitCheckIndex -gt $serverExitCaptureIndex) 'Server wrapper checks exit status before capture or not at all.'
Assert-True ($serverEnvRestoreIndex -gt $serverExitCheckIndex) 'Server wrapper restores caller Java environment before native exit handling completes.'

Assert-True ($clientWrapper -match 'Select-LocalLabJava\.ps1') 'Client wrapper is not using the canonical Java selector.'
Assert-True ($selector -match 'Major -eq 17') 'Canonical selector no longer prefers the proven Java 17 runtime.'

Assert-True ($ignore -match '(?m)^\*\.log\s*$') '*.log is not ignored.'
Assert-True ($ignore -match '(?m)^\*\.lock\s*$') '*.lock is not ignored.'
Assert-True ($ignore -match '(?m)^\*\.pid\s*$') '*.pid is not ignored.'
Assert-True ($ignore -match '(?m)^runtime/locallab-user-home/\s*$') 'LocalLab isolated user.home runtime tree is not ignored.'

if (-not $SkipJavaProbe) {
    $probeHadJavaHome = Test-Path Env:JAVA_HOME
    $probeJavaHome = $env:JAVA_HOME
    $probePath = $env:Path
    try {
        . (Join-Path $repo 'scripts\Select-LocalLabJava.ps1')
        $java = Set-LocalLabJava
        Assert-True ($null -ne $java) 'Canonical selector returned no Java runtime.'
        Assert-True ([int]$java.Major -ge 11) "Selected Java is below 11: $($java.Major)"
        Assert-True (Test-Path -LiteralPath $java.Path -PathType Leaf) "Selected Java path is missing: $($java.Path)"
    }
    finally {
        if ($probeHadJavaHome) {
            $env:JAVA_HOME = $probeJavaHome
        }
        else {
            Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        }
        $env:Path = $probePath
    }
}

Write-Host 'LOCALLAB_LAUNCHER_CONTRACT_PASS' -ForegroundColor Green
