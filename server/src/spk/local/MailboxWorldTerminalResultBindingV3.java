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
import java.util.Optional;

/**
 * G21.123: per-call opt-in immutable post-confirmed result of an
 * actual G21.66 reserved World FIFO terminal strict publication.
 * SAME v1/v2 WORLD_TERMINAL UUID, canonical terminal snapshot
 * Receipt, G21.83 prepared journal and exact real terminal file
 * SHA-256. No World grant/replay/session/release/client ACK.
 *
 * This does not authenticate external writer provenance, exclude
 * raw/default/historical writers or confer an atomic archive epoch.
 */
final class MailboxWorldTerminalResultBindingV3 {
    private static final String PROTOCOL="SPK-G21123-WORLD-TERMINAL-RESULT-V3";
    private static final String SUFFIX=".g21123-world-terminal-result-v3-";
    private static final String UUID=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}";
    private static final int MAX_RECORDS=16;
    private static final int MAX_RECORD_BYTES=512;
    private static final int MAX_ACCOUNT_BYTES=1048576;
    private static final long LOCK_MS=1500L;

    enum Status {
        NO_RESULT_BINDING_LEGACY_OR_UNKNOWN,
        BOUND_REAL_TERMINAL_DISK_MATCH_NO_AUTHORITY
    }
    static final class Report {
        final Status status;
        final int boundAttempts;
        final String terminalSha256;
        final String preparedSha256;
        final String terminalFileBytesSha256;
        final int snapshotVersion;
        final boolean independentWriterSignatureProved=false;
        final boolean historicalWriterCensusProved=false;
        final boolean defaultAndRawWritersExcluded=false;
        final boolean futureWriterGenerationsFenced=false;
        final boolean crossJvmAtomicEpochProved=false;
        final boolean hardwarePowerLossDurabilityProved=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean archiveRolloverAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(
            Status s,int count,String terminal,String prepared,
            String disk,int version
        ){
            status=s;
            boundAttempts=count;
            terminalSha256=terminal;
            preparedSha256=prepared;
            terminalFileBytesSha256=disk;
            snapshotVersion=version;
        }
    }
    private static IOException veto(String reason){
        return new IOException(
            "G21.123 WORLD_TERMINAL_RESULT_UNSAFE_NO_AUTHORITY "+
            reason+" rollover=false cleanup=false grant=false");
    }
    private static String account(String a)throws IOException{
        if(a==null||!a.matches("[a-z0-9_-]{1,64}"))
            throw veto("NONCANONICAL_ACCOUNT");
        return a;
    }
    private static Path file(Path p){
        return Objects.requireNonNull(p,"file")
            .toAbsolutePath().normalize();
    }
    private static Path result(Path selected,String uuid){
        return selected.resolveSibling(selected.getFileName()+
            SUFFIX+uuid+".result");
    }
    private static String sha(byte[] bytes){
        try{
            byte[] d=MessageDigest.getInstance("SHA-256")
                .digest(bytes);
            StringBuilder out=new StringBuilder(64);
            for(byte b:d)
                out.append(String.format(java.util.Locale.ROOT,
                    "%02x",b&255));
            return out.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException(impossible);
        }
    }
    private static byte[] safe(Path selected,int max)throws IOException{
        final BasicFileAttributes first;
        try{
            first=Files.readAttributes(
                selected,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        }catch(IOException missing){
            throw veto("SOURCE_LEAF_MISSING_OR_UNSAFE");
        }
        if(!first.isRegularFile()||first.isSymbolicLink()||
           first.fileKey()==null||first.size()<1||
           first.size()>max)
            throw veto("NONREGULAR_OR_OVERSIZED_SOURCE");
        ByteBuffer bytes=ByteBuffer.allocate(max+1);
        try(FileChannel channel=FileChannel.open(selected,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(bytes.hasRemaining()){
                int n=channel.read(bytes);
                if(n<0)break;
                if(n==0)throw veto("STALLED_SOURCE_READ");
            }
        }
        BasicFileAttributes after=Files.readAttributes(
            selected,BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS);
        if(first.size()!=bytes.position()||
           first.size()!=after.size()||
           !Objects.equals(first.fileKey(),after.fileKey())||
           !first.lastModifiedTime().equals(after.lastModifiedTime()))
            throw veto("PHYSICAL_SOURCE_CHANGED");
        byte[] out=new byte[bytes.position()];
        System.arraycopy(bytes.array(),0,out,0,out.length);
        return out;
    }
    private static String payload(
        String name,String uuid,String prepared,String terminal,
        int version,String rawFileSha
    ){
        return PROTOCOL+"\nACCOUNT="+name+
            "\nATTEMPT="+uuid+
            "\nKIND=WORLD_TERMINAL"+
            "\nPREPARED_SHA256="+prepared+
            "\nTERMINAL_SHA256="+terminal+
            "\nSNAPSHOT_VERSION="+version+
            "\nTERMINAL_FILE_BYTES_SHA256="+rawFileSha+
            "\nSTAGE=POST_CONFIRMED\n";
    }
    private static PlayerSnapshot load(
        Path selected,String name
    )throws IOException{
        final Optional<PlayerSnapshot> read;
        try{
            read=new FilePlayerRepository(who->{
                if(!name.equals(who))
                    throw new IllegalArgumentException(
                        "G21.123 account resolver drift");
                return selected;
            }).load(name);
        }catch(IOException|RuntimeException failure){
            throw veto("TERMINAL_ACCOUNT_UNREADABLE");
        }
        if(!read.isPresent())throw veto("TERMINAL_ACCOUNT_ABSENT");
        return read.get();
    }
    private static String canonical(PlayerSnapshot snapshot){
        return StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(snapshot);
    }
    private static String before(PlayerSnapshot snapshot)
        throws IOException{
        String prior=snapshot.value(
            "extension."+MailboxAtomicTerminalSnapshot.NAMESPACE+
            ".before");
        if(prior==null||!prior.matches("[0-9a-f]{64}"))
            throw veto("TERMINAL_MISSING_PREPARED_HASH");
        return prior;
    }
    /** Must execute while caller holds the G21.39 account lock. */
    private static void requireRealTerminal(
        Path selected,String name,PlayerSnapshot terminal,
        String expectedPrepared,String expectedTerminal
    )throws IOException{
        if(terminal.version()!=PlayerSnapshot.CURRENT_VERSION||
           !name.equals(terminal.username())||
           MailboxAtomicTerminalSnapshot.inspect(terminal).state!=
              MailboxAtomicTerminalSnapshot.State
                 .COHERENT_TERMINAL_NO_GRANT||
           !expectedTerminal.equals(canonical(terminal))||
           !expectedPrepared.equals(before(terminal)))
            throw veto("REAL_G2166_TERMINAL_CONTENT_CHANGED");
        MailboxDurableIdempotencyIntentJournal.Observation journal=
            new MailboxDurableIdempotencyIntentJournal(who->{
                if(!name.equals(who))
                    throw new IllegalArgumentException(
                        "G21.123 journal account resolver drift");
                return selected;
            }).inspectInsidePublicationLock(name,selected);
        if(journal.status!=
             MailboxDurableIdempotencyIntentJournal.Status
                 .TERMINAL_MATCH_NO_COMMIT||
           !journal.recordValidated||
           !expectedPrepared.equals(journal.preparedSha256)||
           !expectedTerminal.equals(journal.terminalSha256))
            throw veto("REAL_G2183_TERMINAL_INTENT_MISMATCH");
    }

    /**
     * Strict G21.66 returned a real Receipt AFTER both World owner
     * checks, file+directory force and G21.48 intent cleanup. Called
     * with original G21.100 lifetime lock already held and BEFORE
     * original v1/v2 COMPLETE.
     */
    static void recordInside(
        Path selected,PlayerSnapshot terminal,String uuid,
        StrictDurablePlayerSnapshotWriter.Receipt receipt,
        String preparedSha
    )throws IOException{
        final Path chosen=file(selected);
        if(terminal==null||receipt==null||
           uuid==null||!uuid.matches(UUID)||
           !chosen.equals(file(receipt.file))||
           !receipt.account.equals(account(terminal.username()))||
           !receipt.matchesSnapshot(terminal)||
           preparedSha==null||!preparedSha.matches("[0-9a-f]{64}"))
            throw veto("UNTRUSTED_TERMINAL_RECEIPT");
        final String name=terminal.username();
        final byte[] actual=MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(chosen,LOCK_MS,()->{
                requireRealTerminal(
                    chosen,name,load(chosen,name),
                    preparedSha,receipt.snapshotSha256);
                return safe(chosen,MAX_ACCOUNT_BYTES);
            });
        final byte[] record=payload(
            name,uuid,preparedSha,receipt.snapshotSha256,
            receipt.snapshotVersion,sha(actual))
            .getBytes(StandardCharsets.US_ASCII);
        if(record.length>MAX_RECORD_BYTES)
            throw veto("RESULT_PAYLOAD_TOO_LARGE");
        Path leaf=result(chosen,uuid);
        Path temporary=Files.createTempFile(
            chosen.getParent(),chosen.getFileName()+
                ".g21123-result-stage-",".tmp");
        try{
            try(FileChannel out=FileChannel.open(
                temporary,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer bytes=ByteBuffer.wrap(record);
                while(bytes.hasRemaining())out.write(bytes);
                out.force(true);
            }
            Files.createLink(leaf,temporary);
            try(FileChannel dir=FileChannel.open(
                    chosen.getParent(),StandardOpenOption.READ)){
                dir.force(true);
            }
        }finally{
            // ONLY the transient staging hardlink; never result leaf.
            Files.deleteIfExists(temporary);
        }
    }
    private static void requireOriginalPair(
        Path chosen,String name,String uuid
    )throws IOException{
        for(boolean begin:new boolean[]{true,false}){
            String stage=begin?"BEGIN":"COMPLETE";
            String ext=begin?".begin":".complete";
            Path v1=chosen.resolveSibling(chosen.getFileName()+
                ".g21103-writer-attempt-v1-"+uuid+ext);
            Path v2=chosen.resolveSibling(chosen.getFileName()+
                ".g21114-writer-kind-v2-"+uuid+ext);
            String original1=MailboxWriterAttemptEvidenceV1.PROTOCOL+
                "\nACCOUNT="+name+"\nATTEMPT="+uuid+
                "\nSTAGE="+stage+"\n";
            String original2=MailboxWriterKindEvidenceV2.PROTOCOL+
                "\nACCOUNT="+name+"\nATTEMPT="+uuid+
                "\nKIND=WORLD_TERMINAL\nSTAGE="+stage+"\n";
            if(!original1.equals(new String(
                    safe(v1,MAX_RECORD_BYTES),
                    StandardCharsets.US_ASCII))||
               !original2.equals(new String(
                    safe(v2,MAX_RECORD_BYTES),
                    StandardCharsets.US_ASCII)))
                throw veto("ORPHAN_OR_WRONG_KIND_UUID");
        }
    }

    /** Read-only under one G21.100 lifecycle ownership. */
    static Report inspectInside(Path selected,String name)
        throws IOException{
        final Path chosen=file(selected);
        account(name);
        MailboxWriterAttemptEvidenceV1.Report v1=
            MailboxWriterAttemptEvidenceV1.inspectInside(chosen,name);
        MailboxWriterKindEvidenceV2.Report v2=
            MailboxWriterKindEvidenceV2.inspectInside(chosen,name);
        if(v1.status==MailboxWriterAttemptEvidenceV1.Status
                .INCOMPLETE_ATTEMPT_NO_CLEANUP||
           v2.status==MailboxWriterKindEvidenceV2.Status
                .INCOMPLETE_TYPED_NO_AUTHORITY)
            throw veto("PRIOR_UNFINISHED_WRITER_ATTEMPT");
        final String prefix=chosen.getFileName()+SUFFIX;
        int count=0;
        String terminalHash=null,preparedHash=null,rawHash=null;
        int version=0;
        try(DirectoryStream<Path> records=Files.newDirectoryStream(
                chosen.getParent(),prefix+"*")){
            for(Path leaf:records){
                if(++count>MAX_RECORDS)
                    throw veto("RESULT_RECORD_CAPACITY_EXCEEDED");
                String part=leaf.getFileName().toString()
                    .substring(prefix.length());
                if(!part.matches(UUID+"\\.result"))
                    throw veto("UNKNOWN_OR_MALFORMED_RESULT_VERSION");
                String uuid=part.substring(0,part.length()-7);
                requireOriginalPair(chosen,name,uuid);
                String observed=new String(safe(leaf,MAX_RECORD_BYTES),
                    StandardCharsets.US_ASCII);
                final String[] expected=new String[4];
                MailboxAccountPublicationCoordinator
                    .withExclusivePublicationBounded(chosen,LOCK_MS,()->{
                        PlayerSnapshot terminal=load(chosen,name);
                        String t=canonical(terminal);
                        String p=before(terminal);
                        requireRealTerminal(
                            chosen,name,terminal,p,t);
                        expected[0]=t;
                        expected[1]=p;
                        expected[2]=sha(safe(chosen,MAX_ACCOUNT_BYTES));
                        expected[3]=payload(
                            name,uuid,p,t,terminal.version(),expected[2]);
                        return Boolean.TRUE;
                    });
                if(!observed.equals(expected[3]))
                    throw veto("RESULT_OR_TERMINAL_BYTES_DIVERGED");
                preparedHash=expected[1];
                terminalHash=expected[0];
                rawHash=expected[2];
                version=PlayerSnapshot.CURRENT_VERSION;
            }
        }
        if(count==0)
            return new Report(Status.NO_RESULT_BINDING_LEGACY_OR_UNKNOWN,
                0,null,null,null,0);
        if(count!=1||
           v2.status!=MailboxWriterKindEvidenceV2.Status
               .OBSERVED_TYPED_PAIRED_NO_AUTHORITY||
           v2.untypedV1Leaves!=0)
            throw veto("MULTIPLE_OR_MIXED_TERMINAL_RESULT_UNTRUSTED");
        return new Report(
            Status.BOUND_REAL_TERMINAL_DISK_MATCH_NO_AUTHORITY,
            count,terminalHash,preparedHash,rawHash,version);
    }
    static Report inspect(
        FilePlayerRepository.PathResolver resolver,String name
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        final Path chosen=file(Objects.requireNonNull(
            resolver.resolve(account(name)),"file"));
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(chosen),
                LOCK_MS,()->inspectInside(chosen,name));
    }

    /** A strict negative-only extension of G21.119 observed four real
     * typed kinds with independent five-plane pins and actual COMMIT.
     * Matching local bytes still do NOT prove a safe archival epoch.
     */
    static Report requirePinnedRealBoundWorldTerminal(
        FilePlayerRepository.PathResolver resolver,String name,
        String v1Portable,String v1Sha,String accountSha,
        String preparedPin,String commitPin,String v2Sha
    )throws IOException{
        MailboxRealWriterSemanticBoundaryV1.Report prior=
            MailboxRealWriterSemanticBoundaryV1
                .requirePinnedRealWriterCandidate(
                    resolver,name,v1Portable,v1Sha,
                    accountSha,preparedPin,commitPin,v2Sha);
        Report result=inspect(resolver,name);
        if(result.status!=Status
                .BOUND_REAL_TERMINAL_DISK_MATCH_NO_AUTHORITY||
           result.boundAttempts!=1||
           prior.diskCommit==null||
           !result.terminalSha256.equals(
                prior.diskCommit.terminalSha256))
            throw veto("TERMINAL_NOT_BOUND_TO_PINNED_DISK_COMMIT");
        return result;
    }
    private MailboxWorldTerminalResultBindingV3(){}
}
