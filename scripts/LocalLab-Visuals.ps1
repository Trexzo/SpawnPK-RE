Set-StrictMode -Version 2.0

function Write-LocalLabRule {
    param(
        [ConsoleColor]$Color = [ConsoleColor]::DarkGray
    )

    Write-Host ("-" * 68) -ForegroundColor $Color
}

function Write-LocalLabHeader {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Phase
    )

    Write-Host ""
    Write-LocalLabRule -Color DarkCyan
    Write-Host "  SPAWNPK LOCALLAB" -ForegroundColor Cyan -NoNewline
    Write-Host "  //  $Phase" -ForegroundColor White
    Write-Host "  local reconstruction + research workbench" -ForegroundColor DarkGray
    Write-LocalLabRule -Color DarkCyan
}

function Write-LocalLabStatus {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Label,

        [Parameter(Mandatory=$true)]
        [string]$Value,

        [ConsoleColor]$Color = [ConsoleColor]::Gray
    )

    Write-Host ("  [{0,-12}] " -f $Label) -ForegroundColor DarkGray -NoNewline
    Write-Host $Value -ForegroundColor $Color
}

function Write-LocalLabFlavor {
    param(
        [Parameter(Mandatory=$true)]
        [ValidateSet("Bootstrap","Import","Launch","Ready","SelfTest")]
        [string]$Stage
    )

    $line = switch ($Stage) {
        "Bootstrap" { "BING QILING :: build lane chilled; authority labels remain serious." }
        "Import"    { "CACHE GOBLIN :: external runtime only enters after SHA-256 tribute." }
        "Launch"    { "PACKET GREMLINS :: containment field warming up." }
        "Ready"     { "BING QILING :: localhost ice cream acquired." }
        "SelfTest"  { "179/179 OR WE ARE COOKED :: regression gate armed." }
    }

    Write-Host ("  > " + $line) -ForegroundColor Magenta
}

function Set-LocalLabWindowTitle {
    param(
        [Parameter(Mandatory=$true)]
        [string]$Title
    )

    try {
        $Host.UI.RawUI.WindowTitle = "SpawnPK LocalLab // $Title"
    }
    catch {
        # Non-interactive hosts may not expose a writable window title.
    }
}
