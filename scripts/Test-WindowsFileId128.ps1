# P5.51 — 128-bit Windows FileIdInfo, evidence only.
# No change to Java G21.171, source kill admission or World save.
[CmdletBinding()]
param([string]$OutputPath = "windows-fileid128-evidence.json")
Set-StrictMode -Version Latest
$ErrorActionPreference="Stop"
if (-not $IsWindows) { throw "P551_WINDOWS_ONLY" }
$native=@'
using System;
using System.IO;
using System.Linq;
using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;
public static class SpkP551FileId128
{
    const uint BACKUP=0x02000000, NOFOLLOW=0x00200000;
    const uint DIR=0x00000010, REPARSE=0x00000400;
    const int FileIdInfo=0x12;
    [StructLayout(LayoutKind.Sequential)]
    struct Basic
    {
        internal uint attributes,c1,c2,a1,a2,w1,w2,serial,s1,s2,nlinks,id1,id2;
    }
    [DllImport("kernel32.dll",EntryPoint="CreateFileW",
        SetLastError=true,CharSet=CharSet.Unicode)]
    static extern SafeFileHandle CreateFile(
        string path,uint access,FileShare share,IntPtr security,
        FileMode disposition,uint flags,IntPtr template);
    [DllImport("kernel32.dll",SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    static extern bool GetFileInformationByHandleEx(
        SafeFileHandle handle,int cls,IntPtr buffer,uint length);
    [DllImport("kernel32.dll",SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    static extern bool GetFileInformationByHandle(
        SafeFileHandle handle,out Basic info);

    public sealed class Id
    {
        internal readonly ulong Volume;
        internal readonly byte[] Bytes;
        public readonly bool Directory, Reparse, Available;
        internal Id(ulong volume,byte[] bytes,uint attributes)
        {
            Volume=volume;Bytes=bytes;
            Directory=(attributes & DIR)!=0;
            Reparse=(attributes & REPARSE)!=0;
            Available=bytes!=null && bytes.Length==16 &&
                bytes.Any(b=>b!=0);
        }
    }
    public sealed class Held : IDisposable
    {
        readonly SafeFileHandle handle;
        internal Held(SafeFileHandle handle) { this.handle=handle; }
        public Id Read()
        {
            if(handle.IsClosed || handle.IsInvalid)
                throw new InvalidOperationException("P551_HANDLE_NOT_HELD");
            // FILE_ID_INFO: 8-byte volume serial + 16-byte FileId128.
            IntPtr raw=Marshal.AllocHGlobal(24);
            try
            {
                if(!GetFileInformationByHandleEx(handle,FileIdInfo,raw,24))
                    throw new Win32Exception(
                        Marshal.GetLastWin32Error(),"P551_FileIdInfo_failed");
                ulong vol=unchecked((ulong)Marshal.ReadInt64(raw,0));
                byte[] id=new byte[16];
                Marshal.Copy(IntPtr.Add(raw,8),id,0,16);
                Basic basic;
                if(!GetFileInformationByHandle(handle,out basic))
                    throw new Win32Exception(
                        Marshal.GetLastWin32Error(),"P551_Attributes_failed");
                return new Id(vol,id,basic.attributes);
            } finally { Marshal.FreeHGlobal(raw); }
        }
        public void Dispose() { handle.Dispose(); }
    }
    public static Held Open(string path)
    {
        SafeFileHandle h=CreateFile(path,0,
            FileShare.ReadWrite | FileShare.Delete,IntPtr.Zero,
            FileMode.Open,BACKUP | NOFOLLOW,IntPtr.Zero);
        if(h.IsInvalid)
        {
            int error=Marshal.GetLastWin32Error();h.Dispose();
            throw new Win32Exception(error,"P551_CreateFileW_failed");
        }
        return new Held(h);
    }
    public static Id Observe(string path)
    {
        using(Held held=Open(path))return held.Read();
    }
    public static bool Same(Id a,Id b)
    {
        return a!=null && b!=null && a.Available && b.Available &&
            a.Volume==b.Volume && a.Bytes.SequenceEqual(b.Bytes);
    }
}
'@
Add-Type -TypeDefinition $native -Language CSharp -ErrorAction Stop
$root=Join-Path (Get-Location).Path ("spk-p551-fileid128-"+[Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null
$report=[ordered]@{
    formatVersion=1
    type="SPK_P551_FILEID128_CAPABILITY_ONLY"
    api="CreateFileW_NOFOLLOW_GetFileInformationByHandleEx_FileIdInfo"
    fileId128Available=$false
    ancestorIdsAvailable=$false
    ancestorCount=0
    reopenStable=$false
    distinctDirectoryIds=$false
    originalHandleStableAfterRename=$false
    renamedNameRetainsId=$false
    replacementAtSamePathDifferentId=$false
    # P5.52 two-level delayed World-save admission analog:
    # a preflighted parent and child must BOTH still be the
    # same native objects when their exact paths are reopened.
    twoLevelCutpointRounds=0
    unchangedTwoLevelChainAccepted=$false
    staleChildPathRejectedWithParentStable=$false
    staleParentPathRejectedAfterReplacement=$false
    originalHeldIdsStableThroughBothReplacements=$false
    regularFileNotDirectory=$false
    symlinkFixtureSupported=$false
    symlinkIsReparse=$false
    positiveWindowsWorldSaveCertified=$false
    nativeWriterFenceIntegrated=$false
    crossJvmOwnerLeaseCertified=$false
    crashDurabilityCertified=$false
}
try {
    $alpha=Join-Path $root "alpha"
    $beta=Join-Path $root "beta"
    New-Item -ItemType Directory -Path $alpha | Out-Null
    New-Item -ItemType Directory -Path $beta | Out-Null
    $held=[SpkP551FileId128]::Open($alpha)
    try {
        $first=$held.Read()
        $again=[SpkP551FileId128]::Observe($alpha)
        $other=[SpkP551FileId128]::Observe($beta)
        if (-not $first.Available -or -not $first.Directory -or $first.Reparse -or
            -not $other.Available -or -not $other.Directory -or $other.Reparse) {
            throw "P551_FILEID128_UNAVAILABLE_OR_UNSAFE"
        }
        $report.fileId128Available=$true
        $report.reopenStable=[SpkP551FileId128]::Same($first,$again)
        $report.distinctDirectoryIds=-not [SpkP551FileId128]::Same($first,$other)
        $cursor=[IO.Path]::GetFullPath($alpha)
        $count=0
        while ($true) {
            $id=[SpkP551FileId128]::Observe($cursor)
            if (-not $id.Available -or -not $id.Directory -or $id.Reparse) {
                throw ("P551_UNSAFE_ANCESTOR depth="+$count)
            }
            Write-Host ("P551_ANCESTOR_OBSERVED depth="+$count+
                " fileId128Available=true directory=true reparse=false")
            $count++
            $parent=[IO.Directory]::GetParent($cursor)
            if ($null -eq $parent) { break }
            $cursor=$parent.FullName
        }
        $report.ancestorCount=$count
        $report.ancestorIdsAvailable=($count -ge 4)
        $renamed=Join-Path $root "alpha-renamed"
        Move-Item -LiteralPath $alpha -Destination $renamed -ErrorAction Stop
        $originalHandle=$held.Read()
        $renamedId=[SpkP551FileId128]::Observe($renamed)
        $report.originalHandleStableAfterRename=[SpkP551FileId128]::Same($first,$originalHandle)
        $report.renamedNameRetainsId=[SpkP551FileId128]::Same($first,$renamedId)
        New-Item -ItemType Directory -Path $alpha | Out-Null
        $replacement=[SpkP551FileId128]::Observe($alpha)
        $report.replacementAtSamePathDifferentId=
            -not [SpkP551FileId128]::Same($first,$replacement)
    } finally { $held.Dispose() }
    # P5.52: two independent rename/replacement cutpoints per
    # isolated pair of nested directories. This is a deterministic
    # NEGATIVE admission simulation, not a real World file write,
    # and cannot fence another process after the final observation.
    for ($round=0; $round -lt 8; $round++) {
        $cut=Join-Path $root ("two-level-"+$round)
        $account=Join-Path $cut "account"
        $leaf=Join-Path $account "snapshot"
        New-Item -ItemType Directory -Path $cut | Out-Null
        New-Item -ItemType Directory -Path $account | Out-Null
        New-Item -ItemType Directory -Path $leaf | Out-Null
        $cutBaseline=[SpkP551FileId128]::Observe($cut)
        $parentHeld=[SpkP551FileId128]::Open($account)
        $leafHeld=[SpkP551FileId128]::Open($leaf)
        try {
            $parentBaseline=$parentHeld.Read()
            $leafBaseline=$leafHeld.Read()
            # Positive control: existing native path still points
            # to the same two genuine open objects, not just text.
            if (-not [SpkP551FileId128]::Same(
                    $parentBaseline,[SpkP551FileId128]::Observe($account)) -or
                -not [SpkP551FileId128]::Same(
                    $leafBaseline,[SpkP551FileId128]::Observe($leaf))) {
                throw ("P552_UNCHANGED_CHAIN_NOT_SAME round="+$round)
            }
            $report.unchangedTwoLevelChainAccepted=$true

            # First cutpoint: mutate only the leaf at its previous
            # exact pathname. Parent handle/path remain identical.
            $retiredLeaf=Join-Path $account "snapshot-retired"
            Move-Item -LiteralPath $leaf -Destination $retiredLeaf -ErrorAction Stop
            New-Item -ItemType Directory -Path $leaf | Out-Null
            $newLeaf=[SpkP551FileId128]::Observe($leaf)
            if (-not [SpkP551FileId128]::Same(
                    $parentBaseline,$parentHeld.Read()) -or
                -not [SpkP551FileId128]::Same(
                    $parentBaseline,[SpkP551FileId128]::Observe($account)) -or
                -not [SpkP551FileId128]::Same(
                    $leafBaseline,$leafHeld.Read()) -or
                [SpkP551FileId128]::Same($leafBaseline,$newLeaf)) {
                throw ("P552_STALE_CHILD_NOT_FENCED round="+$round)
            }
            $report.staleChildPathRejectedWithParentStable=$true

            # Dispose the retired descendant's handle before
            # renaming the nonempty parent directory on Windows.
            $leafHeld.Dispose()
            $leafHeld=$null

            # Second cutpoint: replace the parent at the exact
            # prior account path while the old parent handle stays
            # open. A child-only comparison cannot authorize now.
            $retiredParent=Join-Path $cut "account-retired"
            Move-Item -LiteralPath $account -Destination $retiredParent -ErrorAction Stop
            New-Item -ItemType Directory -Path $account | Out-Null
            New-Item -ItemType Directory -Path $leaf | Out-Null
            $newParent=[SpkP551FileId128]::Observe($account)
            $replacementChild=[SpkP551FileId128]::Observe($leaf)
            if (-not [SpkP551FileId128]::Same(
                    $parentBaseline,$parentHeld.Read()) -or
                [SpkP551FileId128]::Same($parentBaseline,$newParent) -or
                [SpkP551FileId128]::Same($leafBaseline,$replacementChild) -or
                -not [SpkP551FileId128]::Same(
                    $cutBaseline,[SpkP551FileId128]::Observe($cut))) {
                throw ("P552_STALE_PARENT_NOT_FENCED round="+$round)
            }
            $report.staleParentPathRejectedAfterReplacement=$true
            $report.originalHeldIdsStableThroughBothReplacements=$true
            $report.twoLevelCutpointRounds++
        } finally {
            if ($null -ne $leafHeld) { $leafHeld.Dispose() }
            $parentHeld.Dispose()
        }
    }
    Write-Host ("P552_TWO_LEVEL_FILEID128_CUTPOINT_PASS rounds="+
        $report.twoLevelCutpointRounds+
        " unchangedChainAccepted=true staleLeafRejected=true"+
        " staleParentRejected=true heldIdsStable=true"+
        " realWorldSaveNotAttempted=true atomicRawWriterFenceNotProven=true")

    $plain=Join-Path $root "plain-file"
    [IO.File]::WriteAllBytes($plain,[byte[]](1,2,3,4))
    $file=[SpkP551FileId128]::Observe($plain)
    $report.regularFileNotDirectory=
        $file.Available -and -not $file.Directory -and -not $file.Reparse
    $link=Join-Path $root "link"
    try {
        New-Item -ItemType SymbolicLink -Path $link -Target $alpha -ErrorAction Stop | Out-Null
        $report.symlinkFixtureSupported=$true
    } catch {
        Write-Host ("P551_SYMLINK_UNAVAILABLE reason="+
            $_.Exception.GetType().Name+" noSymlinkClaim=true")
    }
    if ($report.symlinkFixtureSupported) {
        $l=[SpkP551FileId128]::Observe($link)
        $report.symlinkIsReparse=$l.Reparse
    }
    if (-not $report.fileId128Available -or
        -not $report.ancestorIdsAvailable -or
        -not $report.reopenStable -or -not $report.distinctDirectoryIds -or
        -not $report.originalHandleStableAfterRename -or
        -not $report.renamedNameRetainsId -or
        -not $report.replacementAtSamePathDifferentId -or
        $report.twoLevelCutpointRounds -ne 8 -or
        -not $report.unchangedTwoLevelChainAccepted -or
        -not $report.staleChildPathRejectedWithParentStable -or
        -not $report.staleParentPathRejectedAfterReplacement -or
        -not $report.originalHeldIdsStableThroughBothReplacements -or
        -not $report.regularFileNotDirectory -or
        ($report.symlinkFixtureSupported -and -not $report.symlinkIsReparse)) {
        throw "P551_FILEID128_CAPABILITY_NEGATIVE"
    }
    $report | ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath ([IO.Path]::GetFullPath($OutputPath)) -Encoding utf8
    Write-Host ("P551_FILEID128_CAPABILITY_PASS ancestorCount="+$report.ancestorCount+
        " heldHandleStable=true samePathReplacementDetected=true"+
        " symlinkTested="+$report.symlinkFixtureSupported.ToString().ToLowerInvariant()+
        " javaWorldSaveAuthorized=false nativeWriterFenceIntegrated=false"+
        " crashDurabilityCertified=false")
} finally {
    $link=Join-Path $root "link"
    if (Test-Path -LiteralPath $link) {
        Remove-Item -LiteralPath $link -Force -ErrorAction Stop
    }
    if (Test-Path -LiteralPath $root) {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction Stop
    }
}
