Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$bootstrapPath = Join-Path $repo 'server\gradle\bootstrap-gradle.ps1'

if (-not (Test-Path -LiteralPath $bootstrapPath -PathType Leaf)) {
    throw "Missing Gradle bootstrap: $bootstrapPath"
}

$bootstrap = Get-Content -LiteralPath $bootstrapPath -Raw

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

$tokens = $null
$errors = $null
[void][System.Management.Automation.Language.Parser]::ParseFile(
    $bootstrapPath,
    [ref]$tokens,
    [ref]$errors
)
if ($errors.Count -gt 0) {
    $detail = ($errors | ForEach-Object { $_.Message }) -join '; '
    throw "PowerShell parse failed for Gradle bootstrap: $detail"
}

Assert-True (
    $bootstrap -match [regex]::Escape(
        '31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26'
    )
) 'Gradle bootstrap lost the pinned 8.10.2 distribution SHA-256.'

Assert-True ($bootstrap -match 'function\s+Get-ExactSha256') 'Gradle bootstrap lost exact SHA-256 helper.'
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
