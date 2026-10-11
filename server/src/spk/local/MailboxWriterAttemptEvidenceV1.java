package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * G21.103: OPT-IN immutable versioned per-attempt writer evidence.
 *
 * Not installed into the three existing save hot paths: that would
 * change write frequency/latency and requires a separate deployment
 * and migration contract. No status authorizes temporary-file unlink,
 * COMMIT deletion, reward granting or automatic session restoration.
 */
final class MailboxWriterAttemptEvidenceV1 {
    static final String PROTOCOL="SPK-MAILBOX-WRITER-ATTEMPT-V1";
    static final int MAX_RECORDS=32;
    private static final int MAX_RECORD_BYTES=512;
    private static final long LOCK_MS=1500L;
    private static final String PREFIX=".g21103-writer-attempt-";
    private static final String V1="v1-";

    enum Status {
        NO_VERSIONED_EVIDENCE_LEGACY_OR_UNKNOWN,
        INCOMPLETE_ATTEMPT_NO_CLEANUP,
        ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP
    }
    private enum Stage { BEGIN, COMPLETE }

    static final class Report {
        final Status status;
        final int started;
        final int completed;
        final boolean completeWriterPopulationKnown=false;
        final boolean everyHistoricalGenerationParticipates=false;
        final boolean crashEpochSealed=false;
        final boolean uncooperativeWritersExcluded=false;
        final boolean futureWritersExcluded=false;
        final boolean unlinkAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean accountMutationAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Report(Status status,int begin,int complete){
            this.status=status;
            started=begin;
            completed=complete;
        }
    }

    private static String checkedAccount(String account){
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.103 invalid canonical account");
        return account;
    }
    private static Path pinned(
        FilePlayerRepository.PathResolver resolver,String account
    ){
        return Objects.requireNonNull(
            resolver.resolve(checkedAccount(account)),"account path")
            .toAbsolutePath().normalize();
    }
    private static String prefix(Path file){
        return file.getFileName().toString()+PREFIX;
    }
    private static String recordText(
        String account,String uuid,Stage stage
    ){
        return PROTOCOL+"\nACCOUNT="+account+"\nATTEMPT="+
            uuid+"\nSTAGE="+stage.name()+"\n";
    }
    private static Path recordPath(
        Path file,String uuid,Stage stage
    ){
        return file.resolveSibling(prefix(file)+V1+uuid+"."+
            stage.name().toLowerCase(java.util.Locale.ROOT));
    }
    private static void forceDirectory(Path parent)throws IOException{
        try(FileChannel directory=FileChannel.open(parent,
                StandardOpenOption.READ)){
            directory.force(true);
        }
    }
    private static void createImmutable(
        Path file,String account,String uuid,Stage stage
    )throws IOException{
        Path parent=file.getParent();
        Path finalPath=recordPath(file,uuid,stage);
        Path temp=Files.createTempFile(parent,
            file.getFileName().toString()+".g21103-evidence-stage-",
            ".tmp");
        try{
            byte[] data=recordText(account,uuid,stage)
                .getBytes(StandardCharsets.US_ASCII);
            try(FileChannel channel=FileChannel.open(
                    temp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer buf=ByteBuffer.wrap(data);
                while(buf.hasRemaining())channel.write(buf);
                channel.force(true);
            }
            // CREATE-ONLY hard link: never replace an existing record.
            Files.createLink(finalPath,temp);
            forceDirectory(parent);
        }finally{
            // Only temporary staging leaf, not a durable BEGIN/COMPLETE.
            // The hard-linked immutable record MUST never be deleted.
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Opt-in testable wrapper. The participating lifetime lock covers
     * BEGIN durability, operation, and COMPLETE durability, in that order.
     * An abrupt JVM death releases the OS lock but preserves BEGIN.
     */
    static <T> T runRecorded(
        FilePlayerRepository.PathResolver resolver,String account,
        MailboxAccountPublicationCoordinator.Operation<T> operation
    )throws IOException{
        Objects.requireNonNull(operation,"operation");
        Path file=pinned(Objects.requireNonNull(resolver,"resolver"),
            account);
        return MailboxPublicationWriterLifecycle.withWriter(file,()->{
            String uuid=UUID.randomUUID().toString();
            createImmutable(file,account,uuid,Stage.BEGIN);
            T result=operation.execute();
            createImmutable(file,account,uuid,Stage.COMPLETE);
            return result;
        });
    }

    private static BasicFileAttributes physical(Path p)
        throws IOException{
        BasicFileAttributes a=Files.readAttributes(
            p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!a.isRegularFile()||a.isSymbolicLink()||
           a.fileKey()==null||a.size()<=0L||
           a.size()>MAX_RECORD_BYTES)
            throw new IOException(
                "G21.103 UNSAFE_WRITER_EVIDENCE_NO_UNLINK "+p);
        return a;
    }
    private static String readBounded(Path p,int exactLength)
        throws IOException{
        ByteBuffer b=ByteBuffer.allocate(exactLength+1);
        try(FileChannel channel=FileChannel.open(
                p,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(b.hasRemaining()&&channel.read(b)>0){}
        }
        if(b.position()!=exactLength)
            throw new IOException(
                "G21.103 CHANGED_WRITER_EVIDENCE_NO_UNLINK "+p);
        return new String(b.array(),0,exactLength,
            StandardCharsets.US_ASCII);
    }

    private static Report inspectInside(
        Path file,String account
    )throws IOException{
        final String prefix=prefix(file);
        Map<String,Integer> stages=new HashMap<>();
        // First bound the WHOLE account-scoped candidate set before
        // touching record contents. Otherwise an early malformed file
        // could mask an over-capacity directory and give inconsistent
        // failure classification depending on enumeration order.
        List<Path> discovered=new ArrayList<>();
        try(DirectoryStream<Path> dir=Files.newDirectoryStream(
                file.getParent(),prefix+"*")){
            for(Path p:dir){
                if(discovered.size()>=MAX_RECORDS)
                    throw new IOException(
                        "G21.103 WRITER_EVIDENCE_OVER_CAPACITY_NO_UNLINK");
                discovered.add(p);
            }
        }catch(NoSuchFileException vanished){
            throw new IOException(
                "G21.103 EVIDENCE_VANISHED_NO_UNLINK",vanished);
        }
        try{
            for(Path p:discovered){
                String name=p.getFileName().toString();
                String tail=name.substring(prefix.length());
                if(!tail.matches(
                        "v1-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
                        "[0-9a-f]{4}-[0-9a-f]{12}\\.(begin|complete)"))
                    throw new IOException(
                        "G21.103 UNKNOWN_WRITER_EVIDENCE_VERSION_NO_UNLINK "+
                        name);
                int dot=tail.lastIndexOf('.');
                String uuid=tail.substring(3,dot);
                Stage stage="begin".equals(tail.substring(dot+1))
                    ?Stage.BEGIN:Stage.COMPLETE;
                BasicFileAttributes before=physical(p);
                String expected=recordText(account,uuid,stage);
                String read=readBounded(p,(int)before.size());
                BasicFileAttributes after=physical(p);
                if(!expected.equals(read)||
                   !Objects.equals(before.fileKey(),after.fileKey())||
                   before.size()!=after.size()||
                   !before.lastModifiedTime().equals(
                       after.lastModifiedTime()))
                    throw new IOException(
                        "G21.103 INVALID_OR_CHANGED_WRITER_RECORD_NO_UNLINK "+
                        name);
                int flag=stage==Stage.BEGIN?1:2;
                int prev=stages.getOrDefault(uuid,0);
                if((prev&flag)!=0)
                    throw new IOException(
                        "G21.103 DUPLICATE_WRITER_RECORD_NO_UNLINK");
                stages.put(uuid,prev|flag);
            }
        }catch(NoSuchFileException vanished){
            throw new IOException(
                "G21.103 EVIDENCE_VANISHED_NO_UNLINK",vanished);
        }
        if(stages.isEmpty())
            return new Report(
                Status.NO_VERSIONED_EVIDENCE_LEGACY_OR_UNKNOWN,0,0);
        int begins=0,completes=0;
        for(int flags:stages.values()){
            if((flags&1)==0)
                throw new IOException(
                    "G21.103 COMPLETE_WITHOUT_BEGIN_NO_UNLINK");
            begins++;
            if((flags&2)!=0)completes++;
        }
        return new Report(
            begins==completes
                ?Status.ALL_OBSERVED_ATTEMPTS_PAIRED_NO_CLEANUP
                :Status.INCOMPLETE_ATTEMPT_NO_CLEANUP,
            begins,completes);
    }

    /** Read-only; successful observation never returns a cleanup token. */
    static Report inspect(
        FilePlayerRepository.PathResolver resolver,
        String account
    )throws IOException{
        Path file=pinned(Objects.requireNonNull(resolver,"resolver"),
            account);
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(file),
                LOCK_MS,()->inspectInside(file,account));
    }

    private MailboxWriterAttemptEvidenceV1(){}
}
