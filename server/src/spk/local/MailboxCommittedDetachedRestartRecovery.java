package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;

/**
 * G21.89: actual, opt-in crash-restart decoding of an independently
 * VERIFIED G21.86 committed disk terminal into a FRESH, DETACHED owner.
 *
 * The detached player NEVER joins World; only its canonical round-trip
 * snapshot is returned. Already-CLAIMED attachments are NOT re-granted.
 * No replay, live mutation, session admission, save, release or ACK.
 *
 * This is not a fully atomic multi-file physical-disk transaction, a
 * trusted external-process lock, or permission for live settlement.
 */
final class MailboxCommittedDetachedRestartRecovery {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2189_DETACHED_RESTART_RECOVERY_NO_GRANT";

    static final class Result {
        final String account;
        final String messageId;
        final String idempotencyKey;
        final String terminalSha256;
        final PlayerSnapshot exactRestoredSnapshot;
        final int occupiedInventorySlots;
        // Retained only for negative comparison, never a live/positive
        // transaction capability or a filesystem lock.
        private final StableLeaf accountObject;
        private final StableLeaf journalObject;
        private final StableLeaf commitObject;
        final boolean claimedBeforeAnyReplay=true;
        final boolean detachedRoundTrip=true;
        final boolean transactionCommitted=false;
        final boolean liveApplied=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean rollbackAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Result(
            MailboxGuardedDiskCommitRecord.Observation record,
            PlayerSnapshot exact,int occupied,
            StableLeaf accountLeaf,StableLeaf journalLeaf,
            StableLeaf commitLeaf
        ){
            account=record.account;
            messageId=record.messageId;
            idempotencyKey=record.intentKey;
            terminalSha256=record.terminalSha256;
            exactRestoredSnapshot=exact;
            occupiedInventorySlots=occupied;
            accountObject=accountLeaf;
            journalObject=journalLeaf;
            commitObject=commitLeaf;
        }

        // G21.91: independently recovered checksums can be identical
        // after a same-byte inode swap. Preserve the file-object
        // identity witness already captured and checked by G21.89.
        // This proves only continuity over the two observations.
        boolean sameDiskObjects(Result other){
            return other!=null&&account.equals(other.account)&&
                accountObject.same(other.accountObject)&&
                journalObject.same(other.journalObject)&&
                commitObject.same(other.commitObject);
        }
    }

    private static final class StableLeaf {
        final Object key;
        final long size;
        final java.nio.file.attribute.FileTime modified;
        final java.nio.file.attribute.FileTime created;
        StableLeaf(Path path)throws IOException{
            final BasicFileAttributes attributes=Files.readAttributes(
                path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attributes.isRegularFile()||attributes.fileKey()==null)
                throw new IOException(
                    "G21.89 RECOVERY_FILE_IDENTITY_UNSAFE_NO_GRANT");
            key=attributes.fileKey();
            size=attributes.size();
            modified=attributes.lastModifiedTime();
            created=attributes.creationTime();
        }
        boolean same(StableLeaf other){
            return Objects.equals(key,other.key)&&
                size==other.size&&
                Objects.equals(modified,other.modified)&&
                Objects.equals(created,other.created);
        }
    }

    private final FilePlayerRepository.PathResolver resolver;

    MailboxCommittedDetachedRestartRecovery(
        FilePlayerRepository.PathResolver resolver
    ){
        this.resolver=Objects.requireNonNull(resolver,"resolver");
    }

    Result recoverDetached(String username)throws IOException{
        if(username==null||!username.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.89 invalid canonical account");
        final Path account=Objects.requireNonNull(
            resolver.resolve(username),"G21.89 account file"
        ).toAbsolutePath().normalize();
        final FilePlayerRepository.PathResolver pinned=other->{
            if(!username.equals(other))
                throw new IllegalArgumentException(
                    "G21.89 foreign recovery account");
            return account;
        };
        // Bounded cooperating lock, NOT nested with G21.86 or G21.83.
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(account,1500L,()->{
                final MailboxGuardedDiskCommitRecord record=
                    new MailboxGuardedDiskCommitRecord(pinned);
                final Path commit=record.recordPath(username);
                final Path prepared=
                    new MailboxDurableIdempotencyIntentJournal(pinned)
                        .journalPath(username);
                // The absent, corrupt or negative-marked record never
                // creates a detached positive grant.
                MailboxGuardedDiskCommitRecord.Observation first=
                    record.inspectInsidePublicationLock(username,account);
                if(first.status!=MailboxGuardedDiskCommitRecord.Status
                        .DISK_COMMIT_MATCH_NO_LIVE_APPLY||
                   !first.recordValidated||!first.diskCommitRecordMatched)
                    throw new IOException(
                        "G21.89 RECOVERY_DISK_COMMIT_UNTRUSTED_NO_GRANT "+
                        first.status);
                StableLeaf accountBefore=new StableLeaf(account);
                StableLeaf journalBefore=new StableLeaf(prepared);
                StableLeaf commitBefore=new StableLeaf(commit);

                Optional<PlayerSnapshot> file=
                    new FilePlayerRepository(pinned).load(username);
                if(!file.isPresent())
                    throw new IOException(
                        "G21.89 RECOVERY_TERMINAL_MISSING_NO_GRANT");
                PlayerSnapshot image=file.get();
                if(!username.equals(image.username())||
                   MailboxAtomicTerminalSnapshot.inspect(image).state!=
                       MailboxAtomicTerminalSnapshot.State
                           .COHERENT_TERMINAL_NO_GRANT||
                   !first.terminalSha256.equals(
                       StrictDurablePlayerSnapshotWriter
                           .canonicalSnapshotSha256(image)))
                    throw new IOException(
                        "G21.89 RECOVERY_TERMINAL_IMAGE_CHANGED_NO_GRANT");

                // Decode original persisted claimed-state and inventory
                // ONCE, on a new player detached from all World sessions.
                WorldPlayer detached=new WorldPlayer();
                final PlayerSnapshot normalized;
                try{
                    normalized=PlayerSnapshotCodec.applyValidated(
                        image,detached);
                }catch(RuntimeException invalid){
                    throw new IOException(
                        "G21.89 RECOVERY_DETACHED_DECODE_FAILED_NO_GRANT",
                        invalid);
                }
                MailboxRewardDeliveryService.Snapshot selected=
                    detached.mailbox().get(first.messageId);
                MailboxPreparedClaimJournal.Intent intention;
                try{
                    intention=MailboxPreparedClaimJournal
                        .inspectPrepared(detached);
                }catch(RuntimeException invalid){
                    throw new IOException(
                        "G21.89 RECOVERY_CLAIM_INTENT_INVALID_NO_GRANT",
                        invalid);
                }
                if(selected==null||
                   selected.claimState!=
                       MailboxRewardDeliveryService.ClaimState.CLAIMED||
                   intention==null||
                   !username.equals(intention.account)||
                   !first.messageId.equals(intention.messageId)||
                   !first.intentKey.equals(intention.idempotencyKey))
                    throw new IOException(
                        "G21.89 RECOVERY_CLAIM_NOT_ALREADY_SETTLED_NO_GRANT");
                int[] expectedIds=intention.proposedItemIds();
                int[] expectedAmounts=intention.proposedQuantities();
                if(expectedIds.length!=BankState.INVENTORY_CAPACITY||
                   expectedAmounts.length!=BankState.INVENTORY_CAPACITY)
                    throw new IOException(
                        "G21.89 RECOVERY_INVENTORY_SHAPE_INVALID_NO_GRANT");
                int occupied=0;
                for(int slot=0;slot<BankState.INVENTORY_CAPACITY;slot++){
                    BankState.InventorySlotSnapshot item=
                        detached.bank().inventorySlotSnapshot(slot);
                    int actualId=item.occupied?item.itemId:-1;
                    int actualAmount=item.occupied?item.quantity:0;
                    if(item.occupied)occupied++;
                    if(actualId!=expectedIds[slot]||
                       actualAmount!=expectedAmounts[slot])
                        throw new IOException(
                            "G21.89 RECOVERY_INVENTORY_DIVERGED_NO_GRANT");
                }

                PlayerSnapshot roundTrip=PlayerSnapshotCodec.capture(
                    username,detached,
                    PlayerSnapshotCodec.accessoryItem(image));
                if(!roundTrip.values().equals(image.values())||
                   !normalized.values().equals(image.values())||
                   roundTrip.version()!=image.version())
                    throw new IOException(
                        "G21.89 RECOVERY_ROUNDTRIP_DIVERGED_NO_GRANT");

                // Re-observe the independent disk record and underlying
                // physical files without leaving this publication lock.
                // G21.87 sidecar admission remains denied separately.
                MailboxGuardedDiskCommitRecord.Observation last=
                    record.inspectInsidePublicationLock(username,account);
                if(last.status!=first.status||
                   !Objects.equals(last.terminalSha256,
                       first.terminalSha256)||
                   !Objects.equals(last.intentKey,first.intentKey)||
                   !Objects.equals(last.messageId,first.messageId)||
                   !accountBefore.same(new StableLeaf(account))||
                   !journalBefore.same(new StableLeaf(prepared))||
                   !commitBefore.same(new StableLeaf(commit))||
                   !account.equals(
                       resolver.resolve(username).toAbsolutePath().normalize()))
                    throw new IOException(
                        "G21.89 RECOVERY_FILE_OR_RECORD_CHANGED_NO_GRANT");
                return new Result(first,roundTrip,occupied,
                    accountBefore,journalBefore,commitBefore);
            });
    }
}
