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
 * G21.63: opt-in, write-once, restart-readable observation of an
 * already-visible hypothetical Mailbox inventory+CLAIMED account file.
 *
 * This evidence is NEVER a durable multi-file transaction COMMIT, an
 * authenticated record, a live grant, an automatic recovery instruction,
 * or permission to hydrate a quarantined World session. No native
 * C2S185/32181 integration. Non-granting forensic primitive only.
 */
final class MailboxDurableTerminalObservation {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2163_TERMINAL_OBSERVATION_NO_GRANT";
    static final String STATE="OBSERVED_POSTIMAGE_NO_GRANT";
    static final String SUFFIX=".g2163-mailbox-terminal-observation";
    private static final String FORMAT="SPK-G2163-TERMINAL-OBSERVATION-V1";
    private static final int MAX_BYTES=1024;

    enum Phase { BEFORE_LINK, AFTER_LINK, BEFORE_DIRECTORY_FORCE,
        AFTER_DIRECTORY_FORCE }
    interface FaultPoint { void check(Phase phase)throws IOException; }
    static final class UnconfirmedObservationException
        extends IOException {
        UnconfirmedObservationException(String message,Throwable cause){
            super(message,cause);
        }
    }
    enum Status {
        ABSENT,
        OBSERVED_HYPOTHETICAL_POSTIMAGE,
        ACCOUNT_DIVERGED,
        INVALID_RECORD
    }
    static final class Observation {
        final Status status;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        Observation(Status state){this.status=state;}
    }
    static final class Record {
        final String account,messageId,intentKey,preparedSha,hypotheticalSha;
        Record(String account,String messageId,String intentKey,
               String preparedSha,String hypotheticalSha){
            this.account=account;
            this.messageId=messageId;
            this.intentKey=intentKey;
            this.preparedSha=preparedSha;
            this.hypotheticalSha=hypotheticalSha;
        }
    }

    private final FilePlayerRepository.PathResolver resolver;
    private final FaultPoint faults;
    MailboxDurableTerminalObservation(
        FilePlayerRepository.PathResolver paths
    ){this(paths,phase->{});}
    MailboxDurableTerminalObservation(
        FilePlayerRepository.PathResolver paths,FaultPoint fault
    ){
        resolver=Objects.requireNonNull(paths,"paths");
        faults=Objects.requireNonNull(fault,"fault");
    }

    Path recordPath(String account){
        return sidecar(account,accountFile(account));
    }
    private Path accountFile(String account){
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.63 invalid canonical account");
        Path file=Objects.requireNonNull(
            resolver.resolve(account),"account path");
        return file.toAbsolutePath().normalize();
    }
    private static Path sidecar(String account,Path file){
        if(file.getParent()==null)
            throw new IllegalArgumentException(
                "G21.63 account file has no parent");
        return file.resolveSibling(
            file.getFileName().toString()+SUFFIX);
    }
    private static Record checkedProposal(
        MailboxSettlementPostimagePlanner.Proposal p
    )throws IOException{
        if(p==null||p.account==null||
           !p.account.matches("[a-z0-9_-]{1,64}")||
           p.preparedPreimage.version()!=
               PlayerSnapshot.CURRENT_VERSION||
           p.hypotheticalPostimage.version()!=
               PlayerSnapshot.CURRENT_VERSION||
           !p.account.equals(p.preparedPreimage.username())||
           !p.account.equals(p.hypotheticalPostimage.username())||
           MailboxPreparedRestartAdmission.inspect(
               p.preparedPreimage).state!=
               MailboxPreparedRestartAdmission.State
                   .VALID_PREPARED_UNCLAIMED||
           p.classify(p.hypotheticalPostimage)!=
               MailboxSettlementPostimagePlanner.RecoveryClass
                   .EXACT_HYPOTHETICAL_POSTIMAGE)
            throw new IOException("G21.63 untrusted postimage proposal");
        return new Record(p.account,p.messageId,p.idempotencyKey,
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                p.preparedPreimage),
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                p.hypotheticalPostimage));
    }

    /**
     * Explicit opt-in forensic write; a successful record still cannot
     * be used as an inventory/CLAIMED COMMIT, even on restart.
     */
    void recordObservedHypothetical(
        MailboxSettlementPostimagePlanner.Proposal proposal
    )throws IOException{
        Record record=checkedProposal(proposal);
        final Path account=accountFile(record.account);
        final Path marker=sidecar(record.account,account);
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            account,()->{
                if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException(
                        "G21.63 terminal observation already exists");
                if(!matchesDisk(account,record.account,
                        record.hypotheticalSha))
                    throw new IOException(
                        "G21.63 hypothetical postimage not on disk");
                byte[] bytes=encode(record);
                Path parent=marker.getParent();
                Path temp=Files.createTempFile(parent,
                    marker.getFileName().toString()+".write-",".tmp");
                boolean linked=false;
                try{
                    try(FileChannel channel=FileChannel.open(temp,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING)){
                        ByteBuffer buffer=ByteBuffer.wrap(bytes);
                        while(buffer.hasRemaining())
                            channel.write(buffer);
                        channel.force(true);
                    }
                    faults.check(Phase.BEFORE_LINK);
                    // Re-read after staging, while holding the account's
                    // cooperating publication lock. Uncooperative external
                    // writers and power-loss remain outside this proof.
                    if(!matchesDisk(account,record.account,
                            record.hypotheticalSha))
                        throw new IOException(
                            "G21.63 postimage changed before record link");
                    Files.createLink(marker,temp);
                    linked=true;
                    try{
                        faults.check(Phase.AFTER_LINK);
                        faults.check(Phase.BEFORE_DIRECTORY_FORCE);
                        try(FileChannel directory=FileChannel.open(
                                parent,StandardOpenOption.READ)){
                            directory.force(true);
                        }
                        faults.check(Phase.AFTER_DIRECTORY_FORCE);
                    }catch(IOException|RuntimeException uncertain){
                        throw new UnconfirmedObservationException(
                            "G21.63 terminal observation may exist; "+
                            "manual reconciliation only",uncertain);
                    }
                    return null;
                }finally{
                    Files.deleteIfExists(temp);
                    // Never delete a published marker after any failure.
                }
            });
    }

    /** Read-only, bound to the selected account path; always NO_GRANT. */
    Observation inspect(String username)throws IOException{
        final String account=username;
        final Path file=accountFile(account);
        final Path marker=sidecar(account,file);
        return MailboxAccountPublicationCoordinator.withExclusivePublication(
            file,()->{
                if(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS))
                    return new Observation(Status.ABSENT);
                final Record record;
                try{
                    byte[] bytes=MailboxNegativeMarkerBoundedRead.read(
                        marker,1,MAX_BYTES);
                    record=decode(bytes);
                    if(!record.account.equals(account))
                        return new Observation(Status.INVALID_RECORD);
                }catch(IOException|IllegalArgumentException invalid){
                    return new Observation(Status.INVALID_RECORD);
                }
                return new Observation(matchesDisk(file,account,
                        record.hypotheticalSha)
                    ?Status.OBSERVED_HYPOTHETICAL_POSTIMAGE
                    :Status.ACCOUNT_DIVERGED);
            });
    }

    private static boolean matchesDisk(
        Path file,String account,String expectedSha
    )throws IOException{
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            return false;
        Optional<PlayerSnapshot> snapshot=
            new FilePlayerRepository(name->file).load(account);
        return snapshot.isPresent()&&expectedSha.equals(
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                snapshot.get()));
    }

    private static byte[] encode(Record r)throws IOException{
        if(!r.account.matches("[a-z0-9_-]{1,64}")||
           !RewardDeliveryMessage.normalizeMessageId(
               r.messageId).equals(r.messageId)||
           !hex64(r.intentKey)||!hex64(r.preparedSha)||
           !hex64(r.hypotheticalSha))
            throw new IOException("G21.63 invalid observation fields");
        String payload=FORMAT+"\n"+AUTHORITY+"\n"+STATE+"\n"+
            r.account+"\n"+r.messageId+"\n"+r.intentKey+"\n"+
            r.preparedSha+"\n"+r.hypotheticalSha+"\n";
        byte[] bytes=(payload+sha256(payload)+"\n").getBytes(
            StandardCharsets.US_ASCII);
        if(bytes.length>MAX_BYTES)
            throw new IOException("G21.63 record too long");
        return bytes;
    }
    private static Record decode(byte[] bytes)throws IOException{
        String record=new String(bytes,StandardCharsets.US_ASCII);
        String[] lines=record.split("\n",-1);
        if(lines.length!=10||!lines[9].isEmpty()||
           !FORMAT.equals(lines[0])||
           !AUTHORITY.equals(lines[1])||
           !STATE.equals(lines[2])||!hex64(lines[8]))
            throw new IOException("G21.63 corrupt record header");
        String payload=record.substring(0,
            record.length()-lines[8].length()-1);
        if(!sha256(payload).equals(lines[8]))
            throw new IOException("G21.63 record checksum mismatch");
        Record parsed=new Record(lines[3],lines[4],lines[5],
            lines[6],lines[7]);
        if(!MessageDigest.isEqual(bytes,encode(parsed)))
            throw new IOException("G21.63 noncanonical observation");
        return parsed;
    }
    private static boolean hex64(String text){
        return text!=null&&text.matches("[0-9a-f]{64}");
    }
    private static String sha256(String payload)throws IOException{
        final MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException missing){
            throw new IOException("G21.63 SHA-256 unavailable",missing);
        }
        byte[] binary=digest.digest(
            payload.getBytes(StandardCharsets.US_ASCII));
        final char[] alphabet="0123456789abcdef".toCharArray();
        char[] hex=new char[binary.length*2];
        for(int i=0;i<binary.length;i++){
            hex[2*i]=alphabet[(binary[i]&255)>>>4];
            hex[2*i+1]=alphabet[binary[i]&15];
        }
        return new String(hex);
    }
}
