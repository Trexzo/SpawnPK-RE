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
 * G21.121: optional post-confirmed v3 binding of an ACTUAL G21.83
 * PREPARED journal to the original v1/v2 PREPARED_JOURNAL attempt UUID.
 * Preserves existing v1/v2 formats and COMMIT v3 independently.
 *
 * Not cryptographic writer attestation, an atomic cross-JVM epoch,
 * replay, grant, admission, cleanup, pruning, unlink or rollover.
 */
final class MailboxPreparedJournalResultBindingV3 {
    private static final String FORMAT="SPK-G21121-PREPARED-RESULT-V3";
    private static final String SUFFIX=".g21121-prepared-result-v3-";
    private static final String UUID=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}";
    private static final int MAX_RESULT_BYTES=512;
    private static final int MAX_JOURNAL_BYTES=1024;
    private static final int MAX_RESULTS=16;
    private static final long WAIT_MS=1500L;
    enum Status{
        NO_RESULT_BINDING_LEGACY_OR_UNKNOWN,
        BOUND_PREPARED_JOURNAL_MATCH_NO_AUTHORITY
    }
    static final class Report{
        final Status status;
        final int boundAttempts;
        final String preparedSha256,terminalSha256,journalBytesSha256;
        final boolean authenticatedWriterExecution=false;
        final boolean fullWriterPopulationKnown=false;
        final boolean futureWritersExcluded=false;
        final boolean atomicEpochProved=false;
        final boolean archiveRolloverAuthorized=false;
        final boolean recordPruningAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(Status s,int count,String p,String t,String journal){
            status=s;boundAttempts=count;
            preparedSha256=p;terminalSha256=t;
            journalBytesSha256=journal;
        }
    }
    private static IOException veto(String why){
        return new IOException(
            "G21.121 PREPARED_RESULT_BINDING_UNSAFE_NO_AUTHORITY "+
            why+" cleanup=false grant=false");
    }
    private static String account(String name)throws IOException{
        if(name==null||!name.matches("[a-z0-9_-]{1,64}"))
            throw veto("NONCANONICAL_ACCOUNT");
        return name;
    }
    private static Path path(Path p){
        return Objects.requireNonNull(p,"path").toAbsolutePath().normalize();
    }
    private static Path journal(Path accountFile){
        return accountFile.resolveSibling(accountFile.getFileName()+
            MailboxDurableIdempotencyIntentJournal.SUFFIX);
    }
    private static Path result(Path file,String uuid){
        return file.resolveSibling(file.getFileName()+SUFFIX+uuid+
            ".result");
    }
    private static String hash(byte[] input){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256")
                .digest(input);
            StringBuilder text=new StringBuilder(64);
            for(byte b:digest)
                text.append(String.format(java.util.Locale.ROOT,
                    "%02x",b&255));
            return text.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException(impossible);
        }
    }
    private static byte[] safe(Path file,int max)throws IOException{
        BasicFileAttributes before;
        try{
            before=Files.readAttributes(file,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(IOException missing){
            throw veto("MISSING_OR_UNSAFE_FILE");
        }
        if(!before.isRegularFile()||before.isSymbolicLink()||
           before.fileKey()==null||before.size()<1||
           before.size()>max)throw veto("NONREGULAR_OR_OVERSIZE");
        ByteBuffer bytes=ByteBuffer.allocate(max+1);
        try(FileChannel reader=FileChannel.open(file,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(bytes.hasRemaining()){
                int n=reader.read(bytes);
                if(n<0)break;
                if(n==0)throw veto("STALLED_READ");
            }
        }
        BasicFileAttributes after=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(bytes.position()!=before.size()||
           before.size()!=after.size()||
           !Objects.equals(before.fileKey(),after.fileKey())||
           !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw veto("FILE_PHYSICAL_IDENTITY_CHANGED");
        byte[] result=new byte[bytes.position()];
        System.arraycopy(bytes.array(),0,result,0,result.length);
        return result;
    }
    private static String body(
        String name,String uuid,String prepared,
        String terminal,String actualJournalHash
    ){
        return FORMAT+"\nACCOUNT="+name+"\nATTEMPT="+uuid+
            "\nKIND=PREPARED_JOURNAL"+
            "\nPREPARED_SHA256="+prepared+
            "\nTERMINAL_SHA256="+terminal+
            "\nJOURNAL_BYTES_SHA256="+actualJournalHash+
            "\nSTAGE=POST_CONFIRMED\n";
    }
    private static MailboxDurableIdempotencyIntentJournal.Observation
        validateInside(Path file,String name
    )throws IOException{
        MailboxDurableIdempotencyIntentJournal.Observation obs=
            new MailboxDurableIdempotencyIntentJournal(
                who->{
                    if(!name.equals(who))
                        throw new IllegalArgumentException(
                            "G21.121 account resolver mismatch");
                    return file;
                }).inspectInsidePublicationLock(name,file);
        if(!obs.recordValidated||
           (obs.status!=MailboxDurableIdempotencyIntentJournal.Status
                .PREPARED_MATCH_NO_REPLAY&&
            obs.status!=MailboxDurableIdempotencyIntentJournal.Status
                .TERMINAL_MATCH_NO_COMMIT))
            throw veto("REAL_PREPARED_JOURNAL_SEMANTIC_MISMATCH");
        return obs;
    }

    /** Existing G21.100 writer lease already held; real G21.83
     * returned confirmed. Check actual journal/semantic bytes under
     * G21.39, then force create-only result BEFORE v1/v2 COMPLETE.
     */
    static void recordInside(
        Path selected,String name,String uuid,Path journalFile,
        String expectedPrepared,String expectedTerminal
    )throws IOException{
        Path file=path(selected);
        account(name);
        if(uuid==null||!uuid.matches(UUID)||
           !journal(file).equals(path(journalFile))||
           expectedPrepared==null||
           !expectedPrepared.matches("[0-9a-f]{64}")||
           expectedTerminal==null||
           !expectedTerminal.matches("[0-9a-f]{64}"))
            throw veto("INVALID_RESULT_WRITER_INPUT");
        byte[] realBytes=MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(file,WAIT_MS,()->{
                MailboxDurableIdempotencyIntentJournal.Observation obs=
                    validateInside(file,name);
                if(!expectedPrepared.equals(obs.preparedSha256)||
                   !expectedTerminal.equals(obs.terminalSha256))
                    throw veto("REAL_JOURNAL_PROPOSAL_MISMATCH");
                return safe(journal(file),MAX_JOURNAL_BYTES);
            });
        Path target=result(file,uuid);
        Path staging=Files.createTempFile(file.getParent(),
            file.getFileName()+".g21121-result-stage-",".tmp");
        try{
            byte[] payload=body(name,uuid,
                expectedPrepared,expectedTerminal,hash(realBytes))
                .getBytes(StandardCharsets.US_ASCII);
            if(payload.length>MAX_RESULT_BYTES)
                throw veto("RESULT_OVER_CAPACITY");
            try(FileChannel channel=FileChannel.open(staging,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer buffer=ByteBuffer.wrap(payload);
                while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            Files.createLink(target,staging);
            try(FileChannel directory=FileChannel.open(
                    file.getParent(),StandardOpenOption.READ)){
                directory.force(true);
            }
        }finally{
            // ONLY the ephemeral staging hardlink may be deleted.
            Files.deleteIfExists(staging);
        }
    }
    private static void matchingOriginalStages(
        Path file,String name,String uuid
    )throws IOException{
        for(boolean begin:new boolean[]{true,false}){
            String stage=begin?"BEGIN":"COMPLETE";
            String extension=begin?".begin":".complete";
            Path v1=file.resolveSibling(file.getFileName()+
                ".g21103-writer-attempt-v1-"+uuid+extension);
            Path v2=file.resolveSibling(file.getFileName()+
                ".g21114-writer-kind-v2-"+uuid+extension);
            String v1Text=MailboxWriterAttemptEvidenceV1.PROTOCOL+
                "\nACCOUNT="+name+"\nATTEMPT="+uuid+
                "\nSTAGE="+stage+"\n";
            String v2Text=MailboxWriterKindEvidenceV2.PROTOCOL+
                "\nACCOUNT="+name+"\nATTEMPT="+uuid+
                "\nKIND=PREPARED_JOURNAL\nSTAGE="+stage+"\n";
            if(!v1Text.equals(new String(safe(v1,MAX_RESULT_BYTES),
                    StandardCharsets.US_ASCII))||
               !v2Text.equals(new String(safe(v2,MAX_RESULT_BYTES),
                    StandardCharsets.US_ASCII)))
                throw veto("RESULT_ORPHAN_OR_KIND_UUID_DISAGREEMENT");
        }
    }

    /** Use only under one already held G21.100 lifetime lease. */
    static Report inspectInside(Path selected,String name)
        throws IOException{
        Path file=path(selected);
        account(name);
        MailboxWriterAttemptEvidenceV1.Report v1=
            MailboxWriterAttemptEvidenceV1.inspectInside(file,name);
        MailboxWriterKindEvidenceV2.Report v2=
            MailboxWriterKindEvidenceV2.inspectInside(file,name);
        if(v1.status==MailboxWriterAttemptEvidenceV1.Status
                .INCOMPLETE_ATTEMPT_NO_CLEANUP||
           v2.status==MailboxWriterKindEvidenceV2.Status
                .INCOMPLETE_TYPED_NO_AUTHORITY)
            throw veto("PRIOR_INCOMPLETE_WRITER_ATTEMPT");
        String prefix=file.getFileName()+SUFFIX;
        int found=0;
        String prepared=null,terminal=null,journalHash=null;
        try(DirectoryStream<Path> leaves=Files.newDirectoryStream(
            file.getParent(),prefix+"*")){
            for(Path leaf:leaves){
                if(++found>MAX_RESULTS)
                    throw veto("RESULT_RETENTION_OVER_CAPACITY");
                String suffix=leaf.getFileName().toString()
                    .substring(prefix.length());
                if(!suffix.matches(UUID+"\\.result"))
                    throw veto("UNKNOWN_RESULT_VERSION");
                String uuid=suffix.substring(0,suffix.length()-7);
                matchingOriginalStages(file,name,uuid);
                String actual=new String(safe(leaf,MAX_RESULT_BYTES),
                    StandardCharsets.US_ASCII);
                // One G21.39 lock for BOTH semantic inspection and
                // exact raw journal digest, not two unlocked reads.
                MailboxDurableIdempotencyIntentJournal.Observation obs=
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublicationBounded(
                            file,WAIT_MS,()->validateInside(file,name));
                byte[] raw=MailboxAccountPublicationCoordinator
                    .withExclusivePublicationBounded(
                        file,WAIT_MS,()->safe(
                            journal(file),MAX_JOURNAL_BYTES));
                String expected=body(name,uuid,obs.preparedSha256,
                    obs.terminalSha256,hash(raw));
                if(!expected.equals(actual))
                    throw veto("JOURNAL_RESULT_BYTES_CHANGED");
                prepared=obs.preparedSha256;
                terminal=obs.terminalSha256;
                journalHash=hash(raw);
            }
        }
        if(found==0)
            return new Report(
                Status.NO_RESULT_BINDING_LEGACY_OR_UNKNOWN,
                0,null,null,null);
        if(found!=1||
           v2.status!=MailboxWriterKindEvidenceV2.Status
                .OBSERVED_TYPED_PAIRED_NO_AUTHORITY||
           v2.untypedV1Leaves!=0)
            throw veto("MIXED_OR_MULTIPLE_RESULT_UNTRUSTED");
        return new Report(
            Status.BOUND_PREPARED_JOURNAL_MATCH_NO_AUTHORITY,
            found,prepared,terminal,journalHash);
    }
    static Report inspect(
        FilePlayerRepository.PathResolver resolver,String name
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Path file=path(Objects.requireNonNull(
            resolver.resolve(account(name)),"account"));
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(file),
                WAIT_MS,()->inspectInside(file,name));
    }
    /** Strict negative-only extension of existing G21.119 five
     * independently pinned planes. The successful return still does
     * NOT confer live settlement or a recoverable archive epoch.
     */
    static Report requirePinnedRealBoundPreparedJournal(
        FilePlayerRepository.PathResolver resolver,String name,
        String v1Portable,String v1Sha,String accountSha,
        String preparedPin,String commitPin,String v2Sha
    )throws IOException{
        MailboxRealWriterSemanticBoundaryV1.Report older=
            MailboxRealWriterSemanticBoundaryV1
                .requirePinnedRealWriterCandidate(
                    resolver,name,v1Portable,v1Sha,
                    accountSha,preparedPin,commitPin,v2Sha);
        Report bound=inspect(resolver,name);
        if(bound.status!=Status.BOUND_PREPARED_JOURNAL_MATCH_NO_AUTHORITY||
           bound.boundAttempts!=1||
           older.diskCommit==null||
           !preparedPin.equals(bound.journalBytesSha256)||
           !bound.terminalSha256.equals(
                older.diskCommit.terminalSha256))
            throw veto("PREPARED_NOT_BOUND_TO_PINNED_RESULT");
        return bound;
    }

    private MailboxPreparedJournalResultBindingV3(){}
}
