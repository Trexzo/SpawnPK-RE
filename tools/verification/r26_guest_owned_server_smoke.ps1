<#
R26 — MANUAL Windows Sandbox GUEST-ONLY owned LocalLab loopback server smoke.
No graphics client is launched. Server process exists ONLY inside the guest.
Default mode: read-only admission plan; -Run with exact approval is required.
Uses already verified R25 private guest HOME, R24 network/cache preflight,
R13 private Java11 and exact staged LocalLab server JAR.

Never modifies Windows Firewall, installs software, creates a host account,
touches another JVM, dials a non-loopback address or uses game credentials.
It may terminate ONLY the specific Process object started by this script
if its private-token graceful shutdown fails; failure is always reported.

A PASS is guest server TCP readiness, local AUX HTTP and clean OWNED process
exit. It does NOT prove a real graphical v308 client works.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string] $GuestHome,
    [switch] $Run,
    [string] $ExactApproval=''
)
$ErrorActionPreference='Stop'
$approval='RUN R26 OWNED GUEST LOCAL SERVER SMOKE'
$assets='C:\SPKR23\assets'
$jdk='C:\SPKR23\jdk'
$serverJar=Join-Path $assets 'SpawnPKLocalServer.jar'
$javaExe=Join-Path $jdk 'bin\java.exe'
$proc=$null
$started=$false
$shutdownAuthorized=$false
$receiptPath=$null

function Test-PortsFree {
    foreach($port in @(43594,43595)) {
        $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,[int]$port)
        try { $listener.Start() } finally { $listener.Stop() }
    }
}

function Get-OwnedListeners([int]$pid) {
    $rows=@(Get-NetTCPConnection -State Listen -ErrorAction Stop |
        Where-Object { $_.LocalPort -in @(43594,43595) -and
                        $_.LocalAddress -eq '127.0.0.1' })
    if($rows.Count -ne 2) { return $false }
    foreach($row in $rows) {
        if($row.OwningProcess -ne $pid) { return $false }
    }
    return (@($rows | Select-Object -ExpandProperty LocalPort -Unique).Count -eq 2)
}

function Test-LocalAux {
    $request=[Net.HttpWebRequest]::Create(
        'http://127.0.0.1:43595/spk_live/versions.txt'
    )
    $request.Proxy=$null
    $request.Timeout=1500
    $request.ReadWriteTimeout=1500
    $request.KeepAlive=$false
    $response=$request.GetResponse()
    try {
        if([int]$response.StatusCode -ne 200) { return $false }
        $reader=[IO.StreamReader]::new($response.GetResponseStream(),
                                       [Text.Encoding]::ASCII)
        try { $text=$reader.ReadToEnd() } finally { $reader.Dispose() }
        return ($text.Length -gt 0 -and $text.Length -lt 16384 -and
                $text.Contains('cache_version') -and
                $text.Contains('sprite_version') -and
                $text.Contains('config_version'))
    } finally { $response.Dispose() }
}

function Test-GameSocket {
    $client=[Net.Sockets.TcpClient]::new()
    try {
        $result=$client.BeginConnect('127.0.0.1',43594,$null,$null)
        if(-not $result.AsyncWaitHandle.WaitOne(1500)) { return $false }
        $client.EndConnect($result)
        return $client.Connected
    } finally { $client.Dispose() }
}

try {
    if($env:USERNAME -cne 'WDAGUtilityAccount') {
        throw 'R26_BLOCKED: only a real Windows Sandbox guest may run this'
    }
    if(-not [IO.Path]::IsPathRooted($GuestHome) -or
       -not (Test-Path -LiteralPath $GuestHome -PathType Container)) {
        throw 'R26_BLOCKED: missing absolute R25 private guest HOME'
    }
    $fullGuestHome=[IO.Path]::GetFullPath($GuestHome).TrimEnd('\')
    $guestTemp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
    if(-not $guestTemp.StartsWith('C:\Users\WDAGUtilityAccount\',
                                 [StringComparison]::OrdinalIgnoreCase) -or
       -not [string]::Equals(
            [IO.Path]::GetDirectoryName($fullGuestHome),$guestTemp,
            [StringComparison]::OrdinalIgnoreCase) -or
       -not [IO.Path]::GetFileName($fullGuestHome).StartsWith(
            'spawnpk-r25-guesthome-', [StringComparison]::Ordinal)) {
        throw 'R26_BLOCKED: HOME must be a private R25 directory under guest Temp'
    }
    $homeItem=Get-Item -LiteralPath $fullGuestHome -ErrorAction Stop
    if(($homeItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'R26_BLOCKED: guest HOME reparse point refused'
    }
    $r25Receipt=Get-Content -LiteralPath (
        Join-Path $fullGuestHome 'r25-private-guest-home-receipt.json'
    ) -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    if($r25Receipt.result -cne
        'R25_GUEST_PRIVATE_WRITABLE_HOME_PREPARED_NO_GUI' -or
       $r25Receipt.guest_home -cne $fullGuestHome -or
       $r25Receipt.copied_static_cache_files -ne 10764 -or
       [long]$r25Receipt.copied_static_cache_bytes -ne 209188225 -or
       $r25Receipt.static_cache_inventory_sha256 -cne
        '37cf52440fb46c44c8671470776e5950b5bdc6a8ecf470bd52bf71c87d5208f3' -or
       $r25Receipt.private_account_state_initially_empty -cne $true -or
       $r25Receipt.original_live_user_profile_mapped -cne $false) {
        throw 'R26_BLOCKED: R25 guest HOME provenance receipt invalid'
    }
    if(-not (Test-Path -LiteralPath (Join-Path $fullGuestHome '.spawnpk') -PathType Container)) {
        throw 'R26_BLOCKED: writable isolated cache directory unavailable'
    }
    $userData=Join-Path $fullGuestHome '.spawnpk-data'
    if(-not (Test-Path -LiteralPath $userData -PathType Container) -or
       @(Get-ChildItem -LiteralPath $userData -Force -ErrorAction Stop).Count -ne 0) {
        throw 'R26_BLOCKED: private account data is not initially empty'
    }

    # R24 itself invokes R23 guest route, network adapter, TCP self-loopback,
    # cache/Java/JAR SHA checks. This cannot be skipped, even in plan mode.
    $r24=Join-Path $assets 'guest-cache-preflight.ps1'
    if(-not (Test-Path -LiteralPath $r24 -PathType Leaf)) {
        throw 'R26_BLOCKED: R24 guest preflight missing'
    }
    & powershell.exe -NoProfile -NonInteractive -File $r24
    if($LASTEXITCODE -ne 0) {
        throw 'R26_BLOCKED: guest R23/R24 isolation and pins not verified'
    }
    if(-not (Test-Path -LiteralPath $serverJar -PathType Leaf) -or
       -not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
        throw 'R26_BLOCKED: pinned server or private JDK absent'
    }
    Test-PortsFree

    if(-not $Run) {
        Write-Output 'R26_GUEST_LOCAL_SERVER_SMOKE_PLAN_PASS_NO_PROCESS_STARTED'
        exit 0
    }
    if($ExactApproval -cne $approval) {
        throw 'R26_BLOCKED: exact guest-only operator approval required'
    }

    $accounts=Join-Path $fullGuestHome 'data\accounts'
    [void][IO.Directory]::CreateDirectory($accounts)
    $accountFile=Join-Path $accounts 'opensrc.properties'
    if(Test-Path -LiteralPath $accountFile) {
        throw 'R26_BLOCKED: selected synthetic account already exists'
    }
    $random=[byte[]]::new(32)
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($random) } finally { $rng.Dispose() }
    $token=([BitConverter]::ToString($random)).Replace('-','').ToLowerInvariant()
    if($token -cnotmatch '^[0-9a-f]{64}$') {
        throw 'R26_BLOCKED: invalid private shutdown token'
    }

    $psi=[Diagnostics.ProcessStartInfo]::new()
    $psi.FileName=$javaExe
    $psi.Arguments=(
        '"-Duser.home=' + $fullGuestHome + '" ' +
        '-Djava.net.preferIPv4Stack=true ' +
        '"-Dspk.local.accountFile=' + $accountFile + '" ' +
        '-Dspk.local.acceptance.stdinShutdownEnabled=true ' +
        '-Dspk.local.acceptance.stdinShutdownToken=' + $token + ' ' +
        '-jar "' + $serverJar + '" --bootstrap --movement'
    )
    $psi.WorkingDirectory=$fullGuestHome
    $psi.UseShellExecute=$false
    $psi.CreateNoWindow=$true
    $psi.RedirectStandardInput=$true
    # Don't redirect stdout/stderr into small unconsumed pipes; the world
    # may print many lines and block if a pipe buffer fills.
    $psi.RedirectStandardOutput=$false
    $psi.RedirectStandardError=$false
    $psi.EnvironmentVariables['APPDATA']=$fullGuestHome
    $psi.EnvironmentVariables['LOCALAPPDATA']=$fullGuestHome

    $proc=[Diagnostics.Process]::new()
    $proc.StartInfo=$psi
    if(-not $proc.Start()) {
        throw 'R26_BLOCKED: owned test JVM did not start'
    }
    $started=$true
    $deadline=[DateTime]::UtcNow.AddSeconds(22)
    $ready=$false
    while([DateTime]::UtcNow -lt $deadline) {
        if($proc.HasExited) {
            throw 'R26_BLOCKED: newly owned server exited before readiness'
        }
        try {
            if((Get-OwnedListeners $proc.Id) -and
               (Test-LocalAux) -and (Test-GameSocket)) {
                $ready=$true
                break
            }
        } catch [Net.WebException] {
            # Server has not bound its local AUX listener yet.
        } catch [Net.Sockets.SocketException] {
            # Game listener not ready yet.
        }
        Start-Sleep -Milliseconds 120
    }
    if(-not $ready) {
        throw 'R26_BLOCKED: owned game/AUX localhost listeners not ready'
    }
    # Recheck exact server/JDK binary pins after their actual use.
    $pins=Get-Content -LiteralPath (
        Join-Path $assets 'asset-pins.json'
    ) -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    $serverSha=(Get-FileHash -LiteralPath $serverJar -Algorithm SHA256).Hash.ToLowerInvariant()
    $javaSha=(Get-FileHash -LiteralPath $javaExe -Algorithm SHA256).Hash.ToLowerInvariant()
    if($serverSha -cne $pins.server_jar_sha256 -or
       $javaSha -cne 'b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef') {
        throw 'R26_BLOCKED: server/private JDK binary changed during smoke'
    }

    $proc.StandardInput.WriteLine($token)
    $proc.StandardInput.Flush()
    $shutdownAuthorized=$true
    if(-not $proc.WaitForExit(30000) -or $proc.ExitCode -ne 0) {
        throw 'R26_BLOCKED: owned server did not shut down cleanly'
    }
    Test-PortsFree
    if(Test-Path -LiteralPath $accountFile) {
        # The test need not log in; account existence is allowed ONLY
        # inside this newly created guest HOME.
        $account=Get-Item -LiteralPath $accountFile
        if(($account.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'R26_BLOCKED: test account became a reparse point'
        }
    }

    $receipt=[ordered]@{
        result='R26_GUEST_OWNED_LOCALLAB_SERVER_LOOPBACK_SMOKE_PASS'
        server_sha256=$serverSha
        java_sha256=$javaSha
        guest_home=$fullGuestHome
        owned_server_pid=$proc.Id
        game_listener='127.0.0.1:43594'
        aux_listener='127.0.0.1:43595'
        both_listeners_owned_by_test_child=$true
        aux_http_versions_verified=$true
        game_tcp_handshake_verified=$true
        private_stdin_shutdown_authorized=$shutdownAuthorized
        owned_server_exit_code=$proc.ExitCode
        both_ports_released=$true
        guest_network_preflight_passed=$true
        graphical_client_started=$false
        authenticated_gameplay_tested=$false
        host_client_sessions_stopped=0
    }
    $receiptPath=Join-Path $fullGuestHome 'r26-owned-server-smoke-receipt.json'
    [IO.File]::WriteAllText(
        $receiptPath, ($receipt | ConvertTo-Json -Depth 4) + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )
    Write-Output 'R26_GUEST_OWNED_LOCALLAB_SERVER_LOOPBACK_SMOKE_PASS'
    Write-Output 'R26_GUEST_SERVER_STOPPED_OWNED_PORTS_RELEASED_NO_GUI'
    exit 0
} catch {
    Write-Output ('R26_GUEST_OWNED_LOCAL_SERVER_SMOKE_BLOCKED: ' + $_.Exception.Message)
    exit 2
} finally {
    if($started -and $null -ne $proc) {
        if(-not $proc.HasExited) {
            # Never touch any java.exe process by name or global PID lookup.
            # First attempt the private test-only stdin shutdown on THIS child.
            try {
                if(-not $shutdownAuthorized -and $token) {
                    $proc.StandardInput.WriteLine($token)
                    $proc.StandardInput.Flush()
                }
                [void]$proc.WaitForExit(12000)
            } catch {}
            if(-not $proc.HasExited) {
                # Only this invocation's own server child object can be killed.
                try {
                    $proc.Kill()
                    [void]$proc.WaitForExit(5000)
                } catch {}
            }
        }
        $proc.Dispose()
    }
}
