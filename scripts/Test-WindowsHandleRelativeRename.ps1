# P5.53 — isolated Win32 handle-relative FILE_RENAME_INFO probe.
# Never invoked by Java World sessions. No release or account grant.
[CmdletBinding()]
param([string]$OutputPath="windows-handle-relative-rename-evidence.json")
Set-StrictMode -Version Latest
$ErrorActionPreference="Stop"
if(-not $IsWindows){throw "P553_WINDOWS_ONLY"}

$native=@'
using System;
using System.IO;
using System.Linq;
using System.Text;
using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

public static class SpkP553HandleRelativeRename
{
    const uint FILE_FLAG_BACKUP_SEMANTICS=0x02000000;
    const uint FILE_FLAG_OPEN_REPARSE_POINT=0x00200000;
    const uint FILE_ADD_FILE=0x00000002;
    const uint FILE_TRAVERSE=0x00000020;
    const uint FILE_READ_ATTRIBUTES=0x00000080;
    const uint DELETE_ACCESS=0x00010000;
    const uint DIRECTORY_ATTRIBUTE=0x00000010;
    const uint REPARSE_ATTRIBUTE=0x00000400;
    const int FileRenameInfo=3;
    const int FileIdInfo=18;

    [StructLayout(LayoutKind.Sequential)]
    struct Basic
    {
        internal uint attributes,c1,c2,a1,a2,w1,w2,serial,s1,s2,links,id1,id2;
    }
    [DllImport("kernel32.dll",EntryPoint="CreateFileW",
        SetLastError=true,CharSet=CharSet.Unicode)]
    static extern SafeFileHandle CreateFile(string path,uint access,
        FileShare share,IntPtr security,FileMode creation,uint flags,
        IntPtr template);
    [DllImport("kernel32.dll",SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    static extern bool GetFileInformationByHandleEx(
        SafeFileHandle handle,int cls,IntPtr buffer,uint length);
    [DllImport("kernel32.dll",SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    static extern bool GetFileInformationByHandle(
        SafeFileHandle handle,out Basic information);
    [DllImport("kernel32.dll",SetLastError=true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    static extern bool SetFileInformationByHandle(
        SafeFileHandle handle,int cls,IntPtr buffer,uint length);

    public sealed class FileIdentity
    {
        readonly ulong volume;
        readonly byte[] fileId;
        public readonly bool Directory,Reparse,Available;
        internal FileIdentity(ulong volume,byte[] fileId,uint flags)
        {
            this.volume=volume;this.fileId=fileId;
            Directory=(flags & DIRECTORY_ATTRIBUTE)!=0;
            Reparse=(flags & REPARSE_ATTRIBUTE)!=0;
            Available=fileId!=null && fileId.Length==16 &&
                fileId.Any(value=>value!=0);
        }
        public bool Same(FileIdentity other)
        {
            return other!=null && Available && other.Available &&
                volume==other.volume && fileId.SequenceEqual(other.fileId);
        }
    }

    static SafeFileHandle Open(string path,uint access,uint flags)
    {
        SafeFileHandle h=CreateFile(path,access,
            FileShare.ReadWrite|FileShare.Delete,IntPtr.Zero,
            FileMode.Open,flags,IntPtr.Zero);
        if(h.IsInvalid)
        {
            int error=Marshal.GetLastWin32Error();h.Dispose();
            throw new Win32Exception(error,"P553_CreateFileW_failed");
        }
        return h;
    }

    static FileIdentity Identity(SafeFileHandle h)
    {
        IntPtr mem=Marshal.AllocHGlobal(24);
        try
        {
            if(!GetFileInformationByHandleEx(h,FileIdInfo,mem,24))
                throw new Win32Exception(Marshal.GetLastWin32Error(),
                    "P553_FileIdInfo_failed");
            ulong volume=unchecked((ulong)Marshal.ReadInt64(mem,0));
            byte[] fileId=new byte[16];
            Marshal.Copy(IntPtr.Add(mem,8),fileId,0,16);
            Basic attributes;
            if(!GetFileInformationByHandle(h,out attributes))
                throw new Win32Exception(Marshal.GetLastWin32Error(),
                    "P553_Attributes_failed");
            return new FileIdentity(volume,fileId,attributes.attributes);
        }
        finally{Marshal.FreeHGlobal(mem);}
    }

    public sealed class HeldDirectory:IDisposable
    {
        readonly SafeFileHandle h;
        internal HeldDirectory(SafeFileHandle h){this.h=h;}
        public FileIdentity Id()
        {
            if(h.IsInvalid || h.IsClosed)
                throw new InvalidOperationException("P553_HandleClosed");
            return Identity(h);
        }
        internal IntPtr Raw {get{return h.DangerousGetHandle();}}
        public void Dispose(){h.Dispose();}
    }

    public static HeldDirectory HoldDirectory(string path)
    {
        SafeFileHandle h=Open(path,FILE_ADD_FILE|FILE_TRAVERSE|
            FILE_READ_ATTRIBUTES,
            FILE_FLAG_BACKUP_SEMANTICS|FILE_FLAG_OPEN_REPARSE_POINT);
        FileIdentity id=Identity(h);
        if(!id.Available || !id.Directory || id.Reparse)
        {
            h.Dispose();
            throw new InvalidOperationException("P553_UnsafeDirectory");
        }
        return new HeldDirectory(h);
    }

    public static FileIdentity ObserveDirectory(string path)
    {
        using(HeldDirectory d=HoldDirectory(path))return d.Id();
    }

    public static int MoveSourceRelativeToHeldDirectory(
        string source,HeldDirectory destination,string leafName)
    {
        // Restrict this test to a single basename. No path separators
        // are allowed in the relative FILE_RENAME_INFO FileName.
        if(String.IsNullOrEmpty(leafName) || leafName=="." || leafName==".." ||
            leafName.IndexOfAny(new[]{'/', '\\', ':'})>=0)
            throw new ArgumentException("P553_UnsafeRelativeLeaf");
        byte[] utf16=Encoding.Unicode.GetBytes(leafName);
        int rootOffset=IntPtr.Size==8?8:4;
        int nameLenOffset=rootOffset+IntPtr.Size;
        int nameOffset=nameLenOffset+4;
        int size=nameOffset+utf16.Length+2;
        IntPtr mem=Marshal.AllocHGlobal(size);
        try
        {
            // FILE_RENAME_INFO flags are zero: NEVER replace an
            // existing destination in this diagnostic.
            Marshal.Copy(new byte[size],0,mem,size);
            Marshal.WriteInt32(mem,0,0);
            Marshal.WriteIntPtr(mem,rootOffset,destination.Raw);
            Marshal.WriteInt32(mem,nameLenOffset,utf16.Length);
            Marshal.Copy(utf16,0,IntPtr.Add(mem,nameOffset),utf16.Length);

            // A DELETE-right file handle is necessary for rename.
            using(SafeFileHandle h=Open(source,
                DELETE_ACCESS|FILE_READ_ATTRIBUTES,0))
            {
                if(!SetFileInformationByHandle(h,FileRenameInfo,mem,
                    (uint)size))return Marshal.GetLastWin32Error();
                return 0;
            }
        }
        finally{Marshal.FreeHGlobal(mem);}
    }
}
'@
Add-Type -TypeDefinition $native -Language CSharp -ErrorAction Stop
$root=Join-Path (Get-Location).Path ("spk-p553-handle-rename-"+[Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null
$report=[ordered]@{
    formatVersion=1
    type="SPK_P553_HANDLE_RELATIVE_RENAME_TEST_ONLY"
    normalHeldDirectoryRenameSucceeded=$false
    normalFileContentRoundTrip=$false
    heldDirectoryIdentityStableAcrossPathReplacement=$false
    targetMovedIntoRetiredHeldDirectory=$false
    newlyRecreatedPathNotWritten=$false
    decoyFileUnmodified=$false
    existingTargetRejectedWithoutReplacement=$false
    sourcePreservedOnRejection=$false
    originalJavaG21171Unmodified=$true
    productionWorldSaveCertified=$false
    nativeWriterIntegrated=$false
    externalRawWriterAtomicFenceCertified=$false
    crashDurabilityCertified=$false
}
try {
    $stage=Join-Path $root "staged"
    $account=Join-Path $root "account"
    New-Item -ItemType Directory -Path $stage | Out-Null
    New-Item -ItemType Directory -Path $account | Out-Null
    $source1=Join-Path $stage "source-1.bin"
    $payload1=[Text.Encoding]::UTF8.GetBytes("P553-normal-payload")
    [IO.File]::WriteAllBytes($source1,$payload1)
    $held=[SpkP553HandleRelativeRename]::HoldDirectory($account)
    try {
        $originalId=$held.Id()
        $code=[SpkP553HandleRelativeRename]::MoveSourceRelativeToHeldDirectory(
            $source1,$held,"normal.bin")
        if($code -ne 0){throw ("P553_NORMAL_RELATIVE_RENAME_FAILED win32="+$code)}
        $normal=Join-Path $account "normal.bin"
        $report.normalHeldDirectoryRenameSucceeded=
            (Test-Path -LiteralPath $normal) -and
            -not (Test-Path -LiteralPath $source1)
        $report.normalFileContentRoundTrip=
            [Convert]::ToBase64String([IO.File]::ReadAllBytes($normal)) -ceq
            [Convert]::ToBase64String($payload1)
        if(-not $report.normalHeldDirectoryRenameSucceeded -or
            -not $report.normalFileContentRoundTrip) {
            throw "P553_NORMAL_POSITIVE_ROUNDTRIP_MISSING"
        }

        # The account pathname now moves while its genuine directory
        # handle is still held. Publish a second file relative to the
        # HELD directory, not the replacement pathname.
        $retired=Join-Path $root "account-retired"
        Move-Item -LiteralPath $account -Destination $retired -ErrorAction Stop
        $report.heldDirectoryIdentityStableAcrossPathReplacement=
            $originalId.Same($held.Id()) -and
            $originalId.Same(
                [SpkP553HandleRelativeRename]::ObserveDirectory($retired))
        New-Item -ItemType Directory -Path $account | Out-Null
        $decoy=Join-Path $account "decoy.bin"
        [IO.File]::WriteAllBytes($decoy,[Text.Encoding]::UTF8.GetBytes("P553-decoy"))
        $decoyBefore=[Convert]::ToBase64String([IO.File]::ReadAllBytes($decoy))
        if($originalId.Same(
            [SpkP553HandleRelativeRename]::ObserveDirectory($account))) {
            throw "P553_REPLACEMENT_SAME_NATIVE_DIRECTORY_ID"
        }
        $source2=Join-Path $stage "source-2.bin"
        $payload2=[Text.Encoding]::UTF8.GetBytes("P553-held-relative-payload")
        [IO.File]::WriteAllBytes($source2,$payload2)
        $code=[SpkP553HandleRelativeRename]::MoveSourceRelativeToHeldDirectory(
            $source2,$held,"held-only.bin")
        if($code -ne 0){throw ("P553_HELD_RELATIVE_RENAME_FAILED win32="+$code)}
        $heldTarget=Join-Path $retired "held-only.bin"
        $report.targetMovedIntoRetiredHeldDirectory=
            (Test-Path -LiteralPath $heldTarget) -and
            -not (Test-Path -LiteralPath $source2) -and
            ([Convert]::ToBase64String(
                [IO.File]::ReadAllBytes($heldTarget)) -ceq
                [Convert]::ToBase64String($payload2))
        $report.newlyRecreatedPathNotWritten=
            -not (Test-Path -LiteralPath (Join-Path $account "held-only.bin"))
        $report.decoyFileUnmodified=
            ([Convert]::ToBase64String([IO.File]::ReadAllBytes($decoy)) -ceq
            $decoyBefore)
        if(-not $report.heldDirectoryIdentityStableAcrossPathReplacement -or
            -not $report.targetMovedIntoRetiredHeldDirectory -or
            -not $report.newlyRecreatedPathNotWritten -or
            -not $report.decoyFileUnmodified) {
            throw "P553_PATH_REPLACEMENT_REDIRECT_OR_IDENTITY_BROKEN"
        }

        # Existing destination with ReplaceIfExists=FALSE must reject
        # without corrupting either the staged or published file.
        $source3=Join-Path $stage "source-3.bin"
        [IO.File]::WriteAllBytes($source3,[Text.Encoding]::UTF8.GetBytes("P553-should-not-move"))
        $normalBefore=[Convert]::ToBase64String(
            [IO.File]::ReadAllBytes((Join-Path $retired "normal.bin")))
        $conflict=[SpkP553HandleRelativeRename]::MoveSourceRelativeToHeldDirectory(
            $source3,$held,"normal.bin")
        $report.existingTargetRejectedWithoutReplacement=
            $conflict -ne 0 -and
            ([Convert]::ToBase64String(
                [IO.File]::ReadAllBytes((Join-Path $retired "normal.bin"))) -ceq
                $normalBefore)
        $report.sourcePreservedOnRejection=Test-Path -LiteralPath $source3
        if(-not $report.existingTargetRejectedWithoutReplacement -or
            -not $report.sourcePreservedOnRejection) {
            throw ("P553_EXISTING_TARGET_REPLACE_NOT_DENIED win32="+$conflict)
        }
    } finally { $held.Dispose() }

    $report | ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath ([IO.Path]::GetFullPath($OutputPath)) -Encoding utf8
    Write-Host ("P553_HANDLE_RELATIVE_RENAME_PASS "+
        "normalRealFileMove=true heldDirectorySurvivedPathReplace=true "+
        "relativeTargetBoundToHeldDirectory=true sameTextNewPathNotWritten=true "+
        "existingDestinationRejected=true sourcePreserved=true "+
        "productionWorldSaveCertified=false externalRawWriterAtomicFenceCertified=false")
} finally {
    if(Test-Path -LiteralPath $root) {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction Stop
    }
}
