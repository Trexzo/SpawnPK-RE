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
 * G21.83: explicit opt-in, write-once durable PREPARED idempotency
 * INTENT, published before any contemplated terminal account change.
 *
 * Not a positive COMMIT; not an account-session admission fence and not
 * a replacement for G21.48's negative strict write-ahead marker.
 * Nothing here grants/replays a reward, applies to a live WorldPlayer,
 * releases a reservation, or acknowledges a client.
 */
final class MailboxDurableIdempotencyIntentJournal {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2183_PREPARED_INTENT_NO_GRANT";
    static final String SUFFIX=".g2183-mailbox-prepared-intent";
    private static final String FORMAT="SPK-G2183-INTENT-V1";
    private static final String STATE="PREPARED_INTENT_NO_GRANT";
    private static final int MAX_BYTES=1024;

    enum Phase {
        BEFORE_TEMP_CREATE, BEFORE_LINK, AFTER_LINK,
        BEFORE_DIRECTORY_FORCE, AFTER_DIRECTORY_FORCE
    }
    interface FaultPoint { void check(Phase phase)throws IOException; }
    static final class UnconfirmedJournalException extends IOException {
        UnconfirmedJournalException(Throwable cause){
            super("G21.83 JOURNAL_PUBLICATION_UNCONFIRMED_NO_GRANT",cause);
        }
    }

    enum Status {
        ABSENT, PREPARED_MATCH_NO_REPLAY, TERMINAL_MATCH_NO_COMMIT,
        NEGATIVE_MARKER_MANUAL_HOLD, ACCOUNT_DIVERGED_QUARANTINE,
        MISSING_ACCOUNT_QUARANTINE, INVALID_RECORD_QUARANTINE
    }

    static final class Observation {
        final Status status;
        final String account;
        final String messageId;
        final String intentKey;
        final String preparedSha256;
        final String terminalSha256;
        final boolean recordValidated;
        final boolean durabilityConfirmed=false;
        final boolean transactionCommitted=false;
        final boolean liveApplied=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Observation(Status status,String account,Record record){
            this.status=status;
            this.account=account;
            recordValidated=record!=null;
            messageId=record==null?null:record.message;
            intentKey=record==null?null:record.key;
            preparedSha256=record==null?null:record.prepared;
            terminalSha256=record==null?null:record.terminal;
        }
    }

    private static final class Record {
        final String account,message,key,prepared,terminal;
        Record(String account,String message,String key,
               String prepared,String terminal){
            this.account=account;
            this.message=message;
            this.key=key;
            this.prepared=prepared;
            this.terminal=terminal;
        }
    }

    private final FilePlayerRepository.PathResolver resolver;
    private final FaultPoint fault;

    MailboxDurableIdempotencyIntentJournal(
        FilePlayerRepository.PathResolver resolver
    ){
        this(resolver,phase->{});
    }

    MailboxDurableIdempotencyIntentJournal(
        FilePlayerRepository.PathResolver resolver,FaultPoint fault
    ){
        this.resolver=Objects.requireNonNull(resolver,"resolver");
        this.fault=Objects.requireNonNull(fault,"fault");
    }

    Path journalPath(String username){
        Path file=accountFile(username);
        return file.resolveSibling(file.getFileName().toString()+SUFFIX);
    }

    private Path accountFile(String username){
        if(username==null||!username.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.83 invalid canonical account");
        return Objects.requireNonNull(
            resolver.resolve(username),"G21.83 account path"
        ).toAbsolutePath().normalize();
    }

    private static Record checked(
        MailboxSettlementPostimagePlanner.Proposal proposal
    )throws IOException{
        if(proposal==null||proposal.account==null||
           !proposal.account.matches("[a-z0-9_-]{1,64}")||
           MailboxPreparedRestartAdmission.inspect(
               proposal.preparedPreimage).state!=
               MailboxPreparedRestartAdmission.State
                   .VALID_PREPARED_UNCLAIMED)
            throw new IOException("G21.83 untrusted PREPARED intent");
        final PlayerSnapshot terminal;
        try{
            terminal=MailboxAtomicTerminalSnapshot.compose(proposal);
        }catch(RuntimeException rejected){
            throw new IOException("G21.83 invalid terminal proposal",rejected);
        }
        return new Record(
            proposal.account,proposal.messageId,proposal.idempotencyKey,
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                proposal.preparedPreimage),
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                terminal)
        );
    }

    /**
     * Opt-in intent publication, not wired to widget32181. A caller must
     * supply an actual PREPARED file. The account lock excludes cooperating
     * publishers during the final check+link, not arbitrary raw writers.
     * Never automatically replace or unlink an existing journal.
     */
    void publishPreparedIntent(
        MailboxSettlementPostimagePlanner.Proposal proposal
    )throws IOException{
        final Record wanted=checked(proposal);
        final Path account=accountFile(wanted.account);
        final Path journal=journalPath(wanted.account);
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            account,()->{
                if(Files.exists(journal,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException(
                        "G21.83 JOURNAL_ALREADY_EXISTS_NO_GRANT");
                if(negative(wanted.account,account))
                    throw new IOException(
                        "G21.83 NEGATIVE_ACCOUNT_MARKER_NO_GRANT");
                if(!matchesPrepared(account,wanted))
                    throw new IOException(
                        "G21.83 DISK_NOT_EXACT_PREPARED_NO_GRANT");
                byte[] bytes=encode(wanted);
                Path parent=journal.getParent();
                fault.check(Phase.BEFORE_TEMP_CREATE);
                Path temp=Files.createTempFile(
                    parent,journal.getFileName().toString()+".write-",
                    ".tmp");
                try{
                    try(FileChannel channel=FileChannel.open(temp,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)){
                        ByteBuffer b=ByteBuffer.wrap(bytes);
                        while(b.hasRemaining())channel.write(b);
                        channel.force(true);
                    }
                    fault.check(Phase.BEFORE_LINK);
                    if(negative(wanted.account,account)||
                       !matchesPrepared(account,wanted))
                        throw new IOException(
                            "G21.83 PRELINK_PREPARED_CHANGED_NO_GRANT");
                    Files.createLink(journal,temp);
                    try{
                        fault.check(Phase.AFTER_LINK);
                        fault.check(Phase.BEFORE_DIRECTORY_FORCE);
                        try(FileChannel directory=FileChannel.open(
                                parent,StandardOpenOption.READ)){
                            directory.force(true);
                        }
                        fault.check(Phase.AFTER_DIRECTORY_FORCE);
                    }catch(IOException|RuntimeException uncertain){
                        throw new UnconfirmedJournalException(uncertain);
                    }
                    return null;
                }finally{
                    // Never remove journal after publication, even
                    // when a force/fault reports an uncertain outcome.
                    Files.deleteIfExists(temp);
                }
            });
    }

    /**
     * Forensic-only read under the bounded, NOFOLLOW G21.73/77 lock.
     * A matching journal is NEVER a COMMIT or a restart credential.
     * The journal is not currently integrated into normal World admission.
     */
    Observation inspect(String username)throws IOException{
        final Path account=accountFile(username);
        final Path journal=journalPath(username);
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(account,1500L,()->{
                if(!Files.exists(journal,LinkOption.NOFOLLOW_LINKS))
                    return new Observation(Status.ABSENT,username,null);
                if(negative(username,account))
                    return new Observation(
                        Status.NEGATIVE_MARKER_MANUAL_HOLD,username,null);
                final Record stored;
                try{
                    stored=decode(MailboxNegativeMarkerBoundedRead.read(
                        journal,1,MAX_BYTES));
                    if(!username.equals(stored.account))
                        return new Observation(
                            Status.INVALID_RECORD_QUARANTINE,username,null);
                }catch(IOException|IllegalArgumentException invalid){
                    return new Observation(
                        Status.INVALID_RECORD_QUARANTINE,username,null);
                }
                if(!Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS))
                    return new Observation(
                        Status.MISSING_ACCOUNT_QUARANTINE,username,stored);
                Optional<PlayerSnapshot> disk;
                try{
                    disk=new FilePlayerRepository(a->account).load(username);
                }catch(IOException|RuntimeException invalid){
                    return new Observation(
                        Status.ACCOUNT_DIVERGED_QUARANTINE,username,stored);
                }
                if(!disk.isPresent())
                    return new Observation(
                        Status.MISSING_ACCOUNT_QUARANTINE,username,stored);
                String digest=StrictDurablePlayerSnapshotWriter
                    .canonicalSnapshotSha256(disk.get());
                if(stored.prepared.equals(digest)&&
                   MailboxPreparedRestartAdmission.inspect(disk.get())
                       .state==MailboxPreparedRestartAdmission.State
                           .VALID_PREPARED_UNCLAIMED)
                    return new Observation(
                        Status.PREPARED_MATCH_NO_REPLAY,username,stored);
                if(stored.terminal.equals(digest)&&
                   MailboxAtomicTerminalSnapshot.inspect(disk.get())
                       .state==MailboxAtomicTerminalSnapshot.State
                           .COHERENT_TERMINAL_NO_GRANT)
                    return new Observation(
                        Status.TERMINAL_MATCH_NO_COMMIT,username,stored);
                return new Observation(
                    Status.ACCOUNT_DIVERGED_QUARANTINE,username,stored);
            });
    }

    private static boolean negative(String name,Path file)
        throws IOException{
        FilePlayerRepository.PathResolver pinned=account->{
            if(!name.equals(account))
                throw new IllegalArgumentException(
                    "G21.83 foreign marker account");
            return file;
        };
        return new MailboxDurableReviewFence(pinned).present(name)||
            new MailboxStrictUncertainFence(pinned).present(name)||
            new MailboxStrictWriteIntentFence(pinned).present(name);
    }

    private static boolean matchesPrepared(
        Path file,Record r
    )throws IOException{
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            return false;
        Optional<PlayerSnapshot> disk=
            new FilePlayerRepository(a->file).load(r.account);
        return disk.isPresent()&&
            MailboxPreparedRestartAdmission.inspect(disk.get()).state==
                MailboxPreparedRestartAdmission.State
                    .VALID_PREPARED_UNCLAIMED&&
            r.prepared.equals(StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(disk.get()));
    }

    private static byte[] encode(Record r)throws IOException{
        if(!r.account.matches("[a-z0-9_-]{1,64}")||
           !RewardDeliveryMessage.normalizeMessageId(r.message)
               .equals(r.message)||
           !hex64(r.key)||!hex64(r.prepared)||!hex64(r.terminal))
            throw new IOException("G21.83 noncanonical intent identity");
        String body=FORMAT+"\n"+AUTHORITY+"\n"+STATE+"\n"+
            r.account+"\n"+r.message+"\n"+r.key+"\n"+
            r.prepared+"\n"+r.terminal+"\n";
        byte[] bytes=(body+sha(body)+"\n").getBytes(
            StandardCharsets.US_ASCII);
        if(bytes.length>MAX_BYTES)
            throw new IOException("G21.83 journal exceeds bound");
        return bytes;
    }

    private static Record decode(byte[] bytes)throws IOException{
        String content=new String(bytes,StandardCharsets.US_ASCII);
        String[] rows=content.split("\n",-1);
        if(rows.length!=10||!rows[9].isEmpty()||
           !FORMAT.equals(rows[0])||!AUTHORITY.equals(rows[1])||
           !STATE.equals(rows[2])||!hex64(rows[8]))
            throw new IOException("G21.83 invalid record header");
        String body=content.substring(
            0,content.length()-rows[8].length()-1);
        if(!sha(body).equals(rows[8]))
            throw new IOException("G21.83 journal checksum invalid");
        Record r=new Record(rows[3],rows[4],rows[5],
            rows[6],rows[7]);
        if(!MessageDigest.isEqual(bytes,encode(r)))
            throw new IOException("G21.83 noncanonical record");
        return r;
    }

    private static boolean hex64(String s){
        return s!=null&&s.matches("[0-9a-f]{64}");
    }

    private static String sha(String text)throws IOException{
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.US_ASCII));
            char[] out=new char[bytes.length*2];
            char[] hex="0123456789abcdef".toCharArray();
            for(int i=0;i<bytes.length;i++){
                out[2*i]=hex[(bytes[i]&255)>>>4];
                out[2*i+1]=hex[bytes[i]&15];
            }
            return new String(out);
        }catch(NoSuchAlgorithmException impossible){
            throw new IOException("G21.83 SHA-256 missing",impossible);
        }
    }
}
