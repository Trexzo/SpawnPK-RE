package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * G21.120: optional, write-once v3 POST_CONFIRMED digest binding for
 * ONE genuine G21.86 COMMIT operation to the SAME v1/v2 attempt UUID.
 *
 * Not a universal causal proof: existing caller-provided labels are
 * not cryptographically authenticated and raw writers remain possible.
 * Never a grant/replay/cleanup/rollover/session/release/ACK token.
 */
final class MailboxCommitResultBindingV3 {
    private static final String PROTOCOL="SPK-G21120-COMMIT-RESULT-V3";
    private static final String SUFFIX=".g21120-commit-result-v3-";
    private static final String UUID=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}";
    private static final int MAX_LEAVES=16;
    private static final int MAX_PAYLOAD=512;
    private static final int MAX_COMMIT_BYTES=1024;
    private static final long LOCK_MS=1500L;

    enum Status {
        NO_RESULT_BINDING_LEGACY_OR_UNKNOWN,
        BOUND_COMMIT_DISK_MATCH_NO_AUTHORITY
    }
    static final class Report {
        final Status status;
        final int boundAttempts;
        final String terminalSha256;
        final String commitBytesSha256;
        final boolean authenticatedWriterExecution=false;
        final boolean historicalWriterPopulationProved=false;
        final boolean allFutureWritersFenced=false;
        final boolean multiFileAtomicEpochProved=false;
        final boolean archiveRolloverAuthorized=false;
        final boolean recordPruningAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(Status s,int n,String terminal,String digest){
            status=s;boundAttempts=n;terminalSha256=terminal;
            commitBytesSha256=digest;
        }
    }
    private static IOException veto(String why){
        return new IOException(
            "G21.120 COMMIT_RESULT_BINDING_UNSAFE_NO_AUTHORITY "+
            why+" cleanup=false grant=false");
    }
    private static String checked(String name)throws IOException{
        if(name==null||!name.matches("[a-z0-9_-]{1,64}"))
            throw veto("INVALID_ACCOUNT");
        return name;
    }
    private static String digest(byte[] data){
        final byte[] bytes;
        try{
            bytes=MessageDigest.getInstance("SHA-256").digest(data);
        }catch(NoSuchAlgorithmException unexpected){
            throw new IllegalStateException(unexpected);
        }
        StringBuilder hex=new StringBuilder(64);
        for(byte b:bytes)
            hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private static Path selected(Path accountFile){
        return Objects.requireNonNull(accountFile,"account")
            .toAbsolutePath().normalize();
    }
    private static Path marker(Path file){
        return file.resolveSibling(
            file.getFileName().toString()+
            MailboxGuardedDiskCommitRecord.SUFFIX);
    }
    private static Path recordPath(Path file,String uuid){
        return file.resolveSibling(
            file.getFileName().toString()+SUFFIX+uuid+".result");
    }
    private static byte[] safeBytes(
        Path path,int max
    )throws IOException{
        BasicFileAttributes first=Files.readAttributes(
            path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!first.isRegularFile()||first.isSymbolicLink()||
           first.fileKey()==null||first.size()<1||
           first.size()>max)
            throw veto("UNSAFE_OR_OVERSIZED_FILE");
        ByteBuffer buf=ByteBuffer.allocate(max+1);
        try(FileChannel reader=FileChannel.open(
                path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(buf.hasRemaining()){
                int n=reader.read(buf);
                if(n<0)break;
                if(n==0)throw veto("STALLED_READ");
            }
        }
        BasicFileAttributes last=Files.readAttributes(
            path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(buf.position()!=first.size()||
           !Objects.equals(first.fileKey(),last.fileKey())||
           first.size()!=last.size()||
           !first.lastModifiedTime().equals(last.lastModifiedTime()))
            throw veto("CHANGED_PHYSICAL_FILE");
        byte[] result=new byte[buf.position()];
        System.arraycopy(buf.array(),0,result,0,result.length);
        return result;
    }
    private static String payload(
        String account,String uuid,String terminal,String hash
    ){
        return PROTOCOL+"\nACCOUNT="+account+
            "\nATTEMPT="+uuid+"\nKIND=DISK_COMMIT"+
            "\nTERMINAL_SHA256="+terminal+
            "\nCOMMIT_BYTES_SHA256="+hash+
            "\nSTAGE=POST_CONFIRMED\n";
    }
    private static void forceDirectory(Path dir)throws IOException{
        try(FileChannel handle=FileChannel.open(
                dir,StandardOpenOption.READ)){handle.force(true);}
    }

    /**
     * Only after the REAL G21.86 operation returns confirmed, inside
     * the EXISTING parent-held G21.100 lease. Independently validate
     * actual journal, terminal and COMMIT under inner G21.39 lock
     * before capturing the exact disk bytes and forcing v3 evidence.
     */
    static void recordInside(
        Path selectedFile,String account,String uuid,Path commitFile,
        StrictDurablePlayerSnapshotWriter.Receipt receipt
    )throws IOException{
        final Path file=selected(selectedFile);
        checked(account);
        if(uuid==null||!uuid.matches(UUID)||
           receipt==null||!receipt.file.equals(file)||
           !file.equals(file.toAbsolutePath().normalize())||
           !marker(file).equals(selected(commitFile))||
           receipt.snapshotSha256==null||
           !receipt.snapshotSha256.matches("[0-9a-f]{64}"))
            throw veto("UNTRUSTED_COMMIT_RESULT_INPUT");
        final byte[] committed=MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(file,LOCK_MS,()->{
                MailboxGuardedDiskCommitRecord.Observation disk=
                    new MailboxGuardedDiskCommitRecord(
                        who->{
                            if(!account.equals(who))
                                throw new IllegalArgumentException(
                                    "G21.120 account changed");
                            return file;
                        }).inspectInsidePublicationLock(account,file);
                if(disk.status!=
                     MailboxGuardedDiskCommitRecord.Status
                        .DISK_COMMIT_MATCH_NO_LIVE_APPLY||
                   !disk.recordValidated||
                   !receipt.snapshotSha256.equals(
                       disk.terminalSha256))
                    throw veto("ACTUAL_DISK_COMMIT_OR_RECEIPT_MISMATCH");
                return safeBytes(marker(file),MAX_COMMIT_BYTES);
            });
        final String targetHash=digest(committed);
        final Path target=recordPath(file,uuid);
        final Path temp=Files.createTempFile(file.getParent(),
            file.getFileName()+".g21120-result-stage-",".tmp");
        try{
            byte[] data=payload(account,uuid,
                receipt.snapshotSha256,targetHash)
                .getBytes(StandardCharsets.US_ASCII);
            if(data.length>MAX_PAYLOAD)
                throw veto("RESULT_PAYLOAD_TOO_LONG");
            try(FileChannel out=FileChannel.open(
                    temp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer b=ByteBuffer.wrap(data);
                while(b.hasRemaining())out.write(b);
                out.force(true);
            }
            Files.createLink(target,temp);
            forceDirectory(file.getParent());
        }finally{
            // Only the transient staging hardlink is removed.
            Files.deleteIfExists(temp);
        }
    }

    private static String exactStage(
        Path file,String uuid,boolean begin
    ){
        return file.getFileName()+
            ".g21114-writer-kind-v2-"+uuid+
            (begin?".begin":".complete");
    }
    private static void requireOriginalPair(
        Path file,String account,String uuid
    )throws IOException{
        for(boolean begin:new boolean[]{true,false}){
            String stage=begin?"BEGIN":"COMPLETE";
            String name=begin?"begin":"complete";
            Path v1=file.resolveSibling(file.getFileName()+
                ".g21103-writer-attempt-v1-"+uuid+"."+name);
            Path v2=file.resolveSibling(exactStage(file,uuid,begin));
            String expectedV1=MailboxWriterAttemptEvidenceV1.PROTOCOL+
                "\nACCOUNT="+account+"\nATTEMPT="+uuid+
                "\nSTAGE="+stage+"\n";
            String expectedV2=MailboxWriterKindEvidenceV2.PROTOCOL+
                "\nACCOUNT="+account+"\nATTEMPT="+uuid+
                "\nKIND=DISK_COMMIT\nSTAGE="+stage+"\n";
            // A stray v3 leaf WITHOUT either original v1/v2 file
            // is an unsafe orphan, not an ordinary missing-file error.
            // Wrap the NOFOLLOW read in a G21.120 negative category;
            // never create, repair, or unlink absent source evidence.
            final String readV1,readV2;
            try{
                readV1=new String(safeBytes(v1,MAX_PAYLOAD),
                    StandardCharsets.US_ASCII);
                readV2=new String(safeBytes(v2,MAX_PAYLOAD),
                    StandardCharsets.US_ASCII);
            }catch(IOException missingOrUnsafe){
                throw veto("ORPHAN_RESULT_MISSING_ORIGINAL_V1_V2");
            }
            if(!expectedV1.equals(readV1)||
               !expectedV2.equals(readV2))
                throw veto("RESULT_NOT_BOUND_TO_MATCHING_V1_V2");
        }
    }

    /** Under already-held G21.100. Never manufactures absent results. */
    static Report inspectInside(Path selectedFile,String account)
        throws IOException{
        final Path file=selected(selectedFile);
        checked(account);
        // First validate all original evidence under same lifecycle.
        MailboxWriterAttemptEvidenceV1.Report v1=
            MailboxWriterAttemptEvidenceV1.inspectInside(file,account);
        MailboxWriterKindEvidenceV2.Report v2=
            MailboxWriterKindEvidenceV2.inspectInside(file,account);
        if(v1.status==
                MailboxWriterAttemptEvidenceV1.Status
                    .INCOMPLETE_ATTEMPT_NO_CLEANUP||
           v2.status==
                MailboxWriterKindEvidenceV2.Status
                    .INCOMPLETE_TYPED_NO_AUTHORITY)
            throw veto("INCOMPLETE_OR_UNCONFIRMED_WRITER_ATTEMPT");
        String prefix=file.getFileName().toString()+SUFFIX;
        int found=0;
        String terminal=null,commitHash=null;
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(
                file.getParent(),prefix+"*")){
            for(Path result:stream){
                if(++found>MAX_LEAVES)
                    throw veto("OVER_CAPACITY_RESULT_BINDINGS");
                String filename=result.getFileName().toString();
                String suffix=filename.substring(prefix.length());
                if(!suffix.matches(UUID+"\\.result"))
                    throw veto("UNKNOWN_RESULT_RECORD_VERSION");
                String uuid=suffix.substring(0,suffix.length()-7);
                String actual=new String(safeBytes(
                    result,MAX_PAYLOAD),StandardCharsets.US_ASCII);
                requireOriginalPair(file,account,uuid);
                // Independently validate the actual receipt digest and
                // the exact file bytes under the established G21.39.
                final MailboxGuardedDiskCommitRecord.Observation disk=
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublicationBounded(
                            file,LOCK_MS,()->new
                                MailboxGuardedDiskCommitRecord(
                                    who->{
                                        if(!account.equals(who))
                                            throw new IllegalArgumentException(
                                                "G21.120 changed account");
                                        return file;
                                    }).inspectInsidePublicationLock(
                                        account,file));
                if(disk.status!=
                    MailboxGuardedDiskCommitRecord.Status
                        .DISK_COMMIT_MATCH_NO_LIVE_APPLY)
                    throw veto("DISK_COMMIT_NOT_REAL_OR_MISMATCHED");
                byte[] bytes=MailboxAccountPublicationCoordinator
                    .withExclusivePublicationBounded(
                        file,LOCK_MS,()->safeBytes(
                            marker(file),MAX_COMMIT_BYTES));
                String expected=payload(account,uuid,
                    disk.terminalSha256,digest(bytes));
                if(!expected.equals(actual))
                    throw veto("RESULT_DIGEST_OR_TERMINAL_CHANGED");
                terminal=disk.terminalSha256;
                commitHash=digest(bytes);
            }
        }
        if(found==0)
            return new Report(
                Status.NO_RESULT_BINDING_LEGACY_OR_UNKNOWN,0,null,null);
        if(v2.status!=
            MailboxWriterKindEvidenceV2.Status
                .OBSERVED_TYPED_PAIRED_NO_AUTHORITY||
           v2.untypedV1Leaves!=0)
            throw veto("MIXED_OR_UNTYPED_RESULT_NOT_TRUSTED");
        if(found!=1)
            throw veto("MULTIPLE_RESULT_RECORDS_ONE_COMMIT");
        return new Report(
            Status.BOUND_COMMIT_DISK_MATCH_NO_AUTHORITY,
            found,terminal,commitHash);
    }

    static Report inspect(
        FilePlayerRepository.PathResolver resolver,String account
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        final Path file=selected(Objects.requireNonNull(
            resolver.resolve(checked(account)),"account"));
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(file),
                LOCK_MS,()->inspectInside(file,account));
    }
    /** G21.120: optional NEGATIVE-only extension of G21.119.
     * Actual G21.86 COMMIT source SHA MUST agree with separately
     * pinned COMMIT plane AND the durable v3 attempt-bound result.
     * The two read-only observations are not an atomic epoch.
     */
    static Report requirePinnedRealBoundCommit(
        FilePlayerRepository.PathResolver resolver,
        String account,String v1Portable,String v1Sha,
        String accountSha,String preparedPin,String commitPin,
        String v2Sha
    )throws IOException{
        MailboxRealWriterSemanticBoundaryV1.Report prior=
            MailboxRealWriterSemanticBoundaryV1
                .requirePinnedRealWriterCandidate(
                    resolver,account,v1Portable,v1Sha,
                    accountSha,preparedPin,commitPin,v2Sha);
        Report bound=inspect(resolver,account);
        if(bound.status!=Status.BOUND_COMMIT_DISK_MATCH_NO_AUTHORITY||
           bound.boundAttempts!=1||
           !commitPin.equals(bound.commitBytesSha256)||
           prior.diskCommit==null||
           !bound.terminalSha256.equals(
               prior.diskCommit.terminalSha256))
            throw veto("COMMIT_NOT_BOUND_TO_SAME_PINNED_RESULT");
        return bound;
    }

    private MailboxCommitResultBindingV3(){}
}
