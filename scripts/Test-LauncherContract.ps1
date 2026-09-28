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
    'RUN_SECOND_LOCAL_CLIENT.ps1',
    'RUN_CLIENT_LOCALHOST_NONAIRGAP.ps1',
    'RUN_ALL_LOCAL_LAB.ps1',
    'RUN_LOCAL_LAB.ps1',
    'RUN_CURRENT_RELEASE_ACCEPTANCE.ps1',
    'IMPORT_EXISTING_RUNTIME.ps1',
    'BOOTSTRAP.ps1',
    'scripts\Patch-LocalConfigs.ps1',
    'WATCH_CLIENT_NETWORK.ps1',
    'scripts\Run-Server.ps1',
    'scripts\Run-Client-Airgap.ps1',
    'scripts\Select-LocalLabJava.ps1',
    'scripts\Check-ExternalRuntime.ps1',
    'scripts\Build-V308LocalClients.ps1',
    'scripts\Run-R13AssetAcceptance.ps1'
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

Assert-True ($client -match [regex]::Escape('scripts\Check-ExternalRuntime.ps1')) 'Standalone airgap launcher does not verify current exact-v308 external-runtime authority.'
Assert-True ($client -match 'Missing LocalLab external-runtime verifier') 'Standalone airgap launcher does not fail closed when the runtime verifier is missing.'
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

$standaloneRuntimeCheckIndex = $client.IndexOf('& $runtimeCheck')
$standaloneJarIndex = $client.IndexOf('$jar = Join-Path $PSScriptRoot ''local-client\client-airgap.jar''')
$standaloneLaunchIndex = $client.IndexOf('& $java.Path @javaArgs -jar $jar')
Assert-True ($standaloneRuntimeCheckIndex -ge 0) 'Standalone airgap external-runtime verification invocation not found.'
Assert-True ($standaloneJarIndex -gt $standaloneRuntimeCheckIndex) 'Standalone airgap client path is admitted before external-runtime verification.'
Assert-True ($standaloneLaunchIndex -gt $standaloneRuntimeCheckIndex) 'Standalone airgap client launches before exact-v308 external-runtime verification.'

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

$secondDefaultIndex = $secondClient.IndexOf('$launcher = $airgapLauncher')
$secondOptInIndex = $secondClient.IndexOf('if ($AllowNonAirgap)')
$secondNonAirgapAssignIndex = $secondClient.IndexOf('$launcher = $nonAirgapLauncher')
$secondChildOptInIndex = $secondClient.IndexOf('& $launcher -AllowExternalEndpoints')
Assert-True ($secondDefaultIndex -ge 0) 'Second-client launcher has no explicit airgap default assignment.'
Assert-True ($secondOptInIndex -gt $secondDefaultIndex) 'Second-client nonairgap opt-in is not applied after the airgap default.'
Assert-True ($secondNonAirgapAssignIndex -gt $secondOptInIndex) 'Second-client nonairgap launcher is not confined to the explicit opt-in branch.'
Assert-True ($secondChildOptInIndex -gt $secondOptInIndex) 'Second-client parent does not pass child nonairgap consent only after explicit opt-in.'

Assert-True ($nonAirgap -match '\[switch\]\$AllowExternalEndpoints') 'Direct nonairgap launcher does not require explicit -AllowExternalEndpoints consent.'
Assert-True ($nonAirgap -match 'if\s*\(\s*-not\s+\$AllowExternalEndpoints\s*\)') 'Direct nonairgap launcher does not fail closed without external-endpoint consent.'
Assert-True ($nonAirgap -match [regex]::Escape('scripts\Select-LocalLabJava.ps1')) 'Explicit nonairgap launcher does not use the canonical Java selector.'
Assert-True ($nonAirgap -match [regex]::Escape('scripts\Check-ExternalRuntime.ps1')) 'Explicit nonairgap launcher does not verify current external-runtime authority.'
Assert-True ($nonAirgap -match 'Set-LocalLabJava') 'Explicit nonairgap launcher does not invoke canonical Java selection.'
Assert-True ($nonAirgap -match '&\s+\$java\.Path\s+-jar\s+\$jar') 'Explicit nonairgap launcher does not invoke the selected Java executable.'
Assert-True ($nonAirgap -match '\$LASTEXITCODE\s+-ne\s+0') 'Explicit nonairgap launcher does not fail on a nonzero client exit.'
Assert-True ($nonAirgap -match 'NONAIRGAP_DIAGNOSTIC_EXPLICIT') 'Explicit nonairgap launcher lost its external-endpoint warning marker.'
Assert-True ($nonAirgap -notmatch '&\s+java\s+-jar') 'Explicit nonairgap launcher reintroduced bare PATH Java execution.'

$nonAirgapConsentIndex = $nonAirgap.IndexOf('if (-not $AllowExternalEndpoints)')
$nonAirgapSelectorIndex = $nonAirgap.IndexOf('$selector = Join-Path')
$nonAirgapLaunchIndex = $nonAirgap.IndexOf('& $java.Path -jar $jar')
Assert-True ($nonAirgapConsentIndex -ge 0) 'Direct nonairgap consent gate not found.'
Assert-True ($nonAirgapSelectorIndex -gt $nonAirgapConsentIndex) 'Direct nonairgap launcher performs setup before explicit consent.'
Assert-True ($nonAirgapLaunchIndex -gt $nonAirgapSelectorIndex) 'Direct nonairgap launch ordering is malformed.'

Assert-True ($quick -match [regex]::Escape('scripts\Check-ExternalRuntime.ps1')) 'Quick-start launcher does not use current external-runtime preflight.'
Assert-True ($quick -match [regex]::Escape('server\build\SpawnPKLocalServer.jar')) 'Quick-start launcher does not preflight the current built server JAR.'
Assert-True ($quick -match [regex]::Escape('scripts\Run-Server.ps1')) 'Quick-start launcher does not use the canonical server wrapper.'
Assert-True ($quick -match [regex]::Escape('scripts\Run-Client-Airgap.ps1')) 'Quick-start launcher does not use the canonical airgap client wrapper.'
Assert-True ($quick -match [regex]::Escape('$ports = 43594, 43595')) 'Quick-start launcher does not track both game and AUX ports.'
Assert-True ($quick -match [regex]::Escape('$readyPorts -contains 43594')) 'Quick-start launcher does not require game-port readiness.'
Assert-True ($quick -match [regex]::Escape('$readyPorts -contains 43595')) 'Quick-start launcher does not require AUX-port readiness.'
Assert-True ($quick -match [regex]::Escape('$readyOwnerPids.Count -gt 1')) 'Quick-start launcher does not fail closed on multiple listener owners.'
Assert-True ($quick -match 'Get-CimInstance\s+Win32_Process') 'Quick-start launcher does not inspect listener process identity.'
Assert-True ($quick -match 'SpawnPKLocalServer\|spk\\\.local\\\.Main\|SpawnPK-LocalLab') 'Quick-start launcher does not require expected SpawnPK LocalLab server identity.'
Assert-True ($quick -match 'AddSeconds\(30\)') 'Quick-start launcher lost the bounded 30-second server readiness window.'
Assert-True ($quick -match 'QUICKSTART_SERVER_PROCESS_READY') 'Quick-start launcher does not report proven server process readiness.'
Assert-True ($quick -match 'QUICKSTART_SERVER_PORTS_READY game=43594 aux=43595') 'Quick-start launcher does not report dual-port readiness.'

$quickServerJarIndex = $quick.IndexOf('$serverJar = Join-Path')
$quickStartIndex = $quick.IndexOf('Start-Process powershell.exe')
$quickDualReadyIndex = $quick.IndexOf('$readyPorts -contains 43595')
$quickClientLaunchIndex = $quick.IndexOf('& $clientScript')
Assert-True ($quickServerJarIndex -ge 0 -and $quickServerJarIndex -lt $quickStartIndex) 'Quick-start server-JAR preflight is not established before child launch.'
Assert-True ($quickStartIndex -ge 0) 'Quick-start child server launch not found.'
Assert-True ($quickDualReadyIndex -gt $quickStartIndex) 'Quick-start dual-port proof does not occur after server launch.'
Assert-True ($quickClientLaunchIndex -gt $quickDualReadyIndex) 'Quick-start launches the airgap client before dual-port server readiness is proven.'

Assert-True ($externalRuntime -match '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6') 'External runtime no longer pins exact v308 evidence client.'
Assert-True ($externalRuntime -match '83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33') 'External runtime no longer pins exact v308 airgap client.'
Assert-True ($externalRuntime -match '01c878a56ee25fb112dfe8b459dbd11ea26cfa8a92a7f287a4e5ee53f673cdbd') 'External runtime no longer pins exact v308 localhost client.'
Assert-True ($externalRuntime -match 'coherentTriplet=true') 'External runtime does not report coherent v308 triplet authority.'
Assert-True ($runtimeImport -match '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6') 'Runtime importer no longer requires exact v308 evidence client.'
Assert-True ($runtimeImport -match '83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33') 'Runtime importer no longer requires exact v308 airgap client.'
Assert-True ($runtimeImport -match '01c878a56ee25fb112dfe8b459dbd11ea26cfa8a92a7f287a4e5ee53f673cdbd') 'Runtime importer no longer requires exact v308 localhost client.'

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

$runtimeImportPreflightIndex = $runtimeImport.IndexOf('EXTERNAL_RUNTIME_IMPORT_PREFLIGHT_PASS')
$runtimeImportStageIndex = $runtimeImport.IndexOf('RUNTIME_IMPORT_STAGE_VERIFIED')
$runtimeImportBackupIndex = $runtimeImport.IndexOf('RUNTIME_IMPORT_BACKUP_READY')
$runtimeImportTouchIndex = $runtimeImport.IndexOf('$touched.Add($record)')
$runtimeImportPublishIndex = $runtimeImport.IndexOf('Copy-Item -LiteralPath $record.Stage -Destination $record.Destination -Force')
$runtimeImportVerifyIndex = $runtimeImport.IndexOf('RUNTIME_IMPORT_FINAL_VERIFY_PASS')
$runtimeImportCatchIndex = $runtimeImport.IndexOf('catch {', $runtimeImportPublishIndex)
$runtimeImportFinalIndex = $runtimeImport.IndexOf('EXTERNAL_RUNTIME_IMPORT_PASS')
Assert-True ($runtimeImportPreflightIndex -ge 0) 'Runtime importer source preflight marker not found.'
Assert-True ($runtimeImportStageIndex -gt $runtimeImportPreflightIndex) 'Runtime importer stages before proving the complete source triplet.'
Assert-True ($runtimeImportBackupIndex -gt $runtimeImportStageIndex) 'Runtime importer snapshots destinations before all staged artifacts are verified.'
Assert-True ($runtimeImportTouchIndex -gt $runtimeImportBackupIndex) 'Runtime importer acquires destination mutation ownership before all backups exist.'
Assert-True ($runtimeImportPublishIndex -gt $runtimeImportTouchIndex) 'Runtime importer writes a destination before rollback ownership is recorded.'
Assert-True ($runtimeImportVerifyIndex -gt $runtimeImportPublishIndex) 'Runtime importer final verification does not follow destination publication.'
Assert-True ($runtimeImportCatchIndex -gt $runtimeImportVerifyIndex) 'Runtime importer final verification escaped rollback ownership.'
Assert-True ($runtimeImportFinalIndex -gt $runtimeImportVerifyIndex) 'Runtime importer reports success before final whole-triplet verification.'
Assert-True ($runtimeBuilder -match 'build_v308_local_clients\.py') 'PowerShell runtime builder does not invoke the deterministic v308 patcher.'
Assert-True ($runtimeBuilder -match 'Check-ExternalRuntime\.ps1') 'PowerShell runtime builder does not verify the rebuilt exact-v308 triplet.'
Assert-True ($runtimeBuilder -match 'V308_LOCAL_CLIENT_BUILD_AND_VERIFY_PASS') 'PowerShell runtime builder marker missing.'
Assert-True ($runtimeBuilder -match 'OutputDirectory must be the canonical LocalLab runtime directory') 'PowerShell runtime builder accepts an unverifiable noncanonical output directory.'
Assert-True ($runtimeBuilder -match 'ClientJar must be the canonical evidence path') 'PowerShell runtime builder accepts a client path that permanent runtime verification cannot prove.'
Assert-True ($runtimeBuilder -match '854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6') 'PowerShell runtime builder no longer independently gates the exact v308 input SHA.'

Assert-True ($runtimeBuilder -match 'V308_LOCAL_CLIENT_BUILD_PREFLIGHT_PASS') 'PowerShell runtime builder does not report complete fail-before-mutation admission.'
Assert-True ($runtimeBuilder -match '\$canonicalEvidence') 'PowerShell runtime builder no longer resolves canonical evidence-client authority before mutation.'
Assert-True ($runtimeBuilder -match '\$canonicalOutput') 'PowerShell runtime builder no longer resolves canonical output authority before mutation.'

$runtimeBuilderCanonicalClientIndex = $runtimeBuilder.IndexOf('ClientJar must be the canonical evidence path')
$runtimeBuilderHashIndex = $runtimeBuilder.IndexOf('Exact v308 client hash mismatch')
$runtimeBuilderOutputIndex = $runtimeBuilder.IndexOf('OutputDirectory must be the canonical LocalLab runtime directory')
$runtimeBuilderPythonIndex = $runtimeBuilder.IndexOf("Get-Command python")
$runtimeBuilderPreflightPassIndex = $runtimeBuilder.IndexOf('V308_LOCAL_CLIENT_BUILD_PREFLIGHT_PASS')
$runtimeBuilderOutputCreateIndex = $runtimeBuilder.IndexOf('New-Item -ItemType Directory -Force -Path $output')
$runtimeBuilderPatchIndex = $runtimeBuilder.IndexOf('& $python.Source $patcher $client $output')
$runtimeBuilderFinalVerifyIndex = $runtimeBuilder.IndexOf("& (Join-Path $PSScriptRoot 'Check-ExternalRuntime.ps1')")
Assert-True ($runtimeBuilderCanonicalClientIndex -ge 0) 'PowerShell runtime builder canonical-client admission check not found.'
Assert-True ($runtimeBuilderHashIndex -gt $runtimeBuilderCanonicalClientIndex) 'PowerShell runtime builder hashes the client before canonical-path admission.'
Assert-True ($runtimeBuilderOutputIndex -gt $runtimeBuilderHashIndex) 'PowerShell runtime builder validates canonical output before exact client hash admission completes.'
Assert-True ($runtimeBuilderPythonIndex -gt $runtimeBuilderOutputIndex) 'PowerShell runtime builder probes Python before canonical output admission.'
Assert-True ($runtimeBuilderPreflightPassIndex -gt $runtimeBuilderPythonIndex) 'PowerShell runtime builder reports preflight before Python availability is proven.'
Assert-True ($runtimeBuilderOutputCreateIndex -gt $runtimeBuilderPreflightPassIndex) 'PowerShell runtime builder creates canonical output before all admission preflights pass.'
Assert-True ($runtimeBuilderPatchIndex -gt $runtimeBuilderOutputCreateIndex) 'PowerShell runtime builder invokes patcher before bounded canonical output setup.'
Assert-True ($runtimeBuilderFinalVerifyIndex -gt $runtimeBuilderPatchIndex) 'PowerShell runtime builder final triplet verification does not follow patcher publication.'
Assert-True ($v308Patcher -match 'ZIP_STORED') 'v308 local-client patcher no longer uses compression-independent deterministic JAR entries.'
Assert-True ($v308Patcher -match 'wholeJarDeterminismIndependentOfZlib') 'v308 local-client manifest no longer records zlib-independent whole-JAR determinism.'
Assert-True ($v308Patcher -notmatch 'ZIP_DEFLATED') 'v308 local-client patcher reintroduced zlib-dependent output compression.'
Assert-True ($v308Patcher -match 'unchangedEntryPayloadIdentity') 'v308 local-client patcher no longer proves unrelated entry payload identity.'
Assert-True ($v308Patcher -match 'entryInventoryAndOrderPreserved') 'v308 local-client patcher no longer proves entry inventory/order preservation.'
Assert-True ($v308Patcher -match 'manifestPayloadPreserved') 'v308 local-client patcher no longer proves manifest preservation.'

Assert-True ($v308Patcher -match 'tempfile\.TemporaryDirectory') 'v308 local-client builder does not use transaction-owned staging.'
Assert-True ($v308Patcher -match 'V308_LOCAL_CLIENT_STAGE_VERIFY_PASS') 'v308 local-client builder does not prove staged artifacts before publication.'
Assert-True ($v308Patcher -match 'V308_LOCAL_CLIENT_BACKUP_READY') 'v308 local-client builder does not snapshot canonical generated outputs before publication.'
Assert-True ($v308Patcher -match 'V308_LOCAL_CLIENT_FINAL_VERIFY_PASS') 'v308 local-client builder does not verify the complete published generated set under rollback ownership.'
Assert-True ($v308Patcher -match 'V308_LOCAL_CLIENT_ROLLBACK_COMPLETE') 'v308 local-client builder has no explicit clean rollback marker.'
Assert-True ($v308Patcher -match 'transactionalPublication') 'v308 local-client manifest no longer records transactional publication ownership.'
Assert-True ($v308Patcher -match 'touched\.append\(record\)') 'v308 local-client builder does not acquire rollback ownership before canonical writes.'
Assert-True ($v308Patcher -match 'for record in reversed\(touched\)') 'v308 local-client builder does not restore touched outputs in reverse publication order.'
Assert-True ($v308Patcher -match 'shutil\.copyfile\(record\["stage"\], record\["destination"\]\)') 'v308 local-client builder publication no longer comes from verified staging.'
Assert-True ($v308Patcher -notmatch 'build_variant\(source, output / "client-localhost\.jar"') 'v308 local-client builder reintroduced direct localhost generation into canonical output.'
Assert-True ($v308Patcher -notmatch 'build_variant\(source, output / "client-airgap\.jar"') 'v308 local-client builder reintroduced direct airgap generation into canonical output.'

$v308StageIndex = $v308Patcher.IndexOf('V308_LOCAL_CLIENT_STAGE_VERIFY_PASS')
$v308BackupIndex = $v308Patcher.IndexOf('V308_LOCAL_CLIENT_BACKUP_READY')
$v308TouchIndex = $v308Patcher.IndexOf('touched.append(record)')
$v308PublishIndex = $v308Patcher.IndexOf('shutil.copyfile(record["stage"], record["destination"])')
$v308FinalVerifyIndex = $v308Patcher.IndexOf('V308_LOCAL_CLIENT_FINAL_VERIFY_PASS')
$v308ExceptIndex = $v308Patcher.IndexOf('except BaseException as publish_error', $v308PublishIndex)
$v308SuccessIndex = $v308Patcher.IndexOf('V308_LOCAL_CLIENT_PATCH_PASS')
Assert-True ($v308StageIndex -ge 0) 'v308 local-client staged verification marker not found.'
Assert-True ($v308BackupIndex -gt $v308StageIndex) 'v308 local-client builder snapshots canonical outputs before staged verification.'
Assert-True ($v308TouchIndex -gt $v308BackupIndex) 'v308 local-client builder acquires mutation ownership before all backups exist.'
Assert-True ($v308PublishIndex -gt $v308TouchIndex) 'v308 local-client builder writes canonical output before rollback ownership.'
Assert-True ($v308FinalVerifyIndex -gt $v308PublishIndex) 'v308 local-client final verification does not follow canonical publication.'
Assert-True ($v308ExceptIndex -gt $v308FinalVerifyIndex) 'v308 local-client final verification escaped rollback ownership.'
Assert-True ($v308SuccessIndex -gt $v308FinalVerifyIndex) 'v308 local-client builder reports success before final published-set verification.'

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
Assert-True ($all -match 'LOCAL_LAB_REPLACEMENT_PREFLIGHT_PASS') 'Multi-client launcher does not report complete replacement preflight before process termination.'
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

$allRuntimeCheckIndex = $all.IndexOf('& $runtimeCheck')
$allServerJarPreflightIndex = $all.IndexOf('if (-not (Test-Path -LiteralPath $serverJar -PathType Leaf))')
$allLauncherPreflightIndex = $all.IndexOf('foreach ($required in @($serverScript,$watcherScript,$clientScript))')
$allReplacementPreflightPassIndex = $all.IndexOf('LOCAL_LAB_REPLACEMENT_PREFLIGHT_PASS')
$allStopIndex = $all.IndexOf('Stop-Process -Id $ownerPid -Force')
Assert-True ($allRuntimeCheckIndex -ge 0) 'Multi-client external-runtime preflight invocation not found.'
Assert-True ($allServerJarPreflightIndex -gt $allRuntimeCheckIndex) 'Multi-client replacement server JAR is checked before external-runtime authority.'
Assert-True ($allLauncherPreflightIndex -gt $allServerJarPreflightIndex) 'Multi-client launcher components are not checked after server-JAR preflight.'
Assert-True ($allReplacementPreflightPassIndex -gt $allLauncherPreflightIndex) 'Multi-client replacement preflight marker is emitted before required launcher checks.'
Assert-True ($allStopIndex -gt $allReplacementPreflightPassIndex) 'Multi-client launcher may terminate an existing LocalLab before replacement preflight completes.'

Assert-True ($bootstrap -match '\[switch\]\$SkipConfigPatch') 'Bootstrap no longer preserves the legacy -SkipConfigPatch compatibility switch.'
Assert-True ($bootstrap -match 'BOOTSTRAP_CONFIG_PATCH_RETIRED') 'Bootstrap does not state that live config mutation is retired.'
Assert-True ($bootstrap -match 'isolatedCachePipelineRequired=true') 'Bootstrap does not point custom-cache work to isolated authority.'
Assert-True ($bootstrap -notmatch [regex]::Escape('scripts\Patch-LocalConfigs.ps1')) 'Bootstrap reintroduced the retired live config patch script.'

Assert-True ($configPatch -match 'LOCALLAB_CONFIG_PATCH_RETIRED') 'Retired config-patch shim lost its fail-closed marker.'
Assert-True ($configPatch -match 'isolatedCachePipelineRequired=true') 'Retired config-patch shim does not require isolated cache authority.'
Assert-True ($configPatch -match [regex]::Escape('scripts\Run-R13AssetAcceptance.ps1')) 'Retired config-patch shim does not point to canonical R13 acceptance tooling.'
Assert-True ($configPatch -match 'build_r13_isolated_profile\.py') 'Retired config-patch shim does not point to the isolated profile builder.'
Assert-True ($configPatch -notmatch 'VoidglassR3ConfigPatchTool') 'Retired config-patch shim still invokes the obsolete Java mutator.'
Assert-True ($configPatch -notmatch 'Copy-Item') 'Retired config-patch shim still copies live config bytes.'
Assert-True ($configPatch -notmatch '&\s+\$java\.Path') 'Retired config-patch shim still launches Java mutation tooling.'

Assert-True ($serverWrapper -match 'Select-LocalLabJava\.ps1') 'Server wrapper is not using the canonical Java selector.'
Assert-True ($serverWrapper -match [regex]::Escape('server\build\SpawnPKLocalServer.jar')) 'Server wrapper does not target the current built server JAR.'
Assert-True ($serverWrapper -match 'Test-Path\s+-LiteralPath\s+\$required\s+-PathType\s+Leaf') 'Server wrapper does not fail closed on missing launch components.'
Assert-True ($serverWrapper -match '&\s+\$java\.Path\s+-jar\s+\$jar\s+--bootstrap\s+--movement') 'Server wrapper does not launch through the selected Java path with canonical server arguments.'
Assert-True ($serverWrapper -match '\$serverExit\s*=\s*\$LASTEXITCODE') 'Server wrapper does not capture the native Java exit code immediately.'
Assert-True ($serverWrapper -match '\$serverExit\s+-ne\s+0') 'Server wrapper does not fail on nonzero Java exit.'

$serverLaunchIndex = $serverWrapper.IndexOf('& $java.Path -jar $jar --bootstrap --movement')
$serverExitCaptureIndex = $serverWrapper.IndexOf('$serverExit = $LASTEXITCODE')
$serverExitCheckIndex = $serverWrapper.IndexOf('if ($serverExit -ne 0)')
Assert-True ($serverLaunchIndex -ge 0) 'Server wrapper native launch not found.'
Assert-True ($serverExitCaptureIndex -gt $serverLaunchIndex) 'Server wrapper does not capture native exit after launch.'
Assert-True ($serverExitCheckIndex -gt $serverExitCaptureIndex) 'Server wrapper checks exit status before capture or not at all.'

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
