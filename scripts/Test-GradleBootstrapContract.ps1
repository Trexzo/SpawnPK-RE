Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$bootstrapPath = Join-Path $repo 'server\gradle\bootstrap-gradle.ps1'

if (-not (Test-Path -LiteralPath $bootstrapPath -PathType Leaf)) {
    throw "Missing Gradle bootstrap: $bootstrapPath"
}

$bootstrap = Get-Content -LiteralPath $bootstrapPath -Raw
$hashHelperPath = Join-Path $repo 'server\gradle\ExactSha256.ps1'
if (-not (Test-Path -LiteralPath $hashHelperPath -PathType Leaf)) {
    throw "Missing Gradle SHA-256 helper: $hashHelperPath"
}
$hashHelper = Get-Content -LiteralPath $hashHelperPath -Raw

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

foreach ($path in @($bootstrapPath, $hashHelperPath)) {
    $tokens = $null
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $path,
        [ref]$tokens,
        [ref]$parseErrors
    )
    if ($parseErrors.Count -gt 0) {
        $detail = ($parseErrors | ForEach-Object { $_.Message }) -join '; '
        throw "PowerShell parse failed for Gradle bootstrap/helper $path : $detail"
    }
}

Assert-True (
    $bootstrap -match [regex]::Escape(
        '31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26'
    )
) 'Gradle bootstrap lost the pinned 8.10.2 distribution SHA-256.'

# G21 Windows portability: the helper is now a separately sourced, pinned
# .NET SHA-256 implementation. Check the actual source for function
# declaration/byte hashing and verify loading occurs BEFORE the first
# ZIP hash. Never accept an empty helper or bypassed SHA check.
Assert-True ($hashHelper -match 'function\s+Get-ExactSha256') 'Gradle bootstrap lost exact SHA-256 helper.'
Assert-True ($bootstrap -match '(?m)^\.\s*\(Join-Path\s+\$PSScriptRoot\s+''ExactSha256\.ps1''\)') 'Gradle bootstrap does not source the exact adjacent SHA-256 helper.'
Assert-True ($hashHelper -match '\[System\.IO\.File\]::OpenRead\(\$Path\)') 'SHA-256 helper must read the actual file bytes.'
Assert-True ($hashHelper -match '\[System\.Security\.Cryptography\.SHA256\]::Create\(\)') 'SHA-256 helper must create a real SHA-256 digest.'
Assert-True ($hashHelper -match '\$algorithm\.ComputeHash\(\$stream\)') 'SHA-256 helper must hash the opened file stream.'
Assert-True ($hashHelper -match '\$algorithm\.Dispose\(\)') 'SHA-256 helper must dispose algorithm.'
Assert-True ($hashHelper -match '\$stream\.Dispose\(\)') 'SHA-256 helper must dispose input stream.'
Assert-True ($hashHelper -notmatch 'Get-FileHash|MD5|SHA1') 'SHA-256 helper uses an unsupported cmdlet or weak digest.'
Assert-ExactTextCount $bootstrap ". (Join-Path `$PSScriptRoot 'ExactSha256.ps1')" 1 'Exact SHA-256 helper must load once.'
$helperLoadIndex = $bootstrap.IndexOf(". (Join-Path `$PSScriptRoot 'ExactSha256.ps1')")
$firstHashIndex = $bootstrap.IndexOf('Get-ExactSha256 $zip')
Assert-True ($helperLoadIndex -ge 0 -and $firstHashIndex -gt $helperLoadIndex) 'Gradle bootstrap SHA-256 helper was not loaded before use.'
Assert-True ($bootstrap -match 'Get-ExactSha256\s+\$zip') 'Gradle bootstrap does not verify the persistent cached ZIP.'
Assert-True ($bootstrap -match 'Get-ExactSha256\s+\$download') 'Gradle bootstrap does not verify a downloaded ZIP before cache publication.'
Assert-True ($bootstrap -match 'Get-ExactSha256\s+\$invocationZip') 'Gradle bootstrap does not independently hash the invocation-owned ZIP.'
Assert-True ($bootstrap -match '\[IO\.Path\]::GetTempPath\(\)') 'Gradle bootstrap does not use invocation-owned temporary extraction.'
Assert-True ($bootstrap -match 'Copy-Item\s+-LiteralPath\s+\$zip\s+-Destination\s+\$invocationZip') 'Gradle bootstrap does not freeze cached bytes into invocation-owned authority.'
Assert-True ($bootstrap -match 'Expand-Archive\s+-LiteralPath\s+\$invocationZip') 'Gradle bootstrap does not extract the verified invocation-owned ZIP.'
Assert-True ($bootstrap -match '\$gradleHome\s*=\s*Join-Path\s+\$extractRoot') 'Gradle executable is not resolved inside invocation-owned extraction.'
Assert-True ($bootstrap -match 'LOCALLAB_GRADLE_DISTRIBUTION_VERIFIED') 'Gradle bootstrap lacks an exact distribution verification boundary.'
Assert-True ($bootstrap -match 'invocationOwned=true') 'Gradle bootstrap success evidence does not identify invocation-owned distribution authority.'
Assert-True ($bootstrap -match 'Remove-Item\s+-LiteralPath\s+\$invocationRoot\s+-Recurse') 'Gradle bootstrap does not clean invocation-owned extraction.'
Assert-True ($bootstrap -notmatch '\$gradleHome\s*=\s*Join-Path\s+\$cacheRoot') 'Gradle bootstrap reintroduced persistent extracted-home execution authority.'

foreach ($entry in @(
    @('Get-ExactSha256 $invocationZip', 1),
    @('Expand-Archive -LiteralPath $invocationZip', 1),
    @('& $gradleBat @GradleArgs', 1),
    @('LOCALLAB_GRADLE_DISTRIBUTION_VERIFIED', 1),
    @('Test-Path -LiteralPath $gradleBat -PathType Leaf', 1),
    @('Remove-Item -LiteralPath $invocationRoot -Recurse', 1)
)) {
    Assert-ExactTextCount $bootstrap $entry[0] ([int]$entry[1]) 'Gradle bootstrap structural anchor count drift.'
}

$cachedHashIndex = $bootstrap.IndexOf('Get-ExactSha256 $zip')
$invocationCopyIndex = $bootstrap.IndexOf('Copy-Item -LiteralPath $zip -Destination $invocationZip')
$invocationHashIndex = $bootstrap.IndexOf('Get-ExactSha256 $invocationZip')
$verifyMarkerIndex = $bootstrap.IndexOf('LOCALLAB_GRADLE_DISTRIBUTION_VERIFIED')
$extractIndex = $bootstrap.IndexOf('Expand-Archive -LiteralPath $invocationZip')
$gradleResolveIndex = $bootstrap.IndexOf('$gradleBat = Join-Path $gradleHome')
$launcherExistsIndex = $bootstrap.IndexOf('Test-Path -LiteralPath $gradleBat -PathType Leaf')
$executeIndex = $bootstrap.IndexOf('& $gradleBat @GradleArgs')
$cleanupIndex = $bootstrap.IndexOf('Remove-Item -LiteralPath $invocationRoot -Recurse')

Assert-True ($cachedHashIndex -ge 0) 'Cached distribution hash gate not found.'
Assert-True ($invocationCopyIndex -gt $cachedHashIndex) 'Invocation copy occurs before cached ZIP verification.'
Assert-True ($invocationHashIndex -gt $invocationCopyIndex) 'Invocation ZIP is not hashed after private copy.'
Assert-True ($verifyMarkerIndex -gt $invocationHashIndex) 'Verified marker precedes invocation SHA proof.'
Assert-True ($extractIndex -gt $verifyMarkerIndex) 'Gradle extraction occurs before exact invocation verification.'
Assert-True ($gradleResolveIndex -gt $extractIndex) 'Gradle executable is resolved before verified extraction.'
Assert-True ($launcherExistsIndex -gt $gradleResolveIndex) 'Gradle launcher existence check occurs before invocation-owned Gradle path resolution.'
Assert-True ($executeIndex -gt $launcherExistsIndex) 'Gradle executes before the verified extracted launcher existence check.'
Assert-True ($cleanupIndex -gt $executeIndex) 'Invocation-owned Gradle extraction cleanup is not structurally after execution.'

Write-Host 'LOCALLAB_GRADLE_BOOTSTRAP_CONTRACT_PASS pinnedDistributionEveryBuild=true' -ForegroundColor Green
