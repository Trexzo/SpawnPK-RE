# R38: pure trace parser behavioral tests, run on Windows WITHOUT any GUI,
# Java, network listener, Sandbox feature, or changes to local client state.
$ErrorActionPreference='Stop'
$source=Get-Content -LiteralPath (
    Join-Path $PSScriptRoot 'r27_guest_gui_window_smoke.ps1'
) -Raw -ErrorAction Stop
$start=$source.IndexOf('function Read-R38LocalGameTrace(')
$end=$source.IndexOf('function Test-R38OwnedClientGameConnection',$start)
if($start -lt 0 -or $end -le $start){throw 'R38 test parser source unavailable'}
$fragment=$source.Substring($start,$end-$start)
. ([scriptblock]::Create($fragment))
$path=[IO.Path]::GetTempFileName()
$assertions=0
function Require-R38([bool]$condition,[string]$why){
    $script:assertions++
    if(-not $condition){throw ('R38 parser assertion failed: '+$why)}
}
$positive=@'
[session /127.0.0.1:43210] CLIENT_PACKET seq=1 opcode=185 len=2 widget=912 schema=EXACT_V308_INITIAL_WIDGET
[session /127.0.0.1:43210] V512_WORLD_REGISTER playerId=42 generation=1 username=opensrc members=1 worldIdentity=500
[session /127.0.0.1:43210] LOGIN_SUCCESS_LOCAL rank=205 localDevAuthority=true flag=false persistent=true at 2026-11-13T10:00:00Z
[session /127.0.0.1:43210] M5_MOVEMENT_REQUEST MovementRequest{opcode=164, run=false, waypoints=[3088,3495]} action=ACCEPTED queued=1 final=3088,3495 packetRun=0 persistentRun=0 effectiveRun=0
[world player=42] M5_AUTHORITATIVE_TICK mode=WALK tiles=1 from=3087,3495 to=3088,3495 dir=4 combatFacing=NONE remaining=0 movementTick=1 worldTick=3
'@
try{
    [IO.File]::WriteAllText($path,$positive)
    $result=Read-R38LocalGameTrace $path
    Require-R38 $result.Verified 'same peer exact trace PASS'
    Require-R38 ($result.PlayerId -eq 42) 'exact WorldPlayer registration bound'
    Require-R38 ($result.Peer -ceq '127.0.0.1:43210') 'session endpoint bound'
    Require-R38 ($result.FromX -eq 3087 -and $result.ToX -eq 3088) 'movement verified'

    $negative=@(
        @{why='wrong first widget';text=$positive.Replace('widget=912','widget=152')},
        @{why='nonpersistent login';text=$positive.Replace('persistent=true','persistent=false')},
        @{why='route rejected';text=$positive.Replace('action=ACCEPTED','action=REJECTED')},
        @{why='no actual displacement';text=$positive.Replace('to=3088,3495 dir=4','to=3087,3495 dir=4')},
        @{why='different session sends movement';text=$positive.Replace(
            '[session /127.0.0.1:43210] M5_MOVEMENT_REQUEST',
            '[session /127.0.0.1:43211] M5_MOVEMENT_REQUEST')},
        @{why='different WorldPlayer walks';text=$positive.Replace(
            '[world player=42]','[world player=43]')},
        @{why='different login session';text=$positive.Replace(
            '[session /127.0.0.1:43210] LOGIN_SUCCESS_LOCAL',
            '[session /127.0.0.1:43211] LOGIN_SUCCESS_LOCAL')},
        @{why='wrong session registration';text=$positive.Replace(
            '[session /127.0.0.1:43210] V512_WORLD_REGISTER',
            '[session /127.0.0.1:43211] V512_WORLD_REGISTER')}
    )
    foreach($case in $negative){
        [IO.File]::WriteAllText($path,$case.text)
        $proof=Read-R38LocalGameTrace $path
        Require-R38 (-not $proof.Verified) $case.why
    }
    $order=$positive.Split([char]10)
    $swap=$order[0]+[char]10+$order[1]+[char]10+$order[3]+[char]10+$order[2]+[char]10+$order[4]
    [IO.File]::WriteAllText($path,$swap)
    Require-R38 (-not (Read-R38LocalGameTrace $path).Verified) 'out-of-order login/movement refused'

    [IO.File]::WriteAllText($path,'no protocol activity')
    Require-R38 (-not (Read-R38LocalGameTrace $path).Verified) 'empty unrelated stdout refused'

    # The real R38 guest process writes stdout while the host-side
    # parser polls it. Prove that a SHARED OPEN log can be read without
    # a sharing violation, and that a partial fragment cannot PASS.
    $openLog=[IO.FileStream]::new($path,[IO.FileMode]::Create,
        [IO.FileAccess]::Write,[IO.FileShare]::ReadWrite,
        1,[IO.FileOptions]::Asynchronous)
    try{
        $half=[int][Math]::Floor($positive.Length/2)
        $first=[Text.Encoding]::UTF8.GetBytes($positive.Substring(0,$half))
        $second=[Text.Encoding]::UTF8.GetBytes($positive.Substring($half))
        $openLog.Write($first,0,$first.Length)
        $openLog.Flush()
        Require-R38 (-not (Read-R38LocalGameTrace $path).Verified) 'live partial stdout fails closed'
        $openLog.Write($second,0,$second.Length)
        $openLog.Flush()
        Require-R38 (Read-R38LocalGameTrace $path).Verified 'live shared stdout read before writer closes'
    } finally { $openLog.Dispose() }

    Write-Output ('R38_SYNTHETIC_LOCAL_SERVER_TRACE_PARSER_PASS assertions='+
                  $assertions+' noJavaLaunched=true onlyOwnedTempFile=true')
}finally{
    if(Test-Path -LiteralPath $path){[IO.File]::Delete($path)}
}
