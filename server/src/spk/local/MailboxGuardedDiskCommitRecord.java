package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;

/**
 * G21.86: opt-in write-once DISK commit record for a G21.66 confirmed
 * terminal file operation, after a matching G21.83 PREPARED intent.
 *
 * This is a disk ordering primitive, NOT end-to-end Mailbox settlement.
 * A journal or commit record is unauthenticated and cannot establish
 * World live apply, exactly-once replay, session admission or client ACK.
 */
final class MailboxGuardedDiskCommitRecord {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2186_DISK_COMMIT_RECORD_NO_GRANT";
    static final String SUFFIX=".g2186-mailbox-disk-commit";
    private static final String FORMAT="SPK-G2186-DISK-COMMIT-V1";
    private static final String MARKER_STATE="DISK_COMMIT_RECORDED_NO_LIVE_APPLY";
    private static final String TERMINAL_PREFIX="extension."+
        MailboxAtomicTerminalSnapshot.NAMESPACE+".";
    private static final int MAX_BYTES=1024;

    enum Phase {
        BEFORE_TEMP_CREATE, BEFORE_LINK, AFTER_LINK,
        BEFORE_DIRECTORY_FORCE, AFTER_DIRECTORY_FORCE
    }
    interface FaultPoint { void check(Phase phase)throws IOException; }
    static final class UnconfirmedRecordException extends IOException {
        UnconfirmedRecordException(Throwable cause){
            super("G21.86 DISK_COMMIT_RECORD_UNCONFIRMED_NO_GRANT",cause);
        }
    }

    enum Status {
        ABSENT, DISK_COMMIT_MATCH_NO_LIVE_APPLY,
        NEGATIVE_MARKER_MANUAL_HOLD,
        MISSING_ACCOUNT_QUARANTINE,
        PREPARED_NOT_COMMITTED,
        INTENT_CONFLICT_QUARANTINE,
        DIVERGENT_TERMINAL_QUARANTINE,
        INVALID_RECORD_QUARANTINE
    }

    static final class Observation {
        final Status status;
        final String account;
        final String messageId;
        final String intentKey;
        final String preparedSha256;
        final String hypotheticalSha256;
        final String terminalSha256;
        final boolean recordValidated;
        final boolean diskCommitRecordMatched;
        // Disk evidence is not end-to-end transaction settlement.
        final boolean transactionCommitted=false;
        final boolean liveApplied=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        Observation(Status status,String account,Record r){
            this.status=status;
            this.account=account;
            recordValidated=r!=null;
            diskCommitRecordMatched=
                status==Status.DISK_COMMIT_MATCH_NO_LIVE_APPLY;
            messageId=r==null?null:r.message;
            intentKey=r==null?null:r.key;
            preparedSha256=r==null?null:r.prepared;
            hypotheticalSha256=r==null?null:r.hypothetical;
            terminalSha256=r==null?null:r.terminal;
        }
    }

    private static final class Record {
        final String account,message,key,prepared,hypothetical,terminal;
        Record(String account,String message,String key,
               String prepared,String hypothetical,String terminal){
            this.account=account;
            this.message=message;
            this.key=key;
            this.prepared=prepared;
            this.hypothetical=hypothetical;
            this.terminal=terminal;
        }
    }

    private final FilePlayerRepository.PathResolver resolver;
    private final FaultPoint fault;
    MailboxGuardedDiskCommitRecord(
        FilePlayerRepository.PathResolver resolver
    ){
        this(resolver,phase->{});
    }
    MailboxGuardedDiskCommitRecord(
        FilePlayerRepository.PathResolver resolver,FaultPoint fault
    ){
        this.resolver=Objects.requireNonNull(resolver,"resolver");
        this.fault=Objects.requireNonNull(fault,"fault");
    }

    private Path accountFile(String username){
        if(username==null||!username.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.86 noncanonical account");
        return Objects.requireNonNull(resolver.resolve(username),
            "G21.86 selected account file").toAbsolutePath().normalize();
    }
    Path recordPath(String username){
        Path file=accountFile(username);
        return file.resolveSibling(file.getFileName().toString()+SUFFIX);
    }

    /**
     * Only after G21.66 file-success, never inside client claim code.
     * Publication order under cooperating per-account lock:
     * PREPARED intent already forced, terminal file already forced,
     * receipt checked, then forced record bytes, create-only hard link,
     * then force record directory. No overwrite/rollback/cleanup.
     */
    void recordConfirmedDiskTerminal(
        MailboxSettlementPostimagePlanner.Proposal p,
        StrictDurablePlayerSnapshotWriter.Receipt receipt
    )throws IOException{
        if(p==null||receipt==null)
            throw new IOException("G21.86 MISSING_STRICT_RECEIPT_NO_GRANT");
        final PlayerSnapshot terminal;
        try{
            terminal=MailboxAtomicTerminalSnapshot.compose(p);
        }catch(RuntimeException invalid){
            throw new IOException("G21.86 invalid proposal",invalid);
        }
        final Path file=accountFile(p.account);
        if(!receipt.file.equals(file)||!receipt.matchesSnapshot(terminal)||
           !StrictDurablePlayerSnapshotWriter.AUTHORITY.equals(
               receipt.authority))
            throw new IOException(
                "G21.86 RECEIPT_TERMINAL_OR_PATH_MISMATCH_NO_GRANT");

        final Record desired=new Record(
            p.account,p.messageId,p.idempotencyKey,
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                p.preparedPreimage),
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                p.hypotheticalPostimage),
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                terminal)
        );
        final Path marker=recordPath(p.account);
        // G21.100: lifecycle covers forced temp through link/cleanup.
        // Keep lock order lifecycle before existing account lock.
        MailboxPublicationWriterLifecycle.withWriter(file,()->
            MailboxAccountPublicationCoordinator.withExclusivePublication(
            file,()->{
                if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException(
                        "G21.86 COMMIT_RECORD_ALREADY_EXISTS_NO_GRANT");
                if(negative(p.account,file))
                    throw new IOException(
                        "G21.86 NEGATIVE_FENCE_MANUAL_REVIEW_NO_GRANT");
                MailboxDurableIdempotencyIntentJournal.Observation intent=
                    new MailboxDurableIdempotencyIntentJournal(
                        pinned(p.account,file))
                        .inspectInsidePublicationLock(p.account,file);
                if(intent.status!=
                        MailboxDurableIdempotencyIntentJournal.Status
                            .TERMINAL_MATCH_NO_COMMIT||
                   !sameIntent(intent,desired))
                    throw new IOException(
                        "G21.86 PREPARED_INTENT_MISMATCH_NO_GRANT");
                if(!exactTerminal(file,desired))
                    throw new IOException(
                        "G21.86 DISK_NOT_EXACT_TERMINAL_NO_GRANT");

                byte[] body=encode(desired);
                Path parent=marker.getParent();
                fault.check(Phase.BEFORE_TEMP_CREATE);
                Path temp=Files.createTempFile(
                    parent,marker.getFileName().toString()+".write-",
                    ".tmp");
                try{
                    try(FileChannel channel=FileChannel.open(
                            temp,StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)){
                        ByteBuffer bytes=ByteBuffer.wrap(body);
                        while(bytes.hasRemaining())channel.write(bytes);
                        channel.force(true);
                    }
                    fault.check(Phase.BEFORE_LINK);
                    if(negative(p.account,file)||
                       !exactTerminal(file,desired)||
                       !sameIntent(
                           new MailboxDurableIdempotencyIntentJournal(
                               pinned(p.account,file))
                               .inspectInsidePublicationLock(
                                   p.account,file),desired))
                        throw new IOException(
                            "G21.86 PRELINK_ACCOUNT_OR_INTENT_CHANGED");
                    Files.createLink(marker,temp);
                    try{
                        fault.check(Phase.AFTER_LINK);
                        fault.check(Phase.BEFORE_DIRECTORY_FORCE);
                        try(FileChannel directory=FileChannel.open(
                                parent,StandardOpenOption.READ)){
                            directory.force(true);
                        }
                        fault.check(Phase.AFTER_DIRECTORY_FORCE);
                    }catch(IOException|RuntimeException uncertain){
                        throw new UnconfirmedRecordException(uncertain);
                    }
                    return null;
                }finally{
                    // Once linked, NEVER delete a possibly durable
                    // COMMIT record after uncertain force or crash.
                    Files.deleteIfExists(temp);
                }
            }));
    }

    /**
     * Bounded, read-only classification under SAME lock as G21.85.
     * Revalidates record, PREPARED intent, account postimage and markers.
     * A valid disk record still NEVER authorizes reward replay or ACK.
     */
    Observation inspect(String username)throws IOException{
        final Path file=accountFile(username);
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(file,1500L,
                ()->inspectInsidePublicationLock(username,file));
    }

    Observation inspectInsidePublicationLock(
        String username,Path selectedFile
    )throws IOException{
        Path file=accountFile(username);
        if(!file.equals(selectedFile.toAbsolutePath().normalize()))
            throw new IOException(
                "G21.86 COMMIT_RECORD_PATH_CHANGED_NO_GRANT");
        Path marker=recordPath(username);
        if(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS))
            return new Observation(Status.ABSENT,username,null);
        if(negative(username,file))
            return new Observation(
                Status.NEGATIVE_MARKER_MANUAL_HOLD,username,null);
        final Record record;
        try{
            record=decode(MailboxNegativeMarkerBoundedRead.read(
                marker,1,MAX_BYTES));
            if(!username.equals(record.account))
                return new Observation(
                    Status.INVALID_RECORD_QUARANTINE,username,null);
        }catch(IOException|RuntimeException invalid){
            return new Observation(
                Status.INVALID_RECORD_QUARANTINE,username,null);
        }
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            return new Observation(
                Status.MISSING_ACCOUNT_QUARANTINE,username,record);
        Optional<PlayerSnapshot> saved;
        try{
            saved=new FilePlayerRepository(pinned(username,file))
                .load(username);
        }catch(IOException|RuntimeException corrupted){
            return new Observation(
                Status.DIVERGENT_TERMINAL_QUARANTINE,username,record);
        }
        if(!saved.isPresent())
            return new Observation(
                Status.MISSING_ACCOUNT_QUARANTINE,username,record);
        if(MailboxPreparedRestartAdmission.inspect(saved.get()).state==
               MailboxPreparedRestartAdmission.State
                   .VALID_PREPARED_UNCLAIMED)
            return new Observation(
                Status.PREPARED_NOT_COMMITTED,username,record);

        MailboxDurableIdempotencyIntentJournal.Observation intent=
            new MailboxDurableIdempotencyIntentJournal(
                pinned(username,file))
                .inspectInsidePublicationLock(username,file);
        if(intent.status!=
                MailboxDurableIdempotencyIntentJournal.Status
                    .TERMINAL_MATCH_NO_COMMIT||
           !sameIntent(intent,record))
            return new Observation(
                Status.INTENT_CONFLICT_QUARANTINE,username,record);
        return new Observation(
            exactTerminal(file,record)
                ?Status.DISK_COMMIT_MATCH_NO_LIVE_APPLY
                :Status.DIVERGENT_TERMINAL_QUARANTINE,
            username,record
        );
    }

    private static boolean sameIntent(
        MailboxDurableIdempotencyIntentJournal.Observation intent,
        Record r
    ){
        return intent.recordValidated&&
            r.account.equals(intent.account)&&
            r.message.equals(intent.messageId)&&
            r.key.equals(intent.intentKey)&&
            r.prepared.equals(intent.preparedSha256)&&
            r.terminal.equals(intent.terminalSha256);
    }

    private static boolean exactTerminal(
        Path file,Record expected
    )throws IOException{
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            return false;
        Optional<PlayerSnapshot> disk=
            new FilePlayerRepository(pinned(expected.account,file))
                .load(expected.account);
        if(!disk.isPresent())return false;
        PlayerSnapshot p=disk.get();
        return MailboxAtomicTerminalSnapshot.inspect(p).state==
                MailboxAtomicTerminalSnapshot.State
                    .COHERENT_TERMINAL_NO_GRANT&&
            expected.terminal.equals(
                StrictDurablePlayerSnapshotWriter
                    .canonicalSnapshotSha256(p))&&
            expected.prepared.equals(p.value(TERMINAL_PREFIX+"before"))&&
            expected.hypothetical.equals(p.value(
                TERMINAL_PREFIX+"after"))&&
            expected.key.equals(p.value(TERMINAL_PREFIX+"key"))&&
            expected.message.equals(p.value(TERMINAL_PREFIX+"message"));
    }

    private static FilePlayerRepository.PathResolver pinned(
        String name,Path file
    ){
        return other->{
            if(!name.equals(other))
                throw new IllegalArgumentException(
                    "G21.86 pinned account mismatch");
            return file;
        };
    }

    private static boolean negative(
        String username,Path file
    )throws IOException{
        FilePlayerRepository.PathResolver selected=
            pinned(username,file);
        return new MailboxDurableReviewFence(selected).present(username)||
            new MailboxStrictUncertainFence(selected).present(username)||
            new MailboxStrictWriteIntentFence(selected).present(username);
    }

    private static byte[] encode(Record r)throws IOException{
        if(!r.account.matches("[a-z0-9_-]{1,64}")||
           !RewardDeliveryMessage.normalizeMessageId(
               r.message).equals(r.message)||
           !hex64(r.key)||!hex64(r.prepared)||
           !hex64(r.hypothetical)||!hex64(r.terminal))
            throw new IOException(
                "G21.86 invalid disk commit record fields");
        String prefix=FORMAT+"\n"+AUTHORITY+"\n"+MARKER_STATE+"\n"+
            r.account+"\n"+r.message+"\n"+r.key+"\n"+
            r.prepared+"\n"+r.hypothetical+"\n"+r.terminal+"\n";
        byte[] bytes=(prefix+sha(prefix)+"\n").getBytes(
            StandardCharsets.US_ASCII);
        if(bytes.length>MAX_BYTES)
            throw new IOException("G21.86 disk commit record oversized");
        return bytes;
    }

    private static Record decode(byte[] bytes)throws IOException{
        String content=new String(bytes,StandardCharsets.US_ASCII);
        String[] lines=content.split("\n",-1);
        if(lines.length!=11||!lines[10].isEmpty()||
           !FORMAT.equals(lines[0])||!AUTHORITY.equals(lines[1])||
           !MARKER_STATE.equals(lines[2])||!hex64(lines[9]))
            throw new IOException("G21.86 disk commit record malformed");
        String prefix=content.substring(
            0,content.length()-lines[9].length()-1);
        if(!sha(prefix).equals(lines[9]))
            throw new IOException("G21.86 disk commit checksum invalid");
        Record record=new Record(
            lines[3],lines[4],lines[5],lines[6],lines[7],lines[8]);
        if(!MessageDigest.isEqual(bytes,encode(record)))
            throw new IOException("G21.86 noncanonical disk commit");
        return record;
    }

    private static boolean hex64(String value){
        return value!=null&&value.matches("[0-9a-f]{64}");
    }

    private static String sha(String value)throws IOException{
        final byte[] digest;
        try{
            digest=MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII));
        }catch(NoSuchAlgorithmException missing){
            throw new IOException("G21.86 SHA-256 unavailable",missing);
        }
        char[] symbols="0123456789abcdef".toCharArray();
        char[] hex=new char[digest.length*2];
        for(int i=0;i<digest.length;i++){
            hex[2*i]=symbols[(digest[i]&255)>>>4];
            hex[2*i+1]=symbols[digest[i]&15];
        }
        return new String(hex);
    }
}
