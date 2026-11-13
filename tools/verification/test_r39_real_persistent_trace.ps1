# R39: run real LocalSession / isolated World / private opensrc account
# through one genuine localhost socket, then validate its actual server
# stdout with the SAME pure R38 parser used in a future guest trial.
# Never launch the graphical/proprietary client or use real account data.
param(
    [string]$Java='java',
    [string]$Repository=(Join-Path $PSScriptRoot '..\..')
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath($Repository)
$server=Join-Path $repo 'server\build\SpawnPKLocalServer.jar'
$classes=Join-Path $repo 'server\build\classes\java\test'
$resource=Join-Path $repo 'server\build\resources\main'
$guestScript=Join-Path $repo 'tools\verification\r27_guest_gui_window_smoke.ps1'
if(-not (Test-Path -LiteralPath $server -PathType Leaf) -or
   -not (Test-Path -LiteralPath $classes -PathType Container) -or
   -not (Test-Path -LiteralPath $guestScript -PathType Leaf)) {
    throw 'R39 missing exact built server/test classes or guest parser source'
}
$source=Get-Content -LiteralPath $guestScript -Raw
$start=$source.IndexOf('function Read-R38LocalGameTrace(')
$end=$source.IndexOf('function Test-R38OwnedClientGameConnection',$start)
if($start -lt 0 -or $end -le $start) {
    throw 'R39 could not locate R38 guest trace parser function'
}
. ([scriptblock]::Create($source.Substring($start,$end-$start)))
$log=[IO.Path]::GetTempFileName()
try{
    $cp=$server+';'+$classes+';'+$resource
    # Java runner produces its own isolated World and ephemeral loopback
    # ServerSocket. This is a test source, NOT client.jar.
    & $Java -cp $cp spk.local.R39PersistentV308LoginWalkTraceTest *>
        $log
    if($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $log -Tail 20
        throw ('R39 LocalSession test failed with Java exit='+$LASTEXITCODE)
    }
    $entire=Get-Content -LiteralPath $log -Raw
    if(-not $entire.Contains('R39_PERSISTENT_V308_LOGIN_WALK_TRACE_PASS')) {
        throw 'R39 socket test emitted no final all-assertions pass'
    }
    $proof=Read-R38LocalGameTrace $log
    if(-not $proof.Verified) {
        Get-Content -LiteralPath $log -Tail 25
        throw ('R39 actual LocalSession log rejected by R38 guest parser: '+$proof.Reason)
    }
    if($proof.FromX -ne 3087 -or $proof.FromY -ne 3495 -or
       $proof.ToX -ne 3088 -or $proof.ToY -ne 3495 -or
       $proof.Tiles -ne 1 -or $proof.PlayerId -ne 1) {
        throw 'R39 real LocalSession generated unexpected first movement coordinates'
    }
    Write-Output ('R39_REAL_PERSISTENT_LOCAL_SESSION_TO_R38_PARSER_PASS'+
        ' player='+$proof.PlayerId+
        ' peer=ownedEphemeralLoopback'+
        ' from='+$proof.FromX+','+$proof.FromY+
        ' to='+$proof.ToX+','+$proof.ToY+
        ' exactlyOneWalk=true actualGuestGui=false')
}finally{
    if(Test-Path -LiteralPath $log){Remove-Item -LiteralPath $log -Force}
}
