package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * G21.105 opt-in read-only portable snapshot of G21.103/104 records.
 * Exports in memory only; no create, delete, rotate or truncate operation.
 * Neither a matching hash nor a paired ledger confers cleanup authority.
 */
final class MailboxWriterEvidencePortableArchiveV1 {
    static final String FORMAT="SPK-G21105-READONLY-ARCHIVE-V1";
    static final String AUTHORITY=
        "AUTHORITY|NO_UNLINK_NO_CLEANUP_NO_GRANT_NO_REPLAY_NO_SESSION_NO_ACK";
    static final int MAX_PORTABLE_BYTES=32768;
    private static final int MAX_RECORD_BYTES=512;
    private static final long LOCK_TIMEOUT_MS=1500L;
    private static final String RECORD_PREFIX=
        ".g21103-writer-attempt-v1-";
    private static final String RECORD_PATTERN=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}\\.(begin|complete)";

    static final class Verified {
        final String account;
        final MailboxWriterAttemptEvidenceV1.Status status;
        final int started;
        final int completed;
        final int records;
        final String sha256;
        final boolean hardwareDurabilityProved=false;
        final boolean completeWriterPopulationProved=false;
        final boolean sourceStillUnchangedProved=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean recordPruningAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Verified(
            String account,
            MailboxWriterAttemptEvidenceV1.Status status,
            int began,int finished,String hash
        ){
            this.account=account;
            this.status=status;
            started=began;
            completed=finished;
            records=began+finished;
            sha256=hash;
        }
    }
    static final class Snapshot {
        final String portable;
        final String sha256;
        final Verified verified;
        final boolean savedToExternalArchive=false;
        final boolean ledgerMutationAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        private Snapshot(String bytes,String hash,Verified result){
            portable=bytes;
            sha256=hash;
            verified=result;
        }
    }

    private static IOException invalid(String detail){
        return new IOException(
            "G21.105 INVALID_PORTABLE_WRITER_ARCHIVE_NO_UNLINK "+detail);
    }
    private static String sha256(String value){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] hash=digest.digest(
                value.getBytes(StandardCharsets.US_ASCII));
            StringBuilder out=new StringBuilder(hash.length*2);
            for(byte b:hash)
                out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException(impossible);
        }
    }
    private static boolean canonicalAccount(String value){
        return value!=null&&value.matches("[a-z0-9_-]{1,64}");
    }
    private static byte[] readExact(
        Path path,BasicFileAttributes attrs
    )throws IOException{
        if(attrs.size()<1||attrs.size()>MAX_RECORD_BYTES)
            throw invalid("bounded record size "+path);
        ByteBuffer bytes=ByteBuffer.allocate((int)attrs.size()+1);
        try(FileChannel input=FileChannel.open(path,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(bytes.hasRemaining()){
                int n=input.read(bytes);
                if(n<0)break;
                if(n==0)throw invalid("stalled record read "+path);
            }
        }
        if(bytes.position()!=attrs.size())
            throw invalid("record changed size "+path);
        return Arrays.copyOf(bytes.array(),(int)attrs.size());
    }
    private static BasicFileAttributes physical(Path path)
        throws IOException{
        BasicFileAttributes a=Files.readAttributes(
            path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!a.isRegularFile()||a.isSymbolicLink()||
           a.fileKey()==null||a.size()<1||a.size()>MAX_RECORD_BYTES)
            throw invalid("unsafe record leaf "+path);
        return a;
    }
    private static String validatedPayload(
        String account,String uuid,String stage
    ){
        return MailboxWriterAttemptEvidenceV1.PROTOCOL+
            "\nACCOUNT="+account+"\nATTEMPT="+uuid+
            "\nSTAGE="+stage.toUpperCase(Locale.ROOT)+"\n";
    }

    /**
     * Verify a self-contained, independently transported manifest.
     * Caller must pin the expected SHA-256 from an independent trusted
     * channel: a digest accompanying an attacker-controlled manifest
     * provides error detection, not authenticated provenance.
     */
    static Verified verifyPortable(
        String portable,String trustedSha256
    )throws IOException{
        if(portable==null||trustedSha256==null||
           !trustedSha256.matches("[0-9a-f]{64}"))
            throw invalid("missing trusted SHA-256");
        if(portable.length()>MAX_PORTABLE_BYTES||portable.isEmpty())
            throw invalid("portable length");
        for(int i=0;i<portable.length();i++)
            if(portable.charAt(i)>127)
                throw invalid("non-ASCII portable bytes");
        String actualHash=sha256(portable);
        if(!actualHash.equals(trustedSha256))
            throw invalid("SHA256_MISMATCH");
        String[] lines=portable.split("\n",-1);
        if(lines.length<7||!lines[0].equals(FORMAT)||
           !lines[lines.length-1].isEmpty()||
           !lines[lines.length-2].equals(AUTHORITY))
            throw invalid("bad archive framing or policy");
        if(!lines[1].startsWith("ACCOUNT|"))
            throw invalid("missing account");
        String account=lines[1].substring("ACCOUNT|".length());
        if(!canonicalAccount(account))
            throw invalid("invalid account");
        if(!lines[2].startsWith("STATE|"))
            throw invalid("missing state");
        MailboxWriterAttemptEvidenceV1.Status state;
        try{
            state=MailboxWriterAttemptEvidenceV1.Status.valueOf(
                lines[2].substring("STATE|".length()));
        }catch(IllegalArgumentException invalidState){
            throw invalid("unrecognized state");
        }
        if(!lines[3].matches("COUNTS\\|[0-9]+\\|[0-9]+\\|[0-9]+"))
            throw invalid("invalid count framing");
        String[] counts=lines[3].substring("COUNTS|".length())
            .split("\\|",-1);
        final int claimedBegins,claimedCompletions,claimedRecords;
        try{
            claimedBegins=Integer.parseInt(counts[0]);
            claimedCompletions=Integer.parseInt(counts[1]);
            claimedRecords=Integer.parseInt(counts[2]);
        }catch(NumberFormatException malformed){
            throw invalid("invalid numeric count");
        }
        int total=lines.length-6;
        if(total<1||total>MailboxWriterAttemptEvidenceV1.MAX_RECORDS||
           claimedRecords!=total)
            throw invalid("empty/overfull/inconsistent evidence list");
        String beforeName=null;
        Map<String,Integer> flags=new HashMap<>();
        String prefix=account+".properties"+RECORD_PREFIX;
        for(int n=0;n<total;n++){
            String line=lines[n+4];
            String[] columns=line.split("\\|",-1);
            if(columns.length!=3||!"RECORD".equals(columns[0]))
                throw invalid("record fields");
            String filename=columns[1];
            if(!filename.startsWith(prefix))
                throw invalid("cross-account filename");
            String suffix=filename.substring(prefix.length());
            if(!suffix.matches(RECORD_PATTERN))
                throw invalid("non-v1 filename");
            if(beforeName!=null&&beforeName.compareTo(filename)>=0)
                throw invalid("unsorted or duplicate filename");
            beforeName=filename;
            int dot=suffix.lastIndexOf('.');
            String uuid=suffix.substring(0,dot);
            String stage=suffix.substring(dot+1);
            byte[] payload;
            try{
                payload=Base64.getDecoder().decode(columns[2]);
            }catch(IllegalArgumentException malformed){
                throw invalid("bad Base64 payload");
            }
            if(payload.length<1||payload.length>MAX_RECORD_BYTES||
               !Base64.getEncoder().encodeToString(payload)
                   .equals(columns[2])||
               !validatedPayload(account,uuid,stage).equals(
                   new String(payload,StandardCharsets.US_ASCII)))
                throw invalid("noncanonical payload or account mismatch");
            int flag="begin".equals(stage)?1:2;
            int prior=flags.getOrDefault(uuid,0);
            if((prior&flag)!=0)
                throw invalid("duplicate attempt stage");
            flags.put(uuid,prior|flag);
        }
        int begins=0,completions=0;
        for(int flag:flags.values()){
            if((flag&1)==0)
                throw invalid("COMPLETE_WITHOUT_BEGIN");
            begins++;
            if((flag&2)!=0)completions++;
        }
        MailboxWriterAttemptEvidenceV1.Status expected=
            begins==completions
                ?MailboxWriterAttemptEvidenceV1.Status
                    .ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP
                :MailboxWriterAttemptEvidenceV1.Status
                    .INCOMPLETE_ATTEMPT_NO_CLEANUP;
        if(begins!=claimedBegins||
           completions!=claimedCompletions||
           state!=expected)
            throw invalid("status/record counters mismatched");
        return new Verified(
            account,expected,begins,completions,actualHash);
    }

    /**
     * Capture is READ-ONLY. Same lifecycle lock as v1 writers, exact
     * one account path resolution and bounded NOFOLLOW record access.
     * G21.103 scan runs before AND after the bounded byte capture.
     */
    static Snapshot capture(
        FilePlayerRepository.PathResolver resolver,String account
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        if(!canonicalAccount(account))
            throw new IllegalArgumentException(
                "G21.105 noncanonical account");
        Path file=Objects.requireNonNull(
            resolver.resolve(account),"account file")
            .toAbsolutePath().normalize();
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(file),
                LOCK_TIMEOUT_MS,()->captureInside(file,account));
    }

    // G21.106: may be called only while the caller already owns
    // the G21.100 lifecycle lease; avoid nested lifecycle locks.
    static Snapshot captureInside(
        Path accountFile,String account
    )throws IOException{
        MailboxWriterAttemptEvidenceV1.Report first=
            MailboxWriterAttemptEvidenceV1
                .inspectInside(accountFile,account);
        if(first.recordCount==0)
            throw new IOException(
                "G21.105 NO_V1_ARCHIVE_EVIDENCE_NO_UNLINK");
        final String prefix=accountFile.getFileName().toString()+
            ".g21103-writer-attempt-";
        List<Path> found=new ArrayList<>();
        try(DirectoryStream<Path> dir=Files.newDirectoryStream(
                accountFile.getParent(),prefix+"*")){
            for(Path record:dir){
                if(found.size()>=
                        MailboxWriterAttemptEvidenceV1.MAX_RECORDS)
                    throw invalid("over-capacity record leaves");
                found.add(record);
            }
        }catch(NoSuchFileException absent){
            throw invalid("record disappeared during capture");
        }
        Collections.sort(found);
        if(found.size()!=first.recordCount)
            throw invalid("ledger changed between scans");
        StringBuilder portable=new StringBuilder();
        portable.append(FORMAT).append('\n');
        portable.append("ACCOUNT|").append(account).append('\n');
        portable.append("STATE|").append(first.status).append('\n');
        portable.append("COUNTS|").append(first.started).append('|')
            .append(first.completed).append('|')
            .append(first.recordCount).append('\n');
        for(Path p:found){
            BasicFileAttributes before=physical(p);
            byte[] bytes=readExact(p,before);
            BasicFileAttributes after=physical(p);
            if(!before.fileKey().equals(after.fileKey())||
               before.size()!=after.size()||
               !before.lastModifiedTime().equals(
                   after.lastModifiedTime()))
                throw invalid("physical file identity changed");
            portable.append("RECORD|")
                .append(p.getFileName().toString())
                .append('|').append(
                    Base64.getEncoder().encodeToString(bytes))
                .append('\n');
            if(portable.length()>MAX_PORTABLE_BYTES)
                throw invalid("bounded portable size");
        }
        portable.append(AUTHORITY).append('\n');
        String serialized=portable.toString();
        String digest=sha256(serialized);
        Verified verified=verifyPortable(serialized,digest);
        MailboxWriterAttemptEvidenceV1.Report last=
            MailboxWriterAttemptEvidenceV1
                .inspectInside(accountFile,account);
        if(last.started!=first.started||
           last.completed!=first.completed||
           last.status!=first.status||
           last.recordCount!=found.size()||
           verified.started!=first.started||
           verified.completed!=first.completed)
            throw invalid("ledger changed after capture");
        return new Snapshot(serialized,digest,verified);
    }

    private MailboxWriterEvidencePortableArchiveV1(){}
}
