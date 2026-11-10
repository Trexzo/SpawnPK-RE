<#
R27 manual Windows Sandbox GUI smoke. NEVER run this on the Windows host.
DEFAULT: read-only plan. The -Launch path is enabled only inside an actual
network-disabled guest, after R23/R24 cache/network/loopback and R25/R26
guest receipts, with explicit operator approval.

The only objective is to see a REAL v308 GUI top-level window under an
owned, sandbox-local synthetic LocalLab server. It never logs in, uses real
credentials, configures Windows Firewall or contacts external networks.
Only its OWN Java child-process objects may be stopped.
A GUI window is NOT proof of protocol login, graphics correctness or gameplay.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$GuestHome,
    [switch]$Launch,
    [switch]$CaptureFrame,
    [string]$ExactApproval='',
    [ValidateRange(10,120)][int]$ObserveSeconds=45
)
$ErrorActionPreference='Stop'
$assets='C:\SPKR23\assets'
$jdk='C:\SPKR23\jdk'
$serverJar=Join-Path $assets 'SpawnPKLocalServer.jar'
$clientJar=Join-Path $assets 'client-airgap.jar'
$java=Join-Path $jdk 'bin\java.exe'
$expectedClient='024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7'
$expectedJava='b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef'
$approval='LAUNCH R27 GUEST V308 GUI WITH LOCAL SERVER'
$server=$null
$client=$null
$token=$null
$serverStarted=$false
$clientStarted=$false
$windowSeen=$false
$serverWasReady=$false
$clientWasStopped=$false
$serverWasStopped=$false
$forcedClientStop=$false
$forcedServerStop=$false
$guestHomePath=$null
$frameSaved=$false
$framePath=$null
$frameSha=$null
$frameColours=0
$capturedWindowTitle=''


# R37: evidence-only capture, within an independently verified Windows Sandbox
# guest, of the exact GUI window owned by this invocation's Java child.
# PInvoke is used solely for WINDOW ownership/geometry, not input or hooks.
function Capture-GuestOwnedClientWindow([Diagnostics.Process]$child,
                                        [string]$privateHome) {
    $child.Refresh()
    if($child.HasExited -or $child.MainWindowHandle -eq [IntPtr]::Zero) {
        throw 'R37_BLOCKED: no live owned client window to capture'
    }
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class R37WindowNative {
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint owner);
    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool IsWindowVisible(IntPtr hwnd);
    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool GetWindowRect(IntPtr hwnd, out RECT rect);
    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();
}
'@ -ErrorAction Stop
    $handle=$child.MainWindowHandle
    $owner=[uint32]0
    [void][R37WindowNative]::GetWindowThreadProcessId($handle,[ref]$owner)
    if($owner -ne [uint32]$child.Id -or
       -not [R37WindowNative]::IsWindowVisible($handle)) {
        throw 'R37_BLOCKED: window visible/owner PID not verified'
    }
    # CopyFromScreen captures the composed display, not an occluded
    # back-buffer; reject an unfocused window rather than capture another
    # program that happens to overlap the target rectangle.
    if([R37WindowNative]::GetForegroundWindow() -ne $handle) {
        throw 'R37_BLOCKED: client window not foreground; user should focus it inside guest'
    }
    $rect=[R37WindowNative+RECT]::new()
    if(-not [R37WindowNative]::GetWindowRect($handle,[ref]$rect)) {
        throw 'R37_BLOCKED: owned client window rectangle not readable'
    }
    $width=[int]($rect.Right-$rect.Left)
    $height=[int]($rect.Bottom-$rect.Top)
    if($width -lt 360 -or $height -lt 220 -or
       $width -gt 4096 -or $height -gt 4096) {
        throw 'R37_BLOCKED: owned GUI window implausibly sized'
    }
    Add-Type -AssemblyName System.Drawing -ErrorAction Stop
    $file=Join-Path $privateHome 'r37-v308-guest-window.png'
    if(Test-Path -LiteralPath $file) {
        throw 'R37_BLOCKED: would overwrite an existing guest screenshot'
    }
    $bitmap=[Drawing.Bitmap]::new($width,$height)
    $graphics=$null
    try {
        $graphics=[Drawing.Graphics]::FromImage($bitmap)
        $graphics.CopyFromScreen(
            $rect.Left,$rect.Top,0,0,
            [Drawing.Size]::new($width,$height)
        )
        # A single solid-colour window is insufficient even as screenshot
        # evidence. This test does not classify game UI or prove rendering.
        $colours=[System.Collections.Generic.HashSet[int]]::new()
        for($y=0;$y -lt 12;$y++){
            for($x=0;$x -lt 12;$x++){
                $px=[int][Math]::Floor(($x+0.5)*$width/12)
                $py=[int][Math]::Floor(($y+0.5)*$height/12)
                [void]$colours.Add($bitmap.GetPixel($px,$py).ToArgb())
            }
        }
        if($colours.Count -lt 5) {
            throw 'R37_BLOCKED: captured window appears uniformly blank'
        }
        $bitmap.Save($file,[Drawing.Imaging.ImageFormat]::Png)
    } finally {
        if($graphics){$graphics.Dispose()}
        $bitmap.Dispose()
    }
    $hash=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    return @{
        File=$file
        Sha256=$hash
        SampleColourCount=$colours.Count
        Width=$width
        Height=$height
        Title=$child.MainWindowTitle
        OwnerPid=$child.Id
    }
}
function Port-Free {
    foreach($port in @(43594,43595)) {
        $probe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,[int]$port)
        try { $probe.Start() } finally { $probe.Stop() }
    }
}
function Owned-Listeners([int]$ownedPid) {
    $found=@(Get-NetTCPConnection -State Listen -ErrorAction Stop |
        Where-Object { $_.LocalPort -in @(43594,43595) -and
                        $_.LocalAddress -eq '127.0.0.1' })
    if($found.Count -ne 2) { return $false }
    foreach($row in $found) {
        if($row.OwningProcess -ne $ownedPid) { return $false }
    }
    return @($found|Select-Object -ExpandProperty LocalPort -Unique).Count -eq 2
}
function Local-Aux {
    $req=[Net.HttpWebRequest]::Create(
        'http://127.0.0.1:43595/spk_live/versions.txt'
    )
    $req.Proxy=$null
    $req.Timeout=1500
    $req.ReadWriteTimeout=1500
    $resp=$req.GetResponse()
    try {
        if([int]$resp.StatusCode -ne 200) { return $false }
        $stream=[IO.StreamReader]::new(
            $resp.GetResponseStream(),[Text.Encoding]::ASCII)
        try { $body=$stream.ReadToEnd() } finally { $stream.Dispose() }
        return ($body.Length -gt 0 -and $body.Length -lt 16384 -and
                $body.Contains('cache_version') -and
                $body.Contains('sprite_version') -and
                $body.Contains('config_version'))
    } finally { $resp.Dispose() }
}
function Start-PrivateJava($arguments, $work, [bool]$pipeStdin) {
    $psi=[Diagnostics.ProcessStartInfo]::new()
    $psi.FileName=$java
    $psi.Arguments=$arguments
    $psi.WorkingDirectory=$work
    $psi.UseShellExecute=$false
    $psi.CreateNoWindow=$false
    $psi.RedirectStandardInput=$pipeStdin
    # Inherit output to the guest console; do not risk blocking JVM stdout.
    $psi.RedirectStandardOutput=$false
    $psi.RedirectStandardError=$false
    $psi.EnvironmentVariables['APPDATA']=$work
    $psi.EnvironmentVariables['LOCALAPPDATA']=$work
    $psi.EnvironmentVariables['HOME']=$work
    $psi.EnvironmentVariables['USERPROFILE']=$work
    $proc=[Diagnostics.Process]::new()
    $proc.StartInfo=$psi
    if(-not $proc.Start()) { throw 'R27_BLOCKED: owned child start refused' }
    return $proc
}
try {
    if($env:USERNAME -cne 'WDAGUtilityAccount') {
        throw 'R27_BLOCKED: script must be run in actual Windows Sandbox guest'
    }
    if(-not [IO.Path]::IsPathRooted($GuestHome) -or
       -not (Test-Path -LiteralPath $GuestHome -PathType Container)) {
        throw 'R27_BLOCKED: no absolute R25 private guest home'
    }
    $guestHomePath=[IO.Path]::GetFullPath($GuestHome).TrimEnd('\')
    $temp=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
    if(-not $temp.StartsWith('C:\Users\WDAGUtilityAccount\',
                           [StringComparison]::OrdinalIgnoreCase) -or
       -not [string]::Equals(
            [IO.Path]::GetDirectoryName($guestHomePath),$temp,
            [StringComparison]::OrdinalIgnoreCase) -or
       -not [IO.Path]::GetFileName($guestHomePath).StartsWith(
            'spawnpk-r25-guesthome-',[StringComparison]::Ordinal)) {
        throw 'R27_BLOCKED: guest HOME not a canonical R25 temporary home'
    }
    $item=Get-Item -LiteralPath $guestHomePath -ErrorAction Stop
    if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'R27_BLOCKED: HOME reparse point not permitted'
    }

    $r25=Get-Content -LiteralPath (
        Join-Path $guestHomePath 'r25-private-guest-home-receipt.json'
    ) -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    if($r25.result -cne 'R25_GUEST_PRIVATE_WRITABLE_HOME_PREPARED_NO_GUI' -or
       $r25.guest_home -cne $guestHomePath -or
       $r25.copied_static_cache_files -ne 10764 -or
       $r25.original_live_user_profile_mapped -cne $false) {
        throw 'R27_BLOCKED: private HOME receipt invalid'
    }
    # The R26 guest result must come from an actual earlier guest-local
    # server/loopback acceptance, NOT the prior host-only R26 test.
    $r26=Get-Content -LiteralPath (
        Join-Path $guestHomePath 'r26-owned-server-smoke-receipt.json'
    ) -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    if($r26.result -cne 'R26_GUEST_OWNED_LOCALLAB_SERVER_LOOPBACK_SMOKE_PASS' -or
       $r26.guest_home -cne $guestHomePath -or
       $r26.both_ports_released -cne $true -or
       $r26.graphical_client_started -cne $false) {
        throw 'R27_BLOCKED: real R26 guest server receipt missing or invalid'
    }
    # Must re-check effective guest network/loopback and all staged hashes
    # on EVERY invocation, even in plan mode.
    $preflight=Join-Path $assets 'guest-cache-preflight.ps1'
    if(-not (Test-Path -LiteralPath $preflight -PathType Leaf)) {
        throw 'R27_BLOCKED: R23/R24 preflight unavailable'
    }
    & powershell.exe -NoProfile -NonInteractive -File $preflight
    if($LASTEXITCODE -ne 0) {
        throw 'R27_BLOCKED: guest network/cache/loopback preflight failed'
    }
    $pins=Get-Content -LiteralPath (
        Join-Path $assets 'asset-pins.json'
    ) -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    $realClient=(Get-FileHash -LiteralPath $clientJar -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
    $realServer=(Get-FileHash -LiteralPath $serverJar -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
    $realJava=(Get-FileHash -LiteralPath $java -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
    if($realClient -cne $expectedClient -or
       $realServer -cne $pins.server_jar_sha256 -or
       $realJava -cne $expectedJava) {
        throw 'R27_BLOCKED: client/server/private-JDK binary SHA pin failure'
    }
    $audit=Get-Content -LiteralPath (
        Join-Path $assets 'r27-v308-gui-bootstrap-audit.json'
    ) -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    if($audit.result -cne 'R27_PINNED_V308_REAL_GUI_BOOTSTRAP_STATIC_AUDIT_PASS' -or
       $audit.certified_airgap_v308_sha256 -cne $expectedClient -or
       $audit.main_class -cne 'rs.gui.Launcher' -or
       $audit.client_gui_started -cne $false) {
        throw 'R27_BLOCKED: pinned static GUI bootstrap audit missing'
    }
    Port-Free
    if(-not $Launch) {
        if($CaptureFrame){
            throw 'R37_BLOCKED: frame capture requires explicit -Launch and approval'
        }
        Write-Output 'R27_GUEST_GUI_LAUNCH_PLAN_PASS_NO_CLIENT_OR_SERVER_STARTED'
        exit 0
    }
    if($ExactApproval -cne $approval) {
        throw 'R27_BLOCKED: exact manual GUI launch approval missing'
    }
    $name=Join-Path $guestHomePath 'data\accounts\r27-local-only-synthetic.properties'
    if(Test-Path -LiteralPath $name) {
        throw 'R27_BLOCKED: synthetic test account path already exists'
    }
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($name))
    $random=[byte[]]::new(32)
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($random) } finally { $rng.Dispose() }
    $token=([BitConverter]::ToString($random)).Replace('-','').ToLowerInvariant()
    $serverArgs=(
        '"-Duser.home='+$guestHomePath+'" '+
        '-Djava.net.preferIPv4Stack=true '+
        '"-Dspk.local.accountFile='+$name+'" '+
        '-Dspk.local.acceptance.stdinShutdownEnabled=true '+
        '-Dspk.local.acceptance.stdinShutdownToken='+$token+' '+
        '-jar "'+$serverJar+'" --bootstrap --movement'
    )
    $server=Start-PrivateJava $serverArgs $guestHomePath $true
    $serverStarted=$true
    $deadline=[DateTime]::UtcNow.AddSeconds(25)
    while([DateTime]::UtcNow -lt $deadline) {
        if($server.HasExited) {
            throw 'R27_BLOCKED: owned server stopped before binding'
        }
        try {
            if((Owned-Listeners $server.Id) -and (Local-Aux)) {
                $serverWasReady=$true
                break
            }
        } catch [Net.WebException] {
        }
        Start-Sleep -Milliseconds 150
    }
    if(-not $serverWasReady) {
        throw 'R27_BLOCKED: game/AUX listeners never became ready'
    }

    # The manifest in the hash-pinned JAR points to rs.gui.Launcher, whose
    # Guice bootstrap creates the Swing UI. No command-line switches,
    # login credentials, updater URL or external host are passed to it.
    $clientArgs=(
        '"-Duser.home='+$guestHomePath+'" -Djava.awt.headless=false '+
        '-Djava.net.preferIPv4Stack=true -jar "'+$clientJar+'"'
    )
    $client=Start-PrivateJava $clientArgs $guestHomePath $false
    $clientStarted=$true
    $deadline=[DateTime]::UtcNow.AddSeconds($ObserveSeconds)
    while([DateTime]::UtcNow -lt $deadline) {
        $client.Refresh()
        if($client.HasExited) { break }
        if($client.MainWindowHandle -ne [IntPtr]::Zero) {
            $windowSeen=$true
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if(-not $windowSeen) {
        throw 'R27_BLOCKED: no visible GUI top-level window observed'
    }
    # Do not attempt an account login or automate input. This is a
    # bounded visible-window smoke, NOT authentic game protocol acceptance.
    Start-Sleep -Seconds 5
    $client.Refresh()
    if($client.HasExited) {
        throw 'R27_BLOCKED: game window child exited during observation'
    }
    if($CaptureFrame){
        $evidence=Capture-GuestOwnedClientWindow $client $guestHomePath
        $framePath=$evidence.File
        $frameSha=$evidence.Sha256
        $frameColours=$evidence.SampleColourCount
        $capturedWindowTitle=$evidence.Title
        $frameSaved=$true
        Write-Output ('R37_GUEST_WINDOW_FRAME_CAPTURED sha256='+$frameSha+
            ' width='+$evidence.Width+' height='+$evidence.Height+
            ' sampledColors='+$frameColours)
    }
    Write-Output 'R27_GUEST_ORIGINAL_GUI_TOPLEVEL_WINDOW_OBSERVED'
} catch {
    Write-Output ('R27_GUEST_GUI_BOOTSTRAP_BLOCKED: '+$_.Exception.Message)
} finally {
    # STOP ONLY process objects this invocation started inside the guest.
    if($clientStarted -and $client) {
        try {
            $client.Refresh()
            if(-not $client.HasExited) {
                [void]$client.CloseMainWindow()
                if(-not $client.WaitForExit(5000)) {
                    $client.Kill()
                    $forcedClientStop=$true
                    [void]$client.WaitForExit(5000)
                }
            }
            $clientWasStopped=$client.HasExited
        } catch { $clientWasStopped=$false }
        $client.Dispose()
    }
    if($serverStarted -and $server) {
        try {
            if(-not $server.HasExited) {
                $server.StandardInput.WriteLine($token)
                $server.StandardInput.Flush()
                if(-not $server.WaitForExit(20000)) {
                    $server.Kill()
                    $forcedServerStop=$true
                    [void]$server.WaitForExit(5000)
                }
            }
            $serverWasStopped=$server.HasExited
        } catch { $serverWasStopped=$false }
        $server.Dispose()
    }
}
# Only a fully observed/clean own-client/server cycle can PASS.
if($windowSeen -and $clientWasStopped -and $serverWasStopped -and
   -not $forcedClientStop -and -not $forcedServerStop -and
   (-not $CaptureFrame -or $frameSaved)) {
    try { Port-Free } catch { exit 2 }
    if($CaptureFrame){
        if(-not (Test-Path -LiteralPath $framePath -PathType Leaf)){
            exit 2
        }
        # Only a clean OWNED process exit grants an evidence receipt.
        $frameReceipt=[ordered]@{
            result='R37_GUEST_WINDOW_FRAME_EVIDENCE_CAPTURED'
            guest_home=$guestHomePath
            screenshot_path=$framePath
            screenshot_sha256=$frameSha
            distinct_sample_colours=$frameColours
            owned_client_window=$true
            client_window_title=$capturedWindowTitle
            client_gui_frame_seen=$true
            original_unmodified_v308_client_started=$false
            authentic_login_verified=$false
            original_game_rendering_verified=$false
            gameplay_verified=$false
            network_isolation_preflight_passed=$true
            clean_client_server_shutdown=$true
        }
        $receiptFile=Join-Path $guestHomePath 'r37-guest-gui-frame-receipt.json'
        if(Test-Path -LiteralPath $receiptFile){exit 2}
        $frameReceipt|ConvertTo-Json -Depth 4|
            Set-Content -LiteralPath $receiptFile -Encoding UTF8
        Write-Output 'R37_GUEST_WINDOW_FRAME_EVIDENCE_AND_CLEANUP_PASS'
    }
    Write-Output 'R27_GUEST_GUI_WINDOW_AND_OWNED_PROCESS_CLEANUP_PASS'
    Write-Output 'R27_NOT_AUTHENTIC_LOGIN_MOVEMENT_OR_GAMEPLAY'
    exit 0
}
exit 2
