# P5.50 - Win32 directory identity capability proof.
# Read-only capability plus isolated temporary fixtures: no change to
# G21.171, World save admission, source kill grants or release policy.
[CmdletBinding()]
param([string]$OutputPath = "windows-native-directory-id-evidence.json")
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (-not $IsWindows) { throw "P550_REQUIRES_WINDOWS" }

$native = @'
using System;
using System.IO;
using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

public static class SpkP550DirectoryId
{
    const uint BACKUP_SEMANTICS = 0x02000000;
    const uint OPEN_REPARSE_POINT = 0x00200000;
    const uint ATTR_DIRECTORY = 0x00000010;
    const uint ATTR_REPARSE_POINT = 0x00000400;

    [StructLayout(LayoutKind.Sequential)]
    internal struct Information
    {
        internal uint attributes, creationLow, creationHigh,
            accessLow, accessHigh, writeLow, writeHigh,
            volumeSerial, sizeHigh, sizeLow, links,
            indexHigh, indexLow;
    }
    [DllImport("kernel32.dll", EntryPoint="CreateFileW",
        SetLastError=true, CharSet=CharSet.Unicode)]
    static extern SafeFileHandle CreateFile(
        string name, uint access, FileShare share, IntPtr security,
        FileMode creation, uint flags, IntPtr template);
    [DllImport("kernel32.dll", SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    static extern bool GetFileInformationByHandle(
        SafeFileHandle file, out Information value);

    public sealed class Token
    {
        internal readonly uint Volume;
        internal readonly ulong Index;
        public readonly bool Directory;
        public readonly bool Reparse;
        public readonly bool Available;
        internal Token(Information a)
        {
            Volume=a.volumeSerial;
            Index=((ulong)a.indexHigh << 32) | a.indexLow;
            Directory=(a.attributes & ATTR_DIRECTORY)!=0;
            Reparse=(a.attributes & ATTR_REPARSE_POINT)!=0;
            Available=Index!=0;
        }
    }
    public static Token ObserveNoFollow(string path)
    {
        // OPEN_REPARSE_POINT observes link metadata, not the target.
        using(SafeFileHandle h=CreateFile(path,0,
            FileShare.ReadWrite | FileShare.Delete,
            IntPtr.Zero,FileMode.Open,
            BACKUP_SEMANTICS | OPEN_REPARSE_POINT,IntPtr.Zero))
        {
            if(h.IsInvalid)throw new Win32Exception(
                Marshal.GetLastWin32Error(),"P550_CreateFileW_failed");
            Information i;
            if(!GetFileInformationByHandle(h,out i))
                throw new Win32Exception(
                    Marshal.GetLastWin32Error(),
                    "P550_GetFileInformationByHandle_failed");
            return new Token(i);
        }
    }
    public static bool Same(Token a,Token b)
    {
        return a!=null && b!=null && a.Available && b.Available &&
            a.Volume==b.Volume && a.Index==b.Index;
    }
}
'@
Add-Type -TypeDefinition $native -Language CSharp -ErrorAction Stop
$root = Join-Path (Get-Location).Path ("spk-p550-win32-dir-id-" +
    [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null
$report = [ordered]@{
    formatVersion = 1
    type = "SPK_P550_WIN32_DIRECTORY_IDENTITY_CAPABILITY_ONLY"
    probeApi = "CreateFileW_NOFOLLOW_GetFileInformationByHandle"
    nativeDirectoryIdentityAvailable = $false
    allSelectedAncestorsHaveNativeIdentity = $false
    observedNativeAncestorCount = 0
    reopenStable = $false
    distinctDirectoriesHaveDistinctIds = $false
    renamedDirectoryPreservesId = $false
    samePathReplacementChangesId = $false
    ordinaryFileNotDirectory = $false
    symlinkCreationSupported = $false
    symlinkReparseObserved = $false
    productionG21171Unchanged = $true
    positiveWindowsWorldSaveCertified = $false
    nativeIdentityFencingIntegrated = $false
    crashDurabilityCertified = $false
    crossJvmLeaseCertified = $false
}
try {
    $a = Join-Path $root "alpha"
    $b = Join-Path $root "beta"
    New-Item -ItemType Directory -Path $a | Out-Null
    New-Item -ItemType Directory -Path $b | Out-Null
    $first = [SpkP550DirectoryId]::ObserveNoFollow($a)
    $again = [SpkP550DirectoryId]::ObserveNoFollow($a)
    $other = [SpkP550DirectoryId]::ObserveNoFollow($b)
    if (-not $first.Available -or -not $first.Directory -or $first.Reparse -or
        -not $other.Available -or -not $other.Directory -or $other.Reparse) {
        throw "P550_NATIVE_DIRECTORY_ID_UNAVAILABLE"
    }
    $report.nativeDirectoryIdentityAvailable = $true

    # P5.49 showed Java fileKey is null for EVERY directory
    # from the workspace to D:\\ on Windows. Independently inspect
    # those exact native parent paths, not only our two test leaves.
    # Handle identity is a capability observation, not a lease.
    $cursor = [IO.Path]::GetFullPath($a)
    $depth = 0
    while ($true) {
        $parentToken = [SpkP550DirectoryId]::ObserveNoFollow($cursor)
        if (-not $parentToken.Available -or
            -not $parentToken.Directory -or $parentToken.Reparse) {
            throw ("P550_NATIVE_ANCESTOR_ID_UNAVAILABLE_OR_REDIRECTED depth=" +
                $depth + " path=" + $cursor)
        }
        Write-Host ("P550_NATIVE_ANCESTOR_OBSERVED depth=" + $depth +
            " path=" + $cursor +
            " handleIdAvailable=true directory=true reparse=false")
        $depth++
        $parentDir = [IO.Directory]::GetParent($cursor)
        if ($null -eq $parentDir) { break }
        $cursor = $parentDir.FullName
    }
    $report.observedNativeAncestorCount = $depth
    $report.allSelectedAncestorsHaveNativeIdentity = ($depth -ge 4)
    if (-not $report.allSelectedAncestorsHaveNativeIdentity) {
        throw "P550_NATIVE_ANCESTRY_EVIDENCE_INCOMPLETE"
    }
    $report.reopenStable = [SpkP550DirectoryId]::Same($first,$again)
    $report.distinctDirectoriesHaveDistinctIds =
        -not [SpkP550DirectoryId]::Same($first,$other)
    if (-not $report.reopenStable -or
        -not $report.distinctDirectoriesHaveDistinctIds) {
        throw "P550_UNSTABLE_OR_COLLIDING_WIN32_DIRECTORY_ID"
    }

    $moved = Join-Path $root "alpha-moved"
    Move-Item -LiteralPath $a -Destination $moved -ErrorAction Stop
    $renamed = [SpkP550DirectoryId]::ObserveNoFollow($moved)
    New-Item -ItemType Directory -Path $a | Out-Null
    $recreated = [SpkP550DirectoryId]::ObserveNoFollow($a)
    $report.renamedDirectoryPreservesId =
        [SpkP550DirectoryId]::Same($first,$renamed)
    $report.samePathReplacementChangesId =
        -not [SpkP550DirectoryId]::Same($first,$recreated)
    if (-not $report.renamedDirectoryPreservesId -or
        -not $report.samePathReplacementChangesId) {
        throw "P550_DIRECTORY_RENAME_REPLACEMENT_NOT_DETECTED"
    }

    $plain = Join-Path $root "ordinary-file"
    [IO.File]::WriteAllBytes($plain,[byte[]](1,2,3,4))
    $observedFile = [SpkP550DirectoryId]::ObserveNoFollow($plain)
    $report.ordinaryFileNotDirectory =
        $observedFile.Available -and -not $observedFile.Directory -and
        -not $observedFile.Reparse
    if (-not $report.ordinaryFileNotDirectory) {
        throw "P550_FILE_INCORRECTLY_CLASSIFIED_DIRECTORY"
    }

    $link = Join-Path $root "alpha-link"
    try {
        New-Item -ItemType SymbolicLink -Path $link -Target $a -ErrorAction Stop | Out-Null
        $report.symlinkCreationSupported = $true
    } catch {
        Write-Host ("P550_SYMLINK_FIXTURE_UNAVAILABLE exception=" +
            $_.Exception.GetType().Name + " noSymlinkSafetyClaim=true")
    }
    if ($report.symlinkCreationSupported) {
        $linked = [SpkP550DirectoryId]::ObserveNoFollow($link)
        $report.symlinkReparseObserved = $linked.Reparse
        if (-not $report.symlinkReparseObserved) {
            throw "P550_NOFOLLOW_DID_NOT_OBSERVE_REPARSE_POINT"
        }
    }

    $output = [IO.Path]::GetFullPath($OutputPath)
    $report | ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath $output -Encoding utf8
    Write-Host ("P550_WIN32_NATIVE_DIRECTORY_IDENTITY_PASS" +
        " nativeIdAvailable=true" +
        " allAncestorsHaveNativeIds=" + $report.allSelectedAncestorsHaveNativeIdentity.ToString().ToLowerInvariant() +
        " ancestorCount=" + $report.observedNativeAncestorCount +
        " reopenStable=" + $report.reopenStable.ToString().ToLowerInvariant() +
        " distinctDirectories=true" +
        " renamePreservesId=" + $report.renamedDirectoryPreservesId.ToString().ToLowerInvariant() +
        " samePathReplacedIdDetected=" + $report.samePathReplacementChangesId.ToString().ToLowerInvariant() +
        " fileNotDirectory=true" +
        " symlinkTested=" + $report.symlinkCreationSupported.ToString().ToLowerInvariant() +
        " symlinkReparseObserved=" + $report.symlinkReparseObserved.ToString().ToLowerInvariant() +
        " productionG21171Unchanged=true" +
        " positiveWindowsWorldSaveCertified=false")
} finally {
    $link = Join-Path $root "alpha-link"
    if (Test-Path -LiteralPath $link) {
        Remove-Item -LiteralPath $link -Force -ErrorAction Stop
    }
    if (Test-Path -LiteralPath $root) {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction Stop
    }
}
