package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * G21.32: independently persisted, fail-closed NEGATIVE Mailbox fence.
 *
 * A present fence means "manual review required", even if the account
 * snapshot is absent, corrupted, or has lost its PREPARED journal. This
 * deliberately cannot acknowledge a claim, release a fence or authorize
 * any inventory/mailbox mutation. Not a write-ahead COMMIT decision.
 *
 * G21.34: publication is a no-clobber hard-link creation on a local
 * filesystem supporting atomic exclusive link-name creation. Competing
 * independent marker writers cannot replace the winning marker through
 * this API. Unsupported hard links fail closed, not via an unsafe rename
 * fallback. Untrusted external deletions/edits and hardware power-loss
 * guarantees remain outside this negative-only API.
 */
final class MailboxDurableReviewFence {
    static final String STATE="REVIEW_REQUIRED_NO_GRANT";
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2132_DURABLE_NEGATIVE_REVIEW_FENCE";
    private static final String FORMAT="SPK-G2132-MAILBOX-REVIEW-V1";
    private static final int MAX_BYTES=2048;
    private static final String SUFFIX=".g2132-mailbox-review";

    enum Phase {
        BEFORE_TEMP_CREATE,
        AFTER_TEMP_CREATE,
        AFTER_SERIALIZE,
        BEFORE_FILE_FORCE,
        BEFORE_ATOMIC_REPLACE,
        // G21.38 deterministic race seam inside the exclusive lock.
        INSIDE_EXCLUSIVE_PUBLICATION_BEFORE_LINK,
        BEFORE_DIRECTORY_FORCE,
        AFTER_DIRECTORY_FORCE
    }

    interface FaultPoint {
        void check(Phase phase)throws IOException;
    }

    static final class UnconfirmedFenceException extends IOException {
        UnconfirmedFenceException(String message,Throwable cause){
            super(message,cause);
        }
    }

    static final class Record {
        final String account;
        final String messageId;
        final String intentKey;
        final String preparedSha256;
        final String hypotheticalSha256;
        final String state=STATE;
        final String authority=AUTHORITY;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean releaseAuthorized=false;

        private Record(
            String account,String messageId,String intentKey,
            String preparedSha256,String hypotheticalSha256
        ){
            this.account=account;
            this.messageId=messageId;
            this.intentKey=intentKey;
            this.preparedSha256=preparedSha256;
            this.hypotheticalSha256=hypotheticalSha256;
        }

        boolean matches(MailboxSettlementPostimagePlanner.Proposal p){
            return p!=null&&
                account.equals(p.account)&&
                messageId.equals(p.messageId)&&
                intentKey.equals(p.idempotencyKey)&&
                preparedSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(p.preparedPreimage)
                )&&
                hypotheticalSha256.equals(
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(p.hypotheticalPostimage)
                );
        }
    }

    static final class Receipt {
        final Path fenceFile;
        final Record record;
        final String authority=AUTHORITY;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;

        private Receipt(Path path,Record record){
            this.fenceFile=path;
            this.record=record;
        }
    }

    private final FilePlayerRepository.PathResolver resolver;
    private final FaultPoint faults;

    MailboxDurableReviewFence(
        FilePlayerRepository.PathResolver resolver
    ){
        this(resolver,phase->{});
    }

    MailboxDurableReviewFence(
        FilePlayerRepository.PathResolver resolver,FaultPoint faults
    ){
        this.resolver=Objects.requireNonNull(resolver,"resolver");
        this.faults=Objects.requireNonNull(faults,"faults");
    }

    /**
     * Strict opt-in write-once record. In-memory G21.27 token ownership,
     * World-persistence FIFO ordering and commit authority are NOT implied.
     * Do not wire directly to live native C2S185 widget 32181.
     */
    synchronized Receipt arm(
        MailboxSettlementPostimagePlanner.Proposal proposal
    )throws IOException{
        return armInternal(proposal,false);
    }

    /**
     * G21.41 strictly opt-in: verify the CURRENT file-backed account is
     * exactly this PREPARED preimage while holding the same exclusive
     * publication lock before linking the durable negative marker.
     * Does not authorize claim/grant/replay or change arm() semantics.
     */
    synchronized Receipt armVerifiedAgainstCurrentFile(
        MailboxSettlementPostimagePlanner.Proposal proposal
    )throws IOException{
        return armInternal(proposal,true);
    }

    private Receipt armInternal(
        MailboxSettlementPostimagePlanner.Proposal proposal,
        boolean requireExactDiskPrepared
    )throws IOException{
        Objects.requireNonNull(proposal,"proposal");
        if(proposal.preparedPreimage.version()!=
                PlayerSnapshot.CURRENT_VERSION||
           proposal.hypotheticalPostimage.version()!=
                PlayerSnapshot.CURRENT_VERSION||
           !proposal.account.equals(
                proposal.preparedPreimage.username())||
           !proposal.account.equals(
                proposal.hypotheticalPostimage.username())||
           !proposal.idempotencyKey.equals(
                proposal.preparedPreimage.value(
                    "extension."+MailboxPreparedClaimJournal.NAMESPACE+
                    ".key"))||
           !proposal.messageId.equals(
                proposal.preparedPreimage.value(
                    "extension."+MailboxPreparedClaimJournal.NAMESPACE+
                    ".message"))||
           MailboxPreparedRestartAdmission.inspect(
                proposal.preparedPreimage
           ).state!=
                MailboxPreparedRestartAdmission.State
                    .VALID_PREPARED_UNCLAIMED||
           proposal.classify(proposal.hypotheticalPostimage)!=
                MailboxSettlementPostimagePlanner.RecoveryClass
                    .EXACT_HYPOTHETICAL_POSTIMAGE||
           proposal.preparedPreimage.values().equals(
                proposal.hypotheticalPostimage.values()))
            throw new IOException("G21.32 invalid prepared review proposal");

        Record record=new Record(
            proposal.account,proposal.messageId,
            proposal.idempotencyKey,
            StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(proposal.preparedPreimage),
            StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(proposal.hypotheticalPostimage)
        );
        Path file=fencePath(proposal.account);
        Path parent=Objects.requireNonNull(
            file.getParent(),"fence parent"
        );
        // Refuse existing records, whether valid, corrupted or a symlink.
        // A previous uncertain post-rename write cannot be retried blindly.
        if(present(proposal.account))
            throw new IOException(
                "G21.32 review fence already exists; manual review required"
            );

        byte[] content=encode(record);
        faults.check(Phase.BEFORE_TEMP_CREATE);
        Files.createDirectories(parent);
        Path temp=Files.createTempFile(
            parent,file.getFileName().toString()+".g2132-",".tmp"
        );
        final boolean[] published={false};
        try{
            faults.check(Phase.AFTER_TEMP_CREATE);
            try(FileChannel channel=FileChannel.open(
                    temp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                ByteBuffer data=ByteBuffer.wrap(content);
                while(data.hasRemaining())
                    channel.write(data);
                faults.check(Phase.AFTER_SERIALIZE);
                faults.check(Phase.BEFORE_FILE_FORCE);
                channel.force(true);
            }
            faults.check(Phase.BEFORE_ATOMIC_REPLACE);
            // G21.38: exclusive publication shares the SAME account
            // lock as the final check+replacement in the guarded World
            // repository save. The marker remains a no-clobber hard link.
            // Neither snapshot serialization nor proposal planning is
            // performed under this cross-process coordination lock.
            Path accountFile=Objects.requireNonNull(
                resolver.resolve(proposal.account),"account path"
            ).toAbsolutePath().normalize();
            return MailboxAccountPublicationCoordinator
                .withExclusivePublication(accountFile,()->{
                    // G21.34: the final hard-link name is created only
                    // when absent. No ATOMIC_MOVE/copy fallback exists.
                    faults.check(
                        Phase.INSIDE_EXCLUSIVE_PUBLICATION_BEFORE_LINK
                    );
                    if(requireExactDiskPrepared)
                        requireMatchingPersistedPrepared(
                            proposal,record.preparedSha256
                        );
                    Files.createLink(file,temp);
                    published[0]=true;
                    try{
                        Files.delete(temp);
                        faults.check(Phase.BEFORE_DIRECTORY_FORCE);
                        try(FileChannel directory=FileChannel.open(
                                parent,StandardOpenOption.READ)){
                            directory.force(true);
                        }
                        faults.check(Phase.AFTER_DIRECTORY_FORCE);
                    }catch(IOException|RuntimeException uncertain){
                        throw new UnconfirmedFenceException(
                            "G21.38 negative marker publication may "+
                            "already be visible; manual review required",
                            uncertain
                        );
                    }
                    return new Receipt(file,record);
                });
        }finally{
            if(!published[0])
                Files.deleteIfExists(temp);
        }
    }

    /**
     * Must be called under MailboxAccountPublicationCoordinator for the
     * SAME resolver path. A cooperating World save cannot replace the
     * account between this exact-state observation and marker link.
     */
    private void requireMatchingPersistedPrepared(
        MailboxSettlementPostimagePlanner.Proposal proposal,
        String expectedSha
    )throws IOException{
        final java.util.Optional<PlayerSnapshot> disk=
            new FilePlayerRepository(resolver).load(proposal.account);
        if(!disk.isPresent())
            throw new IOException(
                "G21.41 VERIFIED_REVIEW_PREIMAGE_MISSING account="+
                proposal.account
            );
        final PlayerSnapshot snapshot=disk.get();
        final PlayerSnapshot normalized;
        try{
            normalized=PlayerSnapshotCodec.validateAndNormalize(
                snapshot
            );
        }catch(RuntimeException malformed){
            throw new IOException(
                "G21.41 VERIFIED_REVIEW_PREIMAGE_INVALID account="+
                proposal.account,malformed
            );
        }
        if(!snapshot.username().equals(proposal.account)||
           !normalized.username().equals(proposal.account)||
           snapshot.version()!=PlayerSnapshot.CURRENT_VERSION||
           !snapshot.values().equals(normalized.values())||
           MailboxPreparedRestartAdmission.inspect(snapshot).state!=
                MailboxPreparedRestartAdmission.State
                    .VALID_PREPARED_UNCLAIMED||
           !StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(normalized)
                .equals(expectedSha))
            throw new IOException(
                "G21.41 VERIFIED_REVIEW_PREIMAGE_DIVERGENT account="+
                proposal.account+" action=REFUSE_NEGATIVE_RECEIPT"
            );
    }

    /** Presence of ANY marker blocks login, including malformed/symlink. */
    boolean present(String account)throws IOException{
        Path path=fencePath(account);
        // G21.47: either independently persisted NEGATIVE marker must
        // veto real file-backed World login and guarded account saves.
        // The strict-uncertain format is intentionally NOT interpreted
        // as a G21.32 hypothetical-CLAIMED proposal receipt.
        Path accountFile=Objects.requireNonNull(
            resolver.resolve(account),"account path"
        ).toAbsolutePath().normalize();
        if(MailboxStrictUnconfirmedReviewFence.present(accountFile))
            return true;
        try{
            Files.readAttributes(
                path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
            return true;
        }catch(NoSuchFileException missing){
            return false;
        }
    }

    /** Optional diagnostic parser. Malformed content never clears a veto. */
    Record inspect(String account)throws IOException{
        Path file=fencePath(account);
        byte[] content=Files.readAllBytes(file);
        if(content.length>MAX_BYTES||content.length<80)
            throw new IOException("G21.32 review marker size invalid");
        String raw=new String(content,StandardCharsets.US_ASCII);
        if(!Arrays.equals(raw.getBytes(StandardCharsets.US_ASCII),content))
            throw new IOException("G21.32 non-ASCII review marker");
        String[] lines=raw.split("\n",-1);
        if(lines.length!=9||!lines[8].isEmpty()||
           !FORMAT.equals(lines[0])||!STATE.equals(lines[1]))
            throw new IOException("G21.32 review marker format invalid");
        Record parsed=new Record(
            lines[2],lines[3],lines[4],lines[5],lines[6]
        );
        if(!parsed.account.equals(account)||
           !parsed.account.matches("[a-z0-9_-]{1,64}")||
           !parsed.messageId.matches("[a-z0-9._:-]{1,128}")||
           !isSha(parsed.intentKey)||
           !isSha(parsed.preparedSha256)||
           !isSha(parsed.hypotheticalSha256)||
           parsed.preparedSha256.equals(parsed.hypotheticalSha256))
            throw new IOException("G21.32 review marker identity invalid");
        byte[] prefix=encodePayload(parsed);
        if(!lines[7].equals(hex(sha256(prefix))))
            throw new IOException("G21.32 review marker checksum mismatch");
        return parsed;
    }

    /**
     * G21.48: reuse the exact same validated concrete account locator
     * for the separate G21.47 read-only forensic marker inspector.
     * This does not alter the existing fence file name or authority.
     */
    Path accountFileForStrictReview(String account){
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.32 noncanonical review fence account"
            );
        return Objects.requireNonNull(
            resolver.resolve(account),"account path"
        ).toAbsolutePath().normalize();
    }

    Path fencePath(String account){
        Path accountFile=accountFileForStrictReview(account);
        return accountFile.resolveSibling(
            accountFile.getFileName().toString()+SUFFIX
        );
    }

    private static byte[] encode(Record r)throws IOException{
        byte[] payload=encodePayload(r);
        String text=new String(payload,StandardCharsets.US_ASCII)+
            hex(sha256(payload))+"\n";
        byte[] data=text.getBytes(StandardCharsets.US_ASCII);
        if(data.length>MAX_BYTES)
            throw new IOException("G21.32 review fence oversized");
        return data;
    }

    private static byte[] encodePayload(Record r){
        String lines=FORMAT+"\n"+STATE+"\n"+
            r.account+"\n"+r.messageId+"\n"+r.intentKey+"\n"+
            r.preparedSha256+"\n"+r.hypotheticalSha256+"\n";
        return lines.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean isSha(String value){
        return value!=null&&value.matches("[0-9a-f]{64}");
    }

    private static byte[] sha256(byte[] data){
        try{
            return MessageDigest.getInstance("SHA-256").digest(data);
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException("SHA-256 unavailable",impossible);
        }
    }

    private static String hex(byte[] data){
        char[] out=new char[data.length*2];
        char[] digits="0123456789abcdef".toCharArray();
        for(int i=0;i<data.length;i++){
            int b=data[i]&0xff;
            out[i*2]=digits[b>>>4];
            out[i*2+1]=digits[b&15];
        }
        return new String(out);
    }
}
