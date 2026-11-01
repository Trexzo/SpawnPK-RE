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
        if(String.IsNullOrEmpty(leafName) || leafName=="." || leafName==".." ||
            leafName.IndexOfAny(new[]{'/', '\\', ':'})>=0)
            throw new ArgumentException("P553_UnsafeRelativeLeaf");
        return RenameInternal(source,destination.Raw,leafName);
    }

    public static int MoveSourceAbsoluteWithNullRoot(
        string source,string absoluteTarget)
    {
        if(!Path.IsPathRooted(absoluteTarget))
            throw new ArgumentException("P553_FullPathRequiredForNullRoot");
        return RenameInternal(source,IntPtr.Zero,absoluteTarget);
    }

    static int RenameInternal(string source,IntPtr rootDirectory,string name)
    {
        // A/B compares the exact SAME FileRenameInfo layout and file
        // operation, changing ONLY root handle + corresponding name.
        byte[] utf16=Encoding.Unicode.GetBytes(name);
        int rootOffset=IntPtr.Size==8?8:4;
        int lengthOffset=rootOffset+IntPtr.Size;
        int nameOffset=lengthOffset+4;
        int size=nameOffset+utf16.Length+2;
        IntPtr mem=Marshal.AllocHGlobal(size);
        try
        {
            Marshal.Copy(new byte[size],0,mem,size);
            Marshal.WriteInt32(mem,0,0); // ReplaceIfExists = false.
            Marshal.WriteIntPtr(mem,rootOffset,rootDirectory);
            Marshal.WriteInt32(mem,lengthOffset,utf16.Length);
            Marshal.Copy(utf16,0,IntPtr.Add(mem,nameOffset),utf16.Length);
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
    fullPathNullRootBaselineSucceeded=$false
    fullPathNullRootBytesVerified=$false
    rootedMoveSupported=$false
    rootedMoveDeniedWin32Error87=$false
    rootedUnsupportedAttemptNoMove=$false
    heldDirectoryIdentityStableAcrossPathReplacement=$false
    rootedTargetMovedIntoRetiredDirectory=$false
    unsupportedRootAfterPathSwapStillDenied=$false
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

    # A/B baseline: documented commonplace Win32 form uses
    # RootDirectory=NULL and a FULL absolute target pathname.
    $source1=Join-Path $stage "baseline-source.bin"
    $baseline=Join-Path $account "baseline.bin"
    $payload1=[Text.Encoding]::UTF8.GetBytes("P553-absolute-baseline")
    [IO.File]::WriteAllBytes($source1,$payload1)
    $result=[SpkP553HandleRelativeRename]::MoveSourceAbsoluteWithNullRoot(
        $source1,$baseline)
    if($result -ne 0) {
        throw ("P553_NULL_ROOT_BASELINE_FAILED win32="+$result)
    }
    $report.fullPathNullRootBaselineSucceeded=
        (Test-Path -LiteralPath $baseline) -and
        -not (Test-Path -LiteralPath $source1)
    $report.fullPathNullRootBytesVerified=
        [Convert]::ToBase64String([IO.File]::ReadAllBytes($baseline)) -ceq
        [Convert]::ToBase64String($payload1)
    if(-not $report.fullPathNullRootBaselineSucceeded -or
        -not $report.fullPathNullRootBytesVerified) {
        throw "P553_NULL_ROOT_POSITIVE_CONTROL_FAILED"
    }

    # Change ONLY the RootDirectory/name form: non-NULL held
    # directory root plus relative SINGLE leaf. A documented API
    # can still return ERROR_INVALID_PARAMETER 87 on the provider.
    $held=[SpkP553HandleRelativeRename]::HoldDirectory($account)
    try {
        $originalId=$held.Id()
        $source2=Join-Path $stage "rooted-source.bin"
        $payload2=[Text.Encoding]::UTF8.GetBytes("P553-rooted-candidate")
        [IO.File]::WriteAllBytes($source2,$payload2)
        $rootedPath=Join-Path $account "rooted.bin"
        $rootCode=[SpkP553HandleRelativeRename]::MoveSourceRelativeToHeldDirectory(
            $source2,$held,"rooted.bin")
        if($rootCode -eq 0) {
            $report.rootedMoveSupported=
                (Test-Path -LiteralPath $rootedPath) -and
                -not (Test-Path -LiteralPath $source2) -and
                ([Convert]::ToBase64String(
                    [IO.File]::ReadAllBytes($rootedPath)) -ceq
                    [Convert]::ToBase64String($payload2))
            if(-not $report.rootedMoveSupported) {
                throw "P553_ROOTED_MOVE_RETURNED_SUCCESS_WITHOUT_BYTES"
            }
        } elseif ($rootCode -eq 87) {
            $report.rootedMoveDeniedWin32Error87=$true
            $report.rootedUnsupportedAttemptNoMove=
                (Test-Path -LiteralPath $source2) -and
                -not (Test-Path -LiteralPath $rootedPath)
            if(-not $report.rootedUnsupportedAttemptNoMove) {
                throw "P553_ERROR87_ALREADY_MUTATED_FILES"
            }
        } else {
            throw ("P553_ROOT_DIRECTORY_UNCLASSIFIED_WIN32_ERROR="+$rootCode)
        }

        # Delayed pathname replacement with the original directory
        # handle still open. No write can be called authoritative
        # merely because a stale path string still exists.
        $retired=Join-Path $root "account-retired"
        Move-Item -LiteralPath $account -Destination $retired -ErrorAction Stop
        $report.heldDirectoryIdentityStableAcrossPathReplacement=
            $originalId.Same($held.Id()) -and
            $originalId.Same(
                [SpkP553HandleRelativeRename]::ObserveDirectory($retired))
        New-Item -ItemType Directory -Path $account | Out-Null
        if($originalId.Same(
            [SpkP553HandleRelativeRename]::ObserveDirectory($account))) {
            throw "P553_PATH_REPLACEMENT_NOT_DETECTED"
        }
        $decoy=Join-Path $account "decoy.bin"
        [IO.File]::WriteAllBytes($decoy,[Text.Encoding]::UTF8.GetBytes("P553-decoy"))
        $decoyBefore=[Convert]::ToBase64String([IO.File]::ReadAllBytes($decoy))

        $source3=Join-Path $stage "cutpoint-source.bin"
        $payload3=[Text.Encoding]::UTF8.GetBytes("P553-actual-held-dest")
        [IO.File]::WriteAllBytes($source3,$payload3)
        $cutpointCode=[SpkP553HandleRelativeRename]::MoveSourceRelativeToHeldDirectory(
            $source3,$held,"held-only.bin")
        $retiredTarget=Join-Path $retired "held-only.bin"
        $newPathTarget=Join-Path $account "held-only.bin"
        if($report.rootedMoveSupported) {
            if($cutpointCode -ne 0) {
                throw ("P553_ROOTED_SUPPORTED_BUT_CUTPOINT_FAILED win32="+$cutpointCode)
            }
            $report.rootedTargetMovedIntoRetiredDirectory=
                (Test-Path -LiteralPath $retiredTarget) -and
                -not (Test-Path -LiteralPath $source3) -and
                ([Convert]::ToBase64String(
                    [IO.File]::ReadAllBytes($retiredTarget)) -ceq
                    [Convert]::ToBase64String($payload3))
            if(-not $report.rootedTargetMovedIntoRetiredDirectory) {
                throw "P553_ROOTED_CUTPOINT_WRONG_DESTINATION"
            }
        } else {
            # This is the Windows runner's expected negative lane:
            # the API refuses a non-NULL RootDirectory. Do NOT
            # switch to an absolute mutable pathname after the
            # path has been replaced; that could target a decoy.
            $report.unsupportedRootAfterPathSwapStillDenied=
                $cutpointCode -eq 87 -and
                (Test-Path -LiteralPath $source3) -and
                -not (Test-Path -LiteralPath $retiredTarget)
            if(-not $report.unsupportedRootAfterPathSwapStillDenied) {
                throw ("P553_ROOTED_NEGATIVE_CUTPOINT_UNSAFE win32="+$cutpointCode)
            }
        }
        $report.newlyRecreatedPathNotWritten=
            -not (Test-Path -LiteralPath $newPathTarget)
        $report.decoyFileUnmodified=
            ([Convert]::ToBase64String([IO.File]::ReadAllBytes($decoy)) -ceq
                $decoyBefore)
        if(-not $report.heldDirectoryIdentityStableAcrossPathReplacement -or
            -not $report.newlyRecreatedPathNotWritten -or
            -not $report.decoyFileUnmodified) {
            throw "P553_REPLACED_PATH_TARGET_OR_DECOY_CHANGED"
        }

        # Occupied destination on the ordinary supported NULL-root
        # form must still deny a replacement, retaining both bytes.
        $source4=Join-Path $stage "occupied-source.bin"
        [IO.File]::WriteAllBytes(
            $source4,[Text.Encoding]::UTF8.GetBytes("P553-do-not-replace"))
        $retiredBaseline=Join-Path $retired "baseline.bin"
        $before=[Convert]::ToBase64String(
            [IO.File]::ReadAllBytes($retiredBaseline))
        $occupied=[SpkP553HandleRelativeRename]::MoveSourceAbsoluteWithNullRoot(
            $source4,$retiredBaseline)
        $report.existingTargetRejectedWithoutReplacement=
            $occupied -ne 0 -and
            ([Convert]::ToBase64String(
                [IO.File]::ReadAllBytes($retiredBaseline)) -ceq $before)
        $report.sourcePreservedOnRejection=Test-Path -LiteralPath $source4
        if(-not $report.existingTargetRejectedWithoutReplacement -or
            -not $report.sourcePreservedOnRejection) {
            throw ("P553_OCCUPIED_TARGET_REPLACED win32="+$occupied)
        }
    } finally { $held.Dispose() }

    # Both provider branches are real, not simulated. A positive
    # root-handle report requires BOTH handle-relative moves.
    # An unsupported root report requires BOTH strict error-87
    # refusals without any disk changes, plus NULL-root baseline.
    if(-not $report.heldDirectoryIdentityStableAcrossPathReplacement -or
        -not $report.decoyFileUnmodified -or
        -not $report.newlyRecreatedPathNotWritten -or
        -not $report.fullPathNullRootBytesVerified -or
        -not $report.existingTargetRejectedWithoutReplacement -or
        -not $report.sourcePreservedOnRejection -or
        -not (
            ($report.rootedMoveSupported -and
                $report.rootedTargetMovedIntoRetiredDirectory -and
                -not $report.rootedMoveDeniedWin32Error87) -or
            ($report.rootedMoveDeniedWin32Error87 -and
                $report.rootedUnsupportedAttemptNoMove -and
                $report.unsupportedRootAfterPathSwapStillDenied -and
                -not $report.rootedMoveSupported)
        )) {
        throw "P553_PROVIDER_CAPABILITY_NOT_PROVEN"
    }
    $report | ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath ([IO.Path]::GetFullPath($OutputPath)) -Encoding utf8
    Write-Host ("P553_ROOT_DIRECTORY_CAPABILITY_PASS"+
        " nullRootFullPathRoundTrip=true"+
        " rootedRelativeSupported="+$report.rootedMoveSupported.ToString().ToLowerInvariant()+
        " rootedWin32Error87="+$report.rootedMoveDeniedWin32Error87.ToString().ToLowerInvariant()+
        " unchangedHeldDirectoryId=true"+
        " recreatedPathUnmodified=true"+
        " noOverwriteExisting=true"+
        " productionWorldSaveCertified=false"+
        " atomicExternalWriterFenceCertified=false")
} finally {
    if(Test-Path -LiteralPath $root) {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction Stop
    }
}
