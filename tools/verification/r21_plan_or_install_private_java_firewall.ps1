<#
R21 Windows Firewall bootstrap for a VERIFIED, invocation-owned private JDK11.

DEFAULT: read-only plan; NEVER modifies Windows Firewall.
-Apply: only under an already elevated PowerShell window AND with the
exact -ExactApproval phrase. No automatic UAC or process launching.
Applies TWO private-exe-only outbound BLOCK rules: java.exe and javaw.exe.
Never modifies any existing firewall rule, global profile or live Java client.

These program rules are only one network-isolation layer. A native helper or
another executable may make its own network connections. Passing the audit
is NOT graphical-client acceptance or proof of a complete airgap.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $PrivateJdkRoot,
    [switch] $Apply,
    [string] $ExactApproval = ''
)

$ErrorActionPreference = 'Stop'
$fixedApproval = 'INSTALL R21 TWO PRIVATE JAVA OUTBOUND BLOCK RULES'
$expected = @(
    [pscustomobject]@{
        File='java.exe'
        Digest='b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef'
        Display='SpawnPK LocalLab R13 Private JDK All Outbound Block'
        Name='SpawnPK-R21-PrivateJdk-Java-AllOutbound'
    },
    [pscustomobject]@{
        File='javaw.exe'
        Digest='0a4656c826032ffe28d58ebfa93e85d4c099c50a1857f29416461848f0c6adaa'
        Display='SpawnPK LocalLab R21 Private JDK javaw All Outbound Block'
        Name='SpawnPK-R21-PrivateJdk-Javaw-AllOutbound'
    }
)

if (-not [IO.Path]::IsPathRooted($PrivateJdkRoot)) {
    throw 'R21_BLOCKED: private JDK path must be absolute'
}
$root = (Get-Item -LiteralPath $PrivateJdkRoot -ErrorAction Stop)
if (-not $root.PSIsContainer -or
    ($root.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
    $root.Name -cne 'jdk') {
    throw 'R21_BLOCKED: private JDK root is not a non-reparse jdk directory'
}
$owned = $root.Parent
if (-not $owned.Name.StartsWith('spawnpk-r13-owned-jdk-', [StringComparison]::Ordinal) -or
    ($owned.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'R21_BLOCKED: JDK is not an R13 snapshot'
}
$actualTemp = (Get-Item -LiteralPath ([IO.Path]::GetTempPath())).FullName.TrimEnd('\')
if (-not [string]::Equals(
        $owned.Parent.FullName.TrimEnd('\'), $actualTemp,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'R21_BLOCKED: snapshot is not directly inside this user temp directory'
}

foreach ($item in $expected) {
    $item | Add-Member -NotePropertyName FullPath -NotePropertyValue (
        Join-Path $root.FullName ('bin\' + $item.File)
    )
    $exe = Get-Item -LiteralPath $item.FullPath -ErrorAction Stop
    if ($exe.PSIsContainer -or
        ($exe.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw ('R21_BLOCKED: invalid executable ' + $item.File)
    }
    $actual = (Get-FileHash -LiteralPath $exe.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -cne $item.Digest) {
        throw ('R21_BLOCKED: executable digest mismatch for ' + $item.File)
    }
}

$liveJava = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop)
foreach ($proc in $liveJava) {
    if (-not $proc.ExecutablePath) {
        throw 'R21_BLOCKED: Java process without inspectable executable path'
    }
    foreach ($item in $expected) {
        if ([string]::Equals(
                $proc.ExecutablePath, $item.FullPath,
                [StringComparison]::OrdinalIgnoreCase)) {
            throw ('R21_BLOCKED: dedicated JDK already running as PID ' + $proc.ProcessId)
        }
    }
}

$profiles = @(Get-NetFirewallProfile -PolicyStore ActiveStore -ErrorAction Stop)
$service = Get-Service -Name mpssvc -ErrorAction Stop
if ($service.Status.ToString() -ne 'Running' -or
    $profiles.Count -lt 3 -or
    @($profiles | Where-Object { -not $_.Enabled }).Count -gt 0) {
    throw 'R21_BLOCKED: firewall service or one of three network profiles disabled'
}

$existing = @()
foreach ($item in $expected) {
    $byDisplay = @(Get-NetFirewallRule -PolicyStore ActiveStore -DisplayName $item.Display -ErrorAction SilentlyContinue)
    $byName = @(Get-NetFirewallRule -PolicyStore ActiveStore -Name $item.Name -ErrorAction SilentlyContinue)
    if ($byDisplay.Count -gt 0 -or $byName.Count -gt 0) {
        $existing += $item.Display
    }
}

if (-not $Apply) {
    [pscustomobject]@{
        Result='R21_TWO_PRIVATE_JAVA_FIREWALL_PLAN_READY'
        Mode='READ_ONLY_NO_MUTATION'
        PrivateJdk=$root.FullName
        Executables=@($expected | ForEach-Object { [pscustomobject]@{
            File=$_.File
            Path=$_.FullPath
            Sha256=$_.Digest
            DisplayName=$_.Display
        }})
        ExistingMatchingNames=$existing
        ExistingJavaProcessesInspected=$liveJava.Count
        FirewallRulesChanged=0
        GraphicalClientStarted=$false
        Note='A plan does NOT prove firewall activation or permit GUI startup'
    } | ConvertTo-Json -Depth 5
    exit 0
}

if ($existing.Count -ne 0) {
    throw 'R21_BLOCKED: one or more rule names already exist; inspect manually, no overwrite'
}
$admin = ([Security.Principal.WindowsPrincipal](
    [Security.Principal.WindowsIdentity]::GetCurrent()
)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    throw 'R21_BLOCKED: apply requires an already elevated PowerShell'
}
if ($ExactApproval -cne $fixedApproval) {
    throw 'R21_BLOCKED: missing exact explicit approval phrase'
}
$checker = Join-Path $PSScriptRoot 'r21_windows_dual_java_egress_gate.py'
if (-not (Test-Path -LiteralPath $checker -PathType Leaf)) {
    throw 'R21_BLOCKED: independent audit script missing'
}
$python = Get-Command python.exe -ErrorAction Stop

foreach ($item in $expected) {
    # No global firewall setting is changed. The rule covers only the
    # verified private executable, for every outbound protocol/address/port.
    New-NetFirewallRule -PolicyStore PersistentStore `
        -Name $item.Name `
        -DisplayName $item.Display `
        -Description 'SpawnPK R21 test-only private JDK outbound block; never shared v312 runtimes' `
        -Program $item.FullPath `
        -Direction Outbound -Action Block -Enabled True -Profile Any `
        -RemoteAddress Any -Protocol Any -ErrorAction Stop | Out-Null
}
# Keep fail-closed firewall rules installed if the independent audit fails;
# NEVER remove a block simply because verification encountered an error.
& $python.Source $checker --private-jdk-root $root.FullName
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    throw ('R21_BLOCKED: independently installed rules not verified; audit exit=' + $exitCode)
}
Write-Output 'R21_TWO_PRIVATE_JAVA_FIREWALL_RULES_INSTALLED_AND_AUDITED'
Write-Output 'R21_GUI_NOT_LAUNCHED_OTHER_GUI_ADMISSION_GATES_STILL_REQUIRED'
