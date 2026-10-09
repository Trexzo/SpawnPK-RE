package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * G21.33 read-only triage of a G21.32 negative review fence using the
 * existing WorldPlayerPersistence FIFO forensic load. All outcomes are
 * NON-AUTHORIZING: no session admission, grant, replay, rollback, or
 * negative-marker clearing can be inferred from this observation.
 *
 * Account file and fence are separate files, so double-reading the fence
 * only detects some races; it does NOT make these files an atomic snapshot.
 */
final class MailboxFencedRestartForensics {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2133_FENCED_ACCOUNT_FORENSICS_NO_GRANT";

    enum State {
        NO_FENCE_NO_AUTHORITY,
        INVALID_OR_UNREADABLE_FENCE,
        FENCE_DISAPPEARED_OR_CHANGED,
        ACCOUNT_READ_FAILED,
        MISSING_ACCOUNT,
        INVALID_OR_DIVERGENT_ACCOUNT,
        EXACT_PREPARED_UNCLAIMED,
        EXACT_HYPOTHETICAL_CLAIMED,
        OTHER_ACCOUNT_POSTIMAGE,
        // G21.49: negative-only read of actual B permanent/intent files.
        STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY,
        STRICT_UNCERTAIN_DIGEST_MISMATCH_NO_AUTHORITY,
        STRICT_INTENT_DIGEST_MATCH_NO_AUTHORITY,
        STRICT_INTENT_DIGEST_MISMATCH_NO_AUTHORITY,
        STRICT_MARKER_INVALID,
        STRICT_MARKER_CHANGED,
        STRICT_MARKER_ACCOUNT_READ_FAILED,
        STRICT_MARKER_MISSING_ACCOUNT,
        STRICT_MARKER_INVALID_ACCOUNT,
        STRICT_LEGACY_UNVERIFIED_NO_AUTHORITY,
        STRICT_LEGACY_CHECKSUM_VALID_EXACT_NO_AUTHORITY,
        STRICT_LEGACY_CHECKSUM_VALID_DIVERGENT_NO_AUTHORITY,
        STRICT_LEGACY_INVALID_RECORD_NO_AUTHORITY,
        STRICT_LEGACY_CHANGED_NO_AUTHORITY,
        MULTIPLE_NEGATIVE_MARKERS_CONFLICT_NO_AUTHORITY,
        MULTIPLE_NEGATIVE_MARKERS_INVALID_NO_AUTHORITY,
        MULTIPLE_NEGATIVE_MARKERS_CHANGED_NO_AUTHORITY,
        SINGLE_NEGATIVE_MARKER_MEMBERSHIP_CHANGED_NO_AUTHORITY,
        SINGLE_NEGATIVE_MARKER_CONTENT_CHANGED_NO_AUTHORITY,
        NEGATIVE_MARKER_ACCOUNT_PATH_CHANGED_NO_AUTHORITY
    }

    static final class Report {
        final State state;
        final String account;
        final String observedSnapshotSha256;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean releaseFenceAuthorized=false;
        final boolean sessionAdmissionAuthorized=false;
        final boolean fileDurabilityConfirmed=false;
        final boolean automaticRecoveryAuthorized=false;
        final String authority=AUTHORITY;

        private Report(
            State state,String account,String snapshotSha256
        ){
            this.state=Objects.requireNonNull(state,"state");
            this.account=account;
            this.observedSnapshotSha256=snapshotSha256;
        }
    }

    static Report inspect(
        WorldPlayerPersistence persistence,
        MailboxDurableReviewFence fence,
        String account
    ){
        Objects.requireNonNull(persistence,"persistence");
        Objects.requireNonNull(fence,"fence");
        Objects.requireNonNull(account,"account");
        final MarkerSet before;
        try{
            before=inspectMarkerSet(fence,account);
        }catch(IOException|RuntimeException invalid){
            return result(State.MULTIPLE_NEGATIVE_MARKERS_INVALID_NO_AUTHORITY,account,null);
        }
        if(before.problem!=null)return result(before.problem,account,null);
        Report report=inspectSingleMarker(persistence,fence,account);
        final MarkerSet after;
        try{
            after=inspectMarkerSet(fence,account);
        }catch(IOException|RuntimeException changed){
            return result(State.MULTIPLE_NEGATIVE_MARKERS_CHANGED_NO_AUTHORITY,account,null);
        }
        if(!before.accountFile.equals(after.accountFile))
            return result(
                State.NEGATIVE_MARKER_ACCOUNT_PATH_CHANGED_NO_AUTHORITY,
                account,null
            );
        if(!Arrays.equals(before.present,after.present)){
            // G21.52: zero/one marker is NOT exempt from evidence
            // membership stability. A newly published fence may otherwise
            // be silently reported as absent by this read-only inspector.
            State state=(before.count>=2||after.count>=2)
                ?State.MULTIPLE_NEGATIVE_MARKERS_CHANGED_NO_AUTHORITY
                :State.SINGLE_NEGATIVE_MARKER_MEMBERSHIP_CHANGED_NO_AUTHORITY;
            return result(state,account,null);
        }
        if((before.count>=2||after.count>=2)&&!before.matches(after))
            return result(State.MULTIPLE_NEGATIVE_MARKERS_CHANGED_NO_AUTHORITY,account,null);
        if(after.problem!=null)return result(after.problem,account,null);
        if(before.count==1&&after.count==1&&!before.matches(after)&&
           !alreadyInvalidOrChanged(report.state))
            return result(
                State.SINGLE_NEGATIVE_MARKER_CONTENT_CHANGED_NO_AUTHORITY,
                account,null
            );
        return report;
    }

    /**
     * Preserve G21.33/G21.49's more specific single-marker diagnostic
     * when its own inner parser has already detected invalid/change.
     */
    private static boolean alreadyInvalidOrChanged(State state){
        switch(state){
            case INVALID_OR_UNREADABLE_FENCE:
            case FENCE_DISAPPEARED_OR_CHANGED:
            case STRICT_MARKER_INVALID:
            case STRICT_MARKER_CHANGED:
            case STRICT_LEGACY_INVALID_RECORD_NO_AUTHORITY:
            case STRICT_LEGACY_CHANGED_NO_AUTHORITY:
                return true;
            default:
                return false;
        }
    }

    private static Report inspectSingleMarker(
        WorldPlayerPersistence persistence,
        MailboxDurableReviewFence fence,
        String account
    ){
        Objects.requireNonNull(persistence,"persistence");
        Objects.requireNonNull(fence,"fence");
        Objects.requireNonNull(account,"account");

        // Distinct B negative markers must not be passed to the G21.32
        // parser and mislabeled as a malformed proposal.
        try{
            Path accountFile=fence.accountFileForStrictReview(account);
            Path permanent=sidecarPath(
                accountFile,".g2147-strict-uncertain"
            );
            Path intent=sidecarPath(
                accountFile,".g2148-strict-write-intent"
            );
            if(markerPresent(permanent))
                return inspectStrictNegative(
                    persistence,account,permanent,true
                );
            if(markerPresent(intent))
                return inspectStrictNegative(
                    persistence,account,intent,false
                );
            // Former independent A branch sidecar is deliberately a
            // fail-closed legacy observation until its parser/migration
            // is explicitly reconciled. Never silently admit or clear.
            Path legacy=sidecarPath(
                accountFile,".g2147-strict-postpublication-review"
            );
            if(markerPresent(legacy))
                return inspectLegacyStrictNegative(
                    persistence,account,legacy
                );
        }catch(IOException|RuntimeException badMarkerLocator){
            return result(State.STRICT_MARKER_INVALID,account,null);
        }

        final MailboxDurableReviewFence.Record marker;
        try{
            if(!fence.present(account))
                return result(State.NO_FENCE_NO_AUTHORITY,account,null);
            marker=fence.inspect(account);
        }catch(IOException|RuntimeException unreadable){
            return result(State.INVALID_OR_UNREADABLE_FENCE,account,null);
        }

        final Optional<PlayerSnapshot> snapshot;
        try{
            // G21.31 forensic FIFO explicitly avoids session hydration.
            snapshot=persistence.observeUntrustedMailboxAccount(account);
        }catch(IOException|RuntimeException readFailure){
            return result(State.ACCOUNT_READ_FAILED,account,null);
        }

        // Post-read check prevents a detected marker swap/disappearance
        // from being presented as stable evidence. This does NOT provide
        // transaction-wide atomicity across two independent files.
        try{
            if(!fence.present(account))
                return result(
                    State.FENCE_DISAPPEARED_OR_CHANGED,account,null
                );
            MailboxDurableReviewFence.Record rechecked=
                fence.inspect(account);
            if(!sameMarker(marker,rechecked))
                return result(
                    State.FENCE_DISAPPEARED_OR_CHANGED,account,null
                );
        }catch(IOException|RuntimeException mismatch){
            return result(State.FENCE_DISAPPEARED_OR_CHANGED,account,null);
        }

        if(!snapshot.isPresent())
            return result(State.MISSING_ACCOUNT,account,null);

        PlayerSnapshot stored=snapshot.get();
        if(!account.equals(stored.username())||
           stored.version()!=PlayerSnapshot.CURRENT_VERSION)
            return result(
                State.INVALID_OR_DIVERGENT_ACCOUNT,account,null
            );

        final PlayerSnapshot canonical;
        try{
            canonical=PlayerSnapshotCodec.validateAndNormalize(stored);
        }catch(RuntimeException damaged){
            return result(
                State.INVALID_OR_DIVERGENT_ACCOUNT,account,null
            );
        }
        if(!canonical.values().equals(stored.values()))
            return result(
                State.INVALID_OR_DIVERGENT_ACCOUNT,account,null
            );

        String hash=StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(canonical);
        if(hash.equals(marker.preparedSha256)){
            MailboxPreparedRestartAdmission.Decision prepared=
                MailboxPreparedRestartAdmission.inspect(canonical);
            if(prepared.state==
                    MailboxPreparedRestartAdmission.State
                        .VALID_PREPARED_UNCLAIMED&&
               matchesIntentIdentity(canonical,marker))
                return result(
                    State.EXACT_PREPARED_UNCLAIMED,account,hash
                );
            return result(State.OTHER_ACCOUNT_POSTIMAGE,account,hash);
        }

        if(hash.equals(marker.hypotheticalSha256)){
            if(exactClaimedPostimage(canonical,marker))
                return result(
                    State.EXACT_HYPOTHETICAL_CLAIMED,account,hash
                );
            return result(State.OTHER_ACCOUNT_POSTIMAGE,account,hash);
        }

        return result(State.OTHER_ACCOUNT_POSTIMAGE,account,hash);
    }


    /** Secondary files never authorize recovery. A mismatched or corrupt
     * companion marker must not be hidden by the first readable marker. */
    private static MarkerSet inspectMarkerSet(
        MailboxDurableReviewFence fence,String account
    )throws IOException{
        Path file=fence.accountFileForStrictReview(account);
        Path[] paths={
            sidecarPath(file,".g2147-strict-uncertain"),
            sidecarPath(file,".g2148-strict-write-intent"),
            sidecarPath(file,".g2147-strict-postpublication-review"),
            sidecarPath(file,".g2132-mailbox-review")
        };
        boolean[] present=new boolean[paths.length];
        byte[][] evidence=new byte[paths.length][];
        int count=0;
        for(int i=0;i<paths.length;i++){
            present[i]=markerPresent(paths[i]);
            if(present[i])count++;
        }
        if(count==1){
            // G21.53: the inner G21.33/G21.49 parser may have finished
            // before this final census. Pin bounded raw bytes on both
            // sides to detect a late same-path rewrite without trusting
            // the bytes or altering the existing parser classifications.
            for(int i=0;i<paths.length;i++){
                if(!present[i])continue;
                final int min=i==3||i==2?80:60;
                final int max=i==3?2048:(i==2?512:384);
                try{
                    evidence[i]=MailboxNegativeMarkerBoundedRead.read(
                        paths[i],min,max
                    );
                }catch(IOException|RuntimeException invalid){
                    // Let the original single-marker parser emit its
                    // historical invalid/read-failure classification.
                    // Null vs valid evidence still detects a late rewrite.
                    evidence[i]=null;
                }
            }
            return new MarkerSet(file,present,evidence,count,null);
        }
        if(count==0)return new MarkerSet(file,present,evidence,count,null);
        String strict=null;
        MailboxDurableReviewFence.Record proposal=null;
        try{
            for(int i=0;i<paths.length;i++){
                if(!present[i])continue;
                if(i==3){
                    proposal=fence.inspectExactMarkerPath(
                        account,paths[i]
                    );
                    evidence[i]=MailboxNegativeMarkerBoundedRead.read(
                        paths[i],80,2048
                    );
                }else{
                    StrictNegativeRecord record=i==2
                        ?readLegacyNegative(paths[i],account)
                        :readStrictNegative(paths[i],account,i==0);
                    evidence[i]=record.bytes;
                    if(strict==null)strict=record.snapshotSha;
                    else if(!strict.equals(record.snapshotSha))
                        return new MarkerSet(file,present,evidence,count,
                            State.MULTIPLE_NEGATIVE_MARKERS_CONFLICT_NO_AUTHORITY);
                }
            }
        }catch(IOException|RuntimeException unreadable){
            return new MarkerSet(file,present,evidence,count,
                State.MULTIPLE_NEGATIVE_MARKERS_INVALID_NO_AUTHORITY);
        }
        if(proposal!=null&&strict!=null&&
           !strict.equals(proposal.preparedSha256)&&
           !strict.equals(proposal.hypotheticalSha256))
            return new MarkerSet(file,present,evidence,count,
                State.MULTIPLE_NEGATIVE_MARKERS_CONFLICT_NO_AUTHORITY);
        return new MarkerSet(file,present,evidence,count,null);
    }

    private static final class MarkerSet {
        final Path accountFile;
        final boolean[] present;
        final byte[][] evidence;
        final int count;
        final State problem;
        MarkerSet(
            Path file,boolean[] found,byte[][] raw,int count,State problem
        ){
            this.accountFile=file;
            this.present=found;
            this.evidence=raw;
            this.count=count;
            this.problem=problem;
        }
        boolean matches(MarkerSet other){
            if(!accountFile.equals(other.accountFile)||
               count!=other.count||problem!=other.problem||
               !Arrays.equals(present,other.present))return false;
            for(int i=0;i<evidence.length;i++)
                if(!Arrays.equals(evidence[i],other.evidence[i]))
                    return false;
            return true;
        }
    }

    private static final class StrictNegativeRecord {
        final String account;
        final String snapshotSha;
        final byte[] bytes;
        StrictNegativeRecord(String account,String sha,byte[] bytes){
            this.account=account;
            this.snapshotSha=sha;
            this.bytes=bytes;
        }
    }

    private static Path sidecarPath(Path accountFile,String suffix){
        return accountFile.resolveSibling(
            accountFile.getFileName().toString()+suffix
        );
    }

    private static boolean markerPresent(Path path)throws IOException{
        try{
            Files.readAttributes(
                path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
            return true;
        }catch(NoSuchFileException missing){
            return false;
        }
    }

    /**
     * Read only the two exact currently shipped B formats. Their
     * SHA field is *not signed or checksummed*: it is diagnostic
     * evidence, NEVER a trusted approval/recovery receipt.
     */
    private static StrictNegativeRecord readStrictNegative(
        Path path,String expectedAccount,boolean permanent
    )throws IOException{
        BasicFileAttributes attrs=Files.readAttributes(
            path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isRegularFile()||attrs.size()<60||attrs.size()>384)
            throw new IOException("G21.49 invalid strict marker inode/size");
        byte[] bytes=MailboxNegativeMarkerBoundedRead.read(
            path,60,384
        );
        if(bytes.length<60||bytes.length>384)
            throw new IOException("G21.49 invalid strict marker byte count");
        String data=new String(bytes,StandardCharsets.US_ASCII);
        if(!Arrays.equals(data.getBytes(StandardCharsets.US_ASCII),bytes))
            throw new IOException("G21.49 strict marker non ASCII");
        String[] fields=data.split("\\n",-1);
        String format=permanent
            ?"SPK-G2147-STRICT-UNCERTAIN-NO-GRANT-V1"
            :"SPK-G2148-STRICT-WRITE-IN-PROGRESS-V1";
        String status=permanent
            ?"MANUAL_REVIEW_NO_GRANT":"IN_PROGRESS_NO_GRANT";
        if(fields.length!=5||!fields[4].isEmpty()||
           !format.equals(fields[0])||
           !expectedAccount.equals(fields[1])||
           !fields[2].matches("[0-9a-f]{64}")||
           !status.equals(fields[3]))
            throw new IOException("G21.49 strict marker format/identity");
        return new StrictNegativeRecord(fields[1],fields[2],bytes);
    }


    /**
     * Prior A branch has a DIFFERENT (checksum-protected) write-once
     * pathname and format. Support strictly read-only parsing here so
     * operators do not confuse valid legacy review with corrupt G21.32
     * proposal evidence. SHA-256 is unkeyed: this proves neither authorship
     * nor a successful inventory/mailbox settlement.
     */
    private static StrictNegativeRecord readLegacyNegative(
        Path markerPath,String expectedAccount
    )throws IOException{
        BasicFileAttributes attrs=Files.readAttributes(
            markerPath,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isRegularFile()||attrs.size()<80||attrs.size()>512)
            throw new IOException("G21.49 old strict marker type/size");
        byte[] bytes=MailboxNegativeMarkerBoundedRead.read(
            markerPath,80,512
        );
        if(bytes.length<80||bytes.length>512)
            throw new IOException("G21.49 old strict marker size");
        String raw=new String(bytes,StandardCharsets.US_ASCII);
        if(!Arrays.equals(raw.getBytes(StandardCharsets.US_ASCII),bytes))
            throw new IOException("G21.49 old marker not ASCII");
        String[] fields=raw.split("\\n",-1);
        if(fields.length!=6||!fields[5].isEmpty()||
           !"SPK-G2147-STRICT-POSTPUBLICATION-UNCERTAIN-V1".equals(
               fields[0]
           )||
           !"REVIEW_REQUIRED_NO_GRANT".equals(fields[1])||
           !expectedAccount.equals(fields[2])||
           !fields[3].matches("[0-9a-f]{64}")||
           !fields[4].matches("[0-9a-f]{64}"))
            throw new IOException("G21.49 old marker identity/format");
        String payload=fields[0]+"\n"+fields[1]+"\n"+
            fields[2]+"\n"+fields[3]+"\n";
        final byte[] checksum;
        try{
            checksum=MessageDigest.getInstance("SHA-256").digest(
                payload.getBytes(StandardCharsets.US_ASCII)
            );
        }catch(NoSuchAlgorithmException unavailable){
            throw new IOException("SHA-256 unavailable",unavailable);
        }
        char[] hex=new char[checksum.length*2];
        final char[] alphabet="0123456789abcdef".toCharArray();
        for(int k=0;k<checksum.length;k++){
            int b=checksum[k]&255;
            hex[k*2]=alphabet[b>>>4];
            hex[k*2+1]=alphabet[b&15];
        }
        if(!fields[4].equals(new String(hex)))
            throw new IOException("G21.49 old marker checksum mismatch");
        return new StrictNegativeRecord(fields[2],fields[3],bytes);
    }

    private static Report inspectLegacyStrictNegative(
        WorldPlayerPersistence persistence,String account,Path marker
    ){
        final StrictNegativeRecord before;
        try{
            before=readLegacyNegative(marker,account);
        }catch(IOException|RuntimeException malformed){
            return result(
                State.STRICT_LEGACY_INVALID_RECORD_NO_AUTHORITY,
                account,null
            );
        }
        final Optional<PlayerSnapshot> snapshot;
        try{
            snapshot=persistence.observeUntrustedMailboxAccount(account);
        }catch(IOException|RuntimeException readFailure){
            return result(
                State.STRICT_MARKER_ACCOUNT_READ_FAILED,account,null
            );
        }
        try{
            StrictNegativeRecord after=readLegacyNegative(marker,account);
            if(!Arrays.equals(before.bytes,after.bytes))
                return result(
                    State.STRICT_LEGACY_CHANGED_NO_AUTHORITY,account,null
                );
        }catch(IOException|RuntimeException changed){
            return result(
                State.STRICT_LEGACY_CHANGED_NO_AUTHORITY,account,null
            );
        }
        if(!snapshot.isPresent())
            return result(State.STRICT_MARKER_MISSING_ACCOUNT,account,null);
        PlayerSnapshot saved=snapshot.get();
        if(!account.equals(saved.username())||
           saved.version()!=PlayerSnapshot.CURRENT_VERSION)
            return result(State.STRICT_MARKER_INVALID_ACCOUNT,account,null);
        final PlayerSnapshot canonical;
        try{
            canonical=PlayerSnapshotCodec.validateAndNormalize(saved);
            if(!canonical.values().equals(saved.values()))
                return result(
                    State.STRICT_MARKER_INVALID_ACCOUNT,account,null
                );
        }catch(RuntimeException invalid){
            return result(State.STRICT_MARKER_INVALID_ACCOUNT,account,null);
        }
        String sha=StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(canonical);
        return result(
            sha.equals(before.snapshotSha)
                ?State.STRICT_LEGACY_CHECKSUM_VALID_EXACT_NO_AUTHORITY
                :State.STRICT_LEGACY_CHECKSUM_VALID_DIVERGENT_NO_AUTHORITY,
            account,sha
        );
    }

    private static Report inspectStrictNegative(
        WorldPlayerPersistence persistence,String account,
        Path markerPath,boolean permanent
    ){
        final StrictNegativeRecord before;
        try{
            before=readStrictNegative(markerPath,account,permanent);
        }catch(IOException|RuntimeException corrupt){
            return result(State.STRICT_MARKER_INVALID,account,null);
        }
        final Optional<PlayerSnapshot> snapshot;
        try{
            snapshot=persistence.observeUntrustedMailboxAccount(account);
        }catch(IOException|RuntimeException readFault){
            return result(
                State.STRICT_MARKER_ACCOUNT_READ_FAILED,account,null
            );
        }
        try{
            StrictNegativeRecord after=readStrictNegative(
                markerPath,account,permanent
            );
            if(!Arrays.equals(before.bytes,after.bytes))
                return result(State.STRICT_MARKER_CHANGED,account,null);
        }catch(IOException|RuntimeException changed){
            return result(State.STRICT_MARKER_CHANGED,account,null);
        }
        if(!snapshot.isPresent())
            return result(
                State.STRICT_MARKER_MISSING_ACCOUNT,account,null
            );
        PlayerSnapshot stored=snapshot.get();
        if(!account.equals(stored.username())||
           stored.version()!=PlayerSnapshot.CURRENT_VERSION)
            return result(
                State.STRICT_MARKER_INVALID_ACCOUNT,account,null
            );
        final PlayerSnapshot canonical;
        try{
            canonical=PlayerSnapshotCodec.validateAndNormalize(stored);
            if(!canonical.values().equals(stored.values()))
                return result(
                    State.STRICT_MARKER_INVALID_ACCOUNT,account,null
                );
        }catch(RuntimeException damaged){
            return result(
                State.STRICT_MARKER_INVALID_ACCOUNT,account,null
            );
        }
        String digest=StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(canonical);
        boolean same=digest.equals(before.snapshotSha);
        return result(
            permanent
                ?(same?State.STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY
                      :State.STRICT_UNCERTAIN_DIGEST_MISMATCH_NO_AUTHORITY)
                :(same?State.STRICT_INTENT_DIGEST_MATCH_NO_AUTHORITY
                      :State.STRICT_INTENT_DIGEST_MISMATCH_NO_AUTHORITY),
            account,digest
        );
    }

    private static boolean matchesIntentIdentity(
        PlayerSnapshot snapshot,MailboxDurableReviewFence.Record marker
    ){
        String prefix="extension."+
            MailboxPreparedClaimJournal.NAMESPACE+".";
        return marker.account.equals(snapshot.username())&&
            marker.messageId.equals(snapshot.value(prefix+"message"))&&
            marker.intentKey.equals(snapshot.value(prefix+"key"));
    }

    private static boolean exactClaimedPostimage(
        PlayerSnapshot snapshot,MailboxDurableReviewFence.Record marker
    ){
        if(!matchesIntentIdentity(snapshot,marker))
            return false;

        try{
            WorldPlayer detached=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(snapshot,detached);
            MailboxPreparedClaimJournal.Intent journal=
                MailboxPreparedClaimJournal.inspectPrepared(detached);
            if(journal==null||
               !marker.intentKey.equals(journal.idempotencyKey)||
               !marker.messageId.equals(journal.messageId)||
               !marker.account.equals(journal.account))
                return false;
            MailboxRewardDeliveryService.Snapshot selected=
                detached.mailbox().get(marker.messageId);
            if(selected==null||
               selected.claimState!=
                   MailboxRewardDeliveryService.ClaimState.CLAIMED)
                return false;

            StringBuilder attachmentFingerprint=new StringBuilder();
            for(RewardDeliveryMessage.Attachment attachment:
                    selected.message.attachments){
                if(attachmentFingerprint.length()>0)
                    attachmentFingerprint.append(',');
                attachmentFingerprint.append(attachment.itemId)
                    .append(':').append(attachment.amount);
            }
            if(!journal.attachmentFingerprint.equals(
                    attachmentFingerprint.toString()))
                return false;

            int[] expectedIds=journal.proposedItemIds();
            int[] expectedQuantities=journal.proposedQuantities();
            if(expectedIds.length!=BankState.INVENTORY_CAPACITY||
               expectedQuantities.length!=BankState.INVENTORY_CAPACITY)
                return false;
            for(int i=0;i<expectedIds.length;i++){
                BankState.InventorySlotSnapshot slot=
                    detached.bank().inventorySlotSnapshot(i);
                int id=slot.occupied?slot.itemId:-1;
                int qty=slot.occupied?slot.quantity:0;
                if(id!=expectedIds[i]||qty!=expectedQuantities[i])
                    return false;
            }
            return true;
        }catch(RuntimeException invalid){
            return false;
        }
    }

    private static boolean sameMarker(
        MailboxDurableReviewFence.Record a,
        MailboxDurableReviewFence.Record b
    ){
        return a.account.equals(b.account)&&
            a.messageId.equals(b.messageId)&&
            a.intentKey.equals(b.intentKey)&&
            a.preparedSha256.equals(b.preparedSha256)&&
            a.hypotheticalSha256.equals(b.hypotheticalSha256);
    }

    private static Report result(
        State state,String account,String digest
    ){
        return new Report(state,account,digest);
    }

    private MailboxFencedRestartForensics(){}
}
