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
 * G21.122: immutable v3 result evidence tied to SAME G21.103/114
 * STRICT_PREPARED UUID and actual strict-replaced account file bytes.
 *
 * After later coherent terminal replacement only PREPARED canonical
 * digest+G21.83 intent survives. The original PREPARED file bytes
 * are no longer present, so they are NOT reasserted as current bytes.
 * This is observational, never grant or archival epoch authority.
 */
final class MailboxStrictPreparedResultBindingV3 {
    private static final String PROTOCOL="SPK-G21122-STRICT-PREPARED-V3";
    private static final String SUFFIX=".g21122-strict-prepared-result-v3-";
    private static final String UUID=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}";
    private static final int MAX_RESULTS=16;
    private static final int MAX_RESULT_BYTES=512;
    private static final int MAX_ACCOUNT_BYTES=1048576;
    private static final long LOCK_MS=1500L;
    enum Status {
        NO_RESULT_BINDING_LEGACY_OR_UNKNOWN,
        BOUND_CURRENT_PREPARED_BYTES_NO_AUTHORITY,
        BOUND_HISTORICAL_PREPARED_WITH_TERMINAL_JOURNAL_NO_AUTHORITY
    }
    static final class Report {
        final Status status;
        final int boundAttempts;
        final String preparedSha256;
        final String strictFileSha256;
        final int snapshotVersion;
        final boolean originalPreparedFileStillPresent;
        final boolean authenticatedWriterExecution=false;
        final boolean completeHistoricalWriterPopulation=false;
        final boolean allFutureWritersFenced=false;
        final boolean atomicMultiFileEpochProved=false;
        final boolean archiveRolloverAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(Status s,int n,String hash,String raw,int ver,
                       boolean bytesPresent){
            status=s;boundAttempts=n;preparedSha256=hash;
            strictFileSha256=raw;snapshotVersion=ver;
            originalPreparedFileStillPresent=bytesPresent;
        }
    }
    private static IOException veto(String problem){
        return new IOException(
            "G21.122 STRICT_PREPARED_RESULT_UNSAFE_NO_AUTHORITY "+
            problem+" cleanup=false grant=false");
    }
    private static String checked(String name)throws IOException{
        if(name==null||!name.matches("[a-z0-9_-]{1,64}"))
            throw veto("NONCANONICAL_ACCOUNT");
        return name;
    }
    private static Path normalized(Path p){
        return Objects.requireNonNull(p,"file").toAbsolutePath()
            .normalize();
    }
    private static Path result(Path file,String uuid){
        return file.resolveSibling(file.getFileName()+SUFFIX+
            uuid+".result");
    }
    private static String sha(byte[] input){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256")
                .digest(input);
            StringBuilder out=new StringBuilder(64);
            for(byte b:hash)
                out.append(String.format(java.util.Locale.ROOT,
                    "%02x",b&255));
            return out.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException(impossible);
        }
    }
    private static byte[] readSafe(Path file,int max)throws IOException{
        final BasicFileAttributes prior;
        try{
            prior=Files.readAttributes(file,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(IOException missing){
            throw veto("MISSING_OR_UNSAFE_SOURCE_FILE");
        }
        if(!prior.isRegularFile()||prior.isSymbolicLink()||
           prior.fileKey()==null||prior.size()<1||prior.size()>max)
            throw veto("NONREGULAR_OR_OVERSIZED_SOURCE");
        ByteBuffer dst=ByteBuffer.allocate(max+1);
        try(FileChannel in=FileChannel.open(file,
            StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(dst.hasRemaining()){
                int n=in.read(dst);
                if(n<0)break;
                if(n==0)throw veto("STALLED_SOURCE_READ");
            }
        }
        final BasicFileAttributes after=Files.readAttributes(file,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(dst.position()!=prior.size()||
           !Objects.equals(prior.fileKey(),after.fileKey())||
           prior.size()!=after.size()||
           !prior.lastModifiedTime().equals(after.lastModifiedTime()))
            throw veto("SOURCE_IDENTITY_OR_LENGTH_DRIFT");
        byte[] data=new byte[dst.position()];
        System.arraycopy(dst.array(),0,data,0,data.length);
        return data;
    }
    private static String payload(
        String account,String uuid,String canonical,int version,
        String raw
    ){
        return PROTOCOL+"\nACCOUNT="+account+"\nATTEMPT="+uuid+
            "\nKIND=STRICT_PREPARED"+
            "\nPREPARED_SHA256="+canonical+
            "\nSNAPSHOT_VERSION="+version+
            "\nACCOUNT_BYTES_SHA256="+raw+
            "\nSTAGE=POST_CONFIRMED\n";
    }
    private static PlayerSnapshot load(Path file,String account)
        throws IOException{
        Optional<PlayerSnapshot> read;
        try{
            read=new FilePlayerRepository(who->{
                if(!account.equals(who))
                    throw new IllegalArgumentException(
                        "G21.122 account resolver drift");
                return file;
            }).load(account);
        }catch(IOException|RuntimeException unsafe){
            throw veto("ACCOUNT_SNAPSHOT_UNREADABLE");
        }
        if(!read.isPresent())throw veto("ACCOUNT_SNAPSHOT_ABSENT");
        return read.get();
    }
    private static String canonical(PlayerSnapshot snapshot){
        return StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(snapshot);
    }

    /** Call only from the genuine strict writer's G21.120 hook after
     * successful strict Receipt, while G21.100 lifetime already held.
     * Evidence is forced before the original v1/v2 COMPLETE.
     */
    static void recordInside(
        Path selected,PlayerSnapshot snapshot,String uuid,
        StrictDurablePlayerSnapshotWriter.Receipt receipt
    )throws IOException{
        final Path file=normalized(selected);
        final String account=checked(snapshot.username());
        if(uuid==null||!uuid.matches(UUID)||
           receipt==null||!file.equals(receipt.file)||
           !account.equals(receipt.account)||
           !receipt.matchesSnapshot(snapshot)||
           MailboxPreparedRestartAdmission.inspect(snapshot).state!=
               MailboxPreparedRestartAdmission.State
                   .VALID_PREPARED_UNCLAIMED)
            throw veto("UNTRUSTED_STRICT_RESULT");
        byte[] bytes=MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(file,LOCK_MS,()->{
                PlayerSnapshot current=load(file,account);
                if(!canonical(current).equals(receipt.snapshotSha256)||
                   MailboxPreparedRestartAdmission.inspect(current).state!=
                       MailboxPreparedRestartAdmission.State
                           .VALID_PREPARED_UNCLAIMED)
                    throw veto("REAL_STRICT_PREPARED_BYTES_MISMATCH");
                return readSafe(file,MAX_ACCOUNT_BYTES);
            });
        final Path finalResult=result(file,uuid);
        final Path stage=Files.createTempFile(file.getParent(),
            file.getFileName()+".g21122-result-stage-",".tmp");
        try{
            byte[] content=payload(account,uuid,receipt.snapshotSha256,
                receipt.snapshotVersion,sha(bytes))
                .getBytes(StandardCharsets.US_ASCII);
            if(content.length>MAX_RESULT_BYTES)
                throw veto("RESULT_RECORD_TOO_LARGE");
            try(FileChannel channel=FileChannel.open(stage,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer buffer=ByteBuffer.wrap(content);
                while(buffer.hasRemaining())channel.write(buffer);
                channel.force(true);
            }
            Files.createLink(finalResult,stage);
            try(FileChannel directory=FileChannel.open(
                file.getParent(),StandardOpenOption.READ)){
                directory.force(true);
            }
        }finally{
            // Only the ephemeral stage file, never immutable evidence.
            Files.deleteIfExists(stage);
        }
    }

    private static void pairedOriginal(
        Path file,String account,String uuid
    )throws IOException{
        for(boolean begin:new boolean[]{true,false}){
            String stage=begin?"BEGIN":"COMPLETE";
            String ext=begin?".begin":".complete";
            Path v1=file.resolveSibling(file.getFileName()+
                ".g21103-writer-attempt-v1-"+uuid+ext);
            Path v2=file.resolveSibling(file.getFileName()+
                ".g21114-writer-kind-v2-"+uuid+ext);
            String originalV1=MailboxWriterAttemptEvidenceV1.PROTOCOL+
                "\nACCOUNT="+account+"\nATTEMPT="+uuid+
                "\nSTAGE="+stage+"\n";
            String originalV2=MailboxWriterKindEvidenceV2.PROTOCOL+
                "\nACCOUNT="+account+"\nATTEMPT="+uuid+
                "\nKIND=STRICT_PREPARED\nSTAGE="+stage+"\n";
            if(!originalV1.equals(new String(
                    readSafe(v1,MAX_RESULT_BYTES),
                    StandardCharsets.US_ASCII))||
               !originalV2.equals(new String(
                    readSafe(v2,MAX_RESULT_BYTES),
                    StandardCharsets.US_ASCII)))
                throw veto("ORPHAN_OR_CROSS_KIND_ORIGINAL_ATTEMPT");
        }
    }

    /** Called only while G21.100 held. Verify immutable v1/v2
     * identity, and one account semantic observation under G21.39.
     */
    static Report inspectInside(Path selected,String name)
        throws IOException{
        Path file=normalized(selected);
        checked(name);
        MailboxWriterAttemptEvidenceV1.Report v1=
            MailboxWriterAttemptEvidenceV1.inspectInside(file,name);
        MailboxWriterKindEvidenceV2.Report v2=
            MailboxWriterKindEvidenceV2.inspectInside(file,name);
        if(v1.status==MailboxWriterAttemptEvidenceV1.Status
                .INCOMPLETE_ATTEMPT_NO_CLEANUP||
           v2.status==MailboxWriterKindEvidenceV2.Status
                .INCOMPLETE_TYPED_NO_AUTHORITY)
            throw veto("INCOMPLETE_ATTEMPT_NO_RESULT_AUTHORITY");
        String prefix=file.getFileName()+SUFFIX;
        int count=0;
        String originalPrepared=null,originalRaw=null;
        int originalVersion=0;
        Status state=null;
        try(DirectoryStream<Path> records=Files.newDirectoryStream(
                file.getParent(),prefix+"*")){
            for(Path leaf:records){
                if(++count>MAX_RESULTS)throw veto("RESULT_LEAF_OVER_CAP");
                String suffix=leaf.getFileName().toString()
                    .substring(prefix.length());
                if(!suffix.matches(UUID+"\\.result"))
                    throw veto("UNKNOWN_RESULT_VERSION");
                String uuid=suffix.substring(0,suffix.length()-7);
                pairedOriginal(file,name,uuid);
                String body=new String(readSafe(leaf,MAX_RESULT_BYTES),
                    StandardCharsets.US_ASCII);
                final String[] values=new String[3];
                final Status[] outcome=new Status[1];
                MailboxAccountPublicationCoordinator
                    .withExclusivePublicationBounded(file,LOCK_MS,()->{
                        PlayerSnapshot current=load(file,name);
                        String now=canonical(current);
                        if(MailboxPreparedRestartAdmission.inspect(current)
                            .state==MailboxPreparedRestartAdmission.State
                                .VALID_PREPARED_UNCLAIMED){
                            values[0]=now;
                            values[1]=sha(readSafe(file,MAX_ACCOUNT_BYTES));
                            outcome[0]=Status
                                .BOUND_CURRENT_PREPARED_BYTES_NO_AUTHORITY;
                        }else if(MailboxAtomicTerminalSnapshot.inspect(current)
                            .state==MailboxAtomicTerminalSnapshot.State
                                .COHERENT_TERMINAL_NO_GRANT){
                            MailboxDurableIdempotencyIntentJournal.Observation j=
                                new MailboxDurableIdempotencyIntentJournal(
                                    who->{
                                        if(!name.equals(who))
                                            throw new IllegalArgumentException(
                                                "G21.122 journal account changed");
                                        return file;
                                    }).inspectInsidePublicationLock(name,file);
                            if(j.status!=
                                MailboxDurableIdempotencyIntentJournal.Status
                                    .TERMINAL_MATCH_NO_COMMIT||
                               !j.recordValidated||
                               !now.equals(j.terminalSha256))
                                throw veto("UNCORROBORATED_TERMINAL_HISTORY");
                            values[0]=j.preparedSha256;
                            values[1]=null;
                            outcome[0]=Status
                                .BOUND_HISTORICAL_PREPARED_WITH_TERMINAL_JOURNAL_NO_AUTHORITY;
                        }else{
                            throw veto("UNSAFE_ACCOUNT_PHASE");
                        }
                        return Boolean.TRUE;
                    });
                // Must validate exact canonical raw record shape and
                // account UUID kind; terminal phase no longer has the
                // original bytes, so extract only originally recorded
                // hash while validating against journal PREPARED hash.
                String pre=PROTOCOL+"\nACCOUNT="+name+
                    "\nATTEMPT="+uuid+
                    "\nKIND=STRICT_PREPARED\nPREPARED_SHA256="+values[0]+
                    "\nSNAPSHOT_VERSION=";
                if(!body.startsWith(pre))
                    throw veto("PREPARED_OR_ATTEMPT_RESULT_CHANGED");
                String remaining=body.substring(pre.length());
                int line=remaining.indexOf('\n');
                if(line<=0)throw veto("MALFORMED_SNAPSHOT_VERSION");
                String versionText=remaining.substring(0,line);
                int version;
                try{
                    version=Integer.parseInt(versionText);
                }catch(NumberFormatException bad){
                    throw veto("INVALID_SNAPSHOT_VERSION");
                }
                if(version!=PlayerSnapshot.CURRENT_VERSION||
                   !versionText.equals(Integer.toString(version)))
                    throw veto("NONCANONICAL_SNAPSHOT_VERSION");
                String rest=remaining.substring(line+1);
                if(!rest.startsWith("ACCOUNT_BYTES_SHA256="))
                    throw veto("MISSING_ACCOUNT_DIGEST");
                int eol=rest.indexOf('\n');
                if(eol<0)throw veto("INVALID_RAW_DIGEST");
                String stored=rest.substring(
                    "ACCOUNT_BYTES_SHA256=".length(),eol);
                if(!stored.matches("[0-9a-f]{64}")||
                   !rest.substring(eol+1).equals(
                        "STAGE=POST_CONFIRMED\n"))
                    throw veto("NONCANONICAL_RESULT_PAYLOAD");
                if(values[1]!=null&&!stored.equals(values[1]))
                    throw veto("ACTUAL_PREPARED_FILE_BYTES_CHANGED");
                originalPrepared=values[0];
                originalRaw=stored;
                originalVersion=version;
                state=outcome[0];
            }
        }
        if(count==0)
            return new Report(
                Status.NO_RESULT_BINDING_LEGACY_OR_UNKNOWN,
                0,null,null,0,false);
        if(count!=1||
           v2.status!=MailboxWriterKindEvidenceV2.Status
                .OBSERVED_TYPED_PAIRED_NO_AUTHORITY||
           v2.untypedV1Leaves!=0)
            throw veto("MULTIPLE_OR_MIXED_RESULT_EVIDENCE");
        return new Report(state,count,originalPrepared,originalRaw,
            originalVersion,
            state==Status.BOUND_CURRENT_PREPARED_BYTES_NO_AUTHORITY);
    }

    static Report inspect(
        FilePlayerRepository.PathResolver resolver,String name
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Path file=normalized(Objects.requireNonNull(
            resolver.resolve(checked(name)),"account"));
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(file),
                LOCK_MS,()->inspectInside(file,name));
    }

    /** Opt-in negative-only: require G21.119 real-only pinned five
     * planes, then original PREPARED result and current journal match.
     * Two observations are NOT an atomic epoch or source signature.
     */
    static Report requirePinnedRealBoundStrictPrepared(
        FilePlayerRepository.PathResolver resolver,String name,
        String v1Portable,String v1Sha,String accountSha,
        String preparedPin,String commitPin,String v2Sha
    )throws IOException{
        MailboxRealWriterSemanticBoundaryV1.Report older=
            MailboxRealWriterSemanticBoundaryV1
                .requirePinnedRealWriterCandidate(
                    resolver,name,v1Portable,v1Sha,accountSha,
                    preparedPin,commitPin,v2Sha);
        Report bound=inspect(resolver,name);
        if(bound.status!=Status
                .BOUND_HISTORICAL_PREPARED_WITH_TERMINAL_JOURNAL_NO_AUTHORITY||
           bound.boundAttempts!=1||
           older.diskCommit==null||
           !bound.preparedSha256.equals(
               new MailboxDurableIdempotencyIntentJournal(
                   resolver).inspect(name).preparedSha256))
            throw veto("STRICT_PREPARED_NOT_BOUND_TO_PINNED_TERMINAL");
        return bound;
    }

    private MailboxStrictPreparedResultBindingV3(){}
}
