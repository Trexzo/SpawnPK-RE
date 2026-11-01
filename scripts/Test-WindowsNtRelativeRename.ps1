# P5.54 — isolated user-mode NtSetInformationFile capability probe.
# NEVER a production Windows World account writer or fallback from G21.171.
[CmdletBinding()]
param([string]$OutputPath="windows-nt-relative-rename-evidence.json")
Set-StrictMode -Version Latest
$ErrorActionPreference="Stop"
if(-not $IsWindows){throw "P554_REQUIRES_WINDOWS"}

$native=@'
using System;
using System.IO;
using System.Linq;
using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

public static class SpkP554NtRename
{
    const uint BACKUP=0x02000000, NOFOLLOW=0x00200000;
    const uint DIRECTORY=0x10, REPARSE=0x400;
    const uint FILE_ADD_FILE=0x2, FILE_TRAVERSE=0x20;
    const uint READ_ATTR=0x80, DELETE_ACCESS=0x10000;
    const int FILE_ID_INFO_CLASS=18, FILE_RENAME_INFORMATION_CLASS=10;

    [StructLayout(LayoutKind.Sequential)]
    struct ByHandleInfo
    {
        internal uint flags,c1,c2,a1,a2,w1,w2,serial,size1,size2,links,index1,index2;
    }
    [DllImport("kernel32.dll",EntryPoint="CreateFileW",SetLastError=true,CharSet=CharSet.Unicode)]
    static extern SafeFileHandle CreateFile(string path,uint access,FileShare shares,
        IntPtr security,FileMode disposition,uint flags,IntPtr template);
    [DllImport("kernel32.dll",SetLastError=true)]
    [return:MarshalAs(UnmanagedType.Bool)]
    static extern bool GetFileInformationByHandleEx(
        SafeFileHandle handle,int klass,IntPtr buffer,uint size);
    [DllImport("kernel32.dll",SetLastError=true)]
    [return:MarshalAs(UnmanagedType.Bool)]
    static extern bool GetFileInformationByHandle(
        SafeFileHandle handle,out ByHandleInfo info);
    // NTSTATUS is a 32-bit return value; class 10 = FileRenameInformation.
    [DllImport("ntdll.dll",ExactSpelling=true)]
    static extern int NtSetInformationFile(
        SafeFileHandle file,IntPtr ioStatusBlock,
        IntPtr information,uint length,int fileInformationClass);

    public sealed class Id
    {
        readonly ulong volume;
        readonly byte[] bytes;
        public readonly bool Directory,Reparse,Available;
        internal Id(ulong volume,byte[] bytes,uint flags)
        {
            this.volume=volume;this.bytes=bytes;
            Directory=(flags&DIRECTORY)!=0;Reparse=(flags&REPARSE)!=0;
            Available=bytes!=null && bytes.Length==16 && bytes.Any(b=>b!=0);
        }
        public bool Same(Id other)
        {
            return other!=null && Available && other.Available &&
                volume==other.volume && bytes.SequenceEqual(other.bytes);
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
            throw new Win32Exception(error,"P554_CreateFileW_failed");
        }
        return h;
    }
    static Id Read(SafeFileHandle h)
    {
        IntPtr memory=Marshal.AllocHGlobal(24);
        try
        {
            if(!GetFileInformationByHandleEx(h,FILE_ID_INFO_CLASS,memory,24))
                throw new Win32Exception(Marshal.GetLastWin32Error(),"P554_FileIdInfo_failed");
            ulong volume=unchecked((ulong)Marshal.ReadInt64(memory,0));
            byte[] bytes=new byte[16];
            Marshal.Copy(IntPtr.Add(memory,8),bytes,0,16);
            ByHandleInfo attributes;
            if(!GetFileInformationByHandle(h,out attributes))
                throw new Win32Exception(Marshal.GetLastWin32Error(),"P554_Attributes_failed");
            return new Id(volume,bytes,attributes.flags);
        }
        finally{Marshal.FreeHGlobal(memory);}
    }
    public sealed class HeldDir:IDisposable
    {
        readonly SafeFileHandle handle;
        internal HeldDir(SafeFileHandle handle){this.handle=handle;}
        public Id Identity()
        {
            if(handle.IsClosed || handle.IsInvalid)
                throw new InvalidOperationException("P554_ClosedDestinationHandle");
            return Read(handle);
        }
        internal IntPtr Raw{get{return handle.DangerousGetHandle();}}
        public void Dispose(){handle.Dispose();}
    }
    public static HeldDir Hold(string path)
    {
        SafeFileHandle handle=Open(path,FILE_ADD_FILE|FILE_TRAVERSE|READ_ATTR,
            BACKUP|NOFOLLOW);
        Id id=Read(handle);
        if(!id.Available || !id.Directory || id.Reparse)
        {
            handle.Dispose();
            throw new InvalidOperationException("P554_UnsafeDirectory");
        }
        return new HeldDir(handle);
    }
    public static Id Observe(string path)
    {
        using(HeldDir h=Hold(path)){return h.Identity();}
    }

    public static uint MoveRelative(string source,HeldDir destination,string leaf)
    {
        if(String.IsNullOrWhiteSpace(leaf) || leaf=="." || leaf==".." ||
            leaf.IndexOfAny(new[]{'/', '\\', ':'})>=0)
            throw new ArgumentException("P554_InvalidRelativeLeaf");
        byte[] name=System.Text.Encoding.Unicode.GetBytes(leaf);
        // FILE_RENAME_INFORMATION on x64:
        // BOOLEAN at 0, 7-byte padding, RootDirectory at 8,
        // ULONG FileNameLength at 16, WCHAR FileName at 20.
        int rootOffset=IntPtr.Size==8?8:4;
        int lenOffset=rootOffset+IntPtr.Size;
        int nameOffset=lenOffset+4;
        int total=nameOffset+name.Length+2;
        IntPtr buffer=Marshal.AllocHGlobal(total);
        IntPtr io=Marshal.AllocHGlobal(2*IntPtr.Size);
        try
        {
            Marshal.Copy(new byte[total],0,buffer,total);
            Marshal.Copy(new byte[2*IntPtr.Size],0,io,2*IntPtr.Size);
            Marshal.WriteByte(buffer,0,0); // Do NOT replace existing targets.
            Marshal.WriteIntPtr(buffer,rootOffset,destination.Raw);
            Marshal.WriteInt32(buffer,lenOffset,name.Length);
            Marshal.Copy(name,0,IntPtr.Add(buffer,nameOffset),name.Length);
            using(SafeFileHandle src=Open(source,DELETE_ACCESS|READ_ATTR,0))
            {
                return unchecked((uint)NtSetInformationFile(
                    src,io,buffer,(uint)total,FILE_RENAME_INFORMATION_CLASS));
            }
        }
        finally{Marshal.FreeHGlobal(io);Marshal.FreeHGlobal(buffer);}
    }
}
'@
Add-Type -TypeDefinition $native -Language CSharp -ErrorAction Stop

$root=Join-Path (Get-Location).Path ("spk-p554-nt-rename-"+[Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $root -ErrorAction Stop | Out-Null
$report=[ordered]@{
    formatVersion=1
    type="SPK_P554_NT_RELATIVE_RENAME_CAPABILITY_ONLY"
    normalWindowsMoveControl=$false
    sameDirectoryNativeIdentityObserved=$false
    ntRelativeMoveSupported=$false
    unsupportedNtStatus="" 
    rejectedRelativeMovePreservedSource=$false
    retiredHandleIdStillOriginal=$false
    recreatedDirectoryHasDifferentId=$false
    successfulRootedMoveLandedInRetiredDirectory=$false
    unsupportedRootedMoveAfterSwapPreservedSource=$false
    replacementPathUnchanged=$false
    existingTargetDeniedWithoutOverwrite=$false
    existingTargetSourcePreserved=$false
    originalJavaG21171Unmodified=$true
    productionWorldSaveCertified=$false
    nativeWorldWriterIntegrated=$false
    crossJvmLeaseCertified=$false
    atomicExternalRawWriterFenceCertified=$false
    crashDurabilityCertified=$false
}
function BytesEqual([string]$left,[string]$right){
    if(!(Test-Path -LiteralPath $left) -or
       !(Test-Path -LiteralPath $right)){return $false}
    return [Convert]::ToBase64String([IO.File]::ReadAllBytes($left)) -ceq
        [Convert]::ToBase64String([IO.File]::ReadAllBytes($right))
}
try {
    $stage=Join-Path $root "stage"
    $dest=Join-Path $root "account"
    New-Item -ItemType Directory -Path $stage | Out-Null
    New-Item -ItemType Directory -Path $dest | Out-Null

    # Independent positive fixture: the provider can rename an ordinary
    # staged file. This does not establish NT rooted move support.
    $plain=Join-Path $stage "ordinary-source"
    [IO.File]::WriteAllBytes($plain,[Text.Encoding]::UTF8.GetBytes("P554-normal-Windows-file"))
    $moved=Join-Path $dest "ordinary-moved"
    [IO.File]::Move($plain,$moved)
    $report.normalWindowsMoveControl=(Test-Path -LiteralPath $moved) -and
        -not (Test-Path -LiteralPath $plain)

    $held=[SpkP554NtRename]::Hold($dest)
    try {
        $original=$held.Identity()
        $report.sameDirectoryNativeIdentityObserved=
            $original.Same([SpkP554NtRename]::Observe($dest))
        if(-not $report.sameDirectoryNativeIdentityObserved){
            throw "P554_NATIVE_DESTINATION_ID_UNSTABLE"
        }

        $firstSource=Join-Path $stage "source-a"
        $firstBytes=[Text.Encoding]::UTF8.GetBytes("P554-NT-relative-first")
        [IO.File]::WriteAllBytes($firstSource,$firstBytes)
        $firstStatus=[SpkP554NtRename]::MoveRelative($firstSource,$held,"relative-a")
        $firstTarget=Join-Path $dest "relative-a"
        $report.ntRelativeMoveSupported=$firstStatus -eq [uint32]0
        if($report.ntRelativeMoveSupported){
            if((Test-Path -LiteralPath $firstSource) -or
                -not (Test-Path -LiteralPath $firstTarget) -or
                [Convert]::ToBase64String([IO.File]::ReadAllBytes($firstTarget)) -cne
                [Convert]::ToBase64String($firstBytes)){
                throw "P554_NT_SUCCESS_WITHOUT_REAL_FIRST_FILE_MOVE"
            }
        } else {
            # Fail closed on all unsupported NTSTATUS values, but
            # never label them positive or choose an absolute fallback.
            $report.unsupportedNtStatus=("0x{0:X8}" -f $firstStatus)
            $report.rejectedRelativeMovePreservedSource=
                (Test-Path -LiteralPath $firstSource) -and
                -not (Test-Path -LiteralPath $firstTarget)
            if(-not $report.rejectedRelativeMovePreservedSource){
                throw "P554_NT_FAILURE_MUTATED_STAGE_OR_TARGET"
            }
        }

        # Path substitution while ORIGINAL directory handle remains.
        $retired=Join-Path $root "account-retired"
        Move-Item -LiteralPath $dest -Destination $retired -ErrorAction Stop
        $report.retiredHandleIdStillOriginal=
            $original.Same($held.Identity()) -and
            $original.Same([SpkP554NtRename]::Observe($retired))
        New-Item -ItemType Directory -Path $dest | Out-Null
        $report.recreatedDirectoryHasDifferentId=
            -not $original.Same([SpkP554NtRename]::Observe($dest))
        $decoy=Join-Path $dest "decoy"
        $decoyBytes=[Text.Encoding]::UTF8.GetBytes("P554-new-path-decoy")
        [IO.File]::WriteAllBytes($decoy,$decoyBytes)

        $source2=Join-Path $stage "source-b"
        $payload2=[Text.Encoding]::UTF8.GetBytes("P554-held-after-path-swap")
        [IO.File]::WriteAllBytes($source2,$payload2)
        $status2=[SpkP554NtRename]::MoveRelative($source2,$held,"relative-b")
        $oldTarget=Join-Path $retired "relative-b"
        $newTarget=Join-Path $dest "relative-b"
        if($report.ntRelativeMoveSupported){
            $report.successfulRootedMoveLandedInRetiredDirectory=
                $status2 -eq [uint32]0 -and
                (Test-Path -LiteralPath $oldTarget) -and
                -not (Test-Path -LiteralPath $source2) -and
                ([Convert]::ToBase64String([IO.File]::ReadAllBytes($oldTarget)) -ceq
                [Convert]::ToBase64String($payload2))
            if(-not $report.successfulRootedMoveLandedInRetiredDirectory){
                throw ("P554_ROOTED_SECOND_MOVE_FAILED status=0x{0:X8}" -f $status2)
            }
        } else {
            # Neither a later success nor partial write is accepted
            # for an initially unsupported native-root provider.
            $report.unsupportedRootedMoveAfterSwapPreservedSource=
                $status2 -eq $firstStatus -and
                (Test-Path -LiteralPath $source2) -and
                -not (Test-Path -LiteralPath $oldTarget)
            if(-not $report.unsupportedRootedMoveAfterSwapPreservedSource){
                throw ("P554_NEGATIVE_ROOTED_SECOND_MOVE_INCONSISTENT 0x{0:X8}" -f $status2)
            }
        }
        $report.replacementPathUnchanged=
            -not (Test-Path -LiteralPath $newTarget) -and
            ([Convert]::ToBase64String([IO.File]::ReadAllBytes($decoy)) -ceq
                [Convert]::ToBase64String($decoyBytes))
        if(-not $report.replacementPathUnchanged){
            throw "P554_RECREATED_ACCOUNT_PATH_WAS_MODIFIED"
        }

        # Collision refusal is meaningful only when actual relative
        # NT rename capability was independently demonstrated.
        if($report.ntRelativeMoveSupported){
            $occupiedSource=Join-Path $stage "occupied-source"
            [IO.File]::WriteAllBytes(
                $occupiedSource,[Text.Encoding]::UTF8.GetBytes("P554-no-overwrite"))
            $occupiedTarget=Join-Path $retired "relative-a"
            $targetBefore=[Convert]::ToBase64String(
                [IO.File]::ReadAllBytes($occupiedTarget))
            $occupiedStatus=[SpkP554NtRename]::MoveRelative(
                $occupiedSource,$held,"relative-a")
            $report.existingTargetDeniedWithoutOverwrite=
                $occupiedStatus -ne [uint32]0 -and
                ([Convert]::ToBase64String(
                    [IO.File]::ReadAllBytes($occupiedTarget)) -ceq $targetBefore)
            $report.existingTargetSourcePreserved=
                (Test-Path -LiteralPath $occupiedSource)
            if(-not $report.existingTargetDeniedWithoutOverwrite -or
                -not $report.existingTargetSourcePreserved){
                throw "P554_EXISTING_TARGET_WAS_OVERWRITTEN"
            }
        }
    } finally { $held.Dispose() }

    if(-not $report.normalWindowsMoveControl -or
        -not $report.sameDirectoryNativeIdentityObserved -or
        -not $report.retiredHandleIdStillOriginal -or
        -not $report.recreatedDirectoryHasDifferentId -or
        -not $report.replacementPathUnchanged -or
        -not (
            ($report.ntRelativeMoveSupported -and
             $report.successfulRootedMoveLandedInRetiredDirectory -and
             $report.existingTargetDeniedWithoutOverwrite -and
             $report.existingTargetSourcePreserved) -or
            (-not $report.ntRelativeMoveSupported -and
             $report.rejectedRelativeMovePreservedSource -and
             $report.unsupportedRootedMoveAfterSwapPreservedSource)
        )){
        throw "P554_NT_ROOT_DIRECTORY_CAPABILITY_UNPROVEN"
    }
    $report | ConvertTo-Json -Depth 4 |
        Set-Content -LiteralPath ([IO.Path]::GetFullPath($OutputPath)) -Encoding utf8
    Write-Host ("P554_NT_ROOT_DIRECTORY_CAPABILITY_PASS"+
        " ntRootedSupported="+$report.ntRelativeMoveSupported.ToString().ToLowerInvariant()+
        " negativeNtStatus="+$report.unsupportedNtStatus+
        " heldDirectoryIdentified=true recreatedPathUnchanged=true"+
        " originalWorldWriterUnchanged=true productionWorldSaveCertified=false")
} finally {
    if(Test-Path -LiteralPath $root){
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction Stop
    }
}
