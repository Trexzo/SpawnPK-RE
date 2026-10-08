package spk.local;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Server-internal, World-owned Mailbox command boundary.
 *
 * This gateway can only access the registered WorldPlayer's own Mailbox.
 * Registration-generation authority is rechecked for every operation. Its
 * small safety policies (35 visible rows; no unclaimed mail deletion) are
 * explicitly CUSTOM_LOCALLAB, not asserted as original SpawnPK behavior.
 *
 * This is NOT socket dispatch, recovered row clicks, reward settlement,
 * banking, inventory mutation, or a functional top-level Mailbox UI.
 */
final class WorldMailboxGateway {
    private final World world;
    private final WorldPlayer owner;
    private final long generation;

    WorldMailboxGateway(
        World world,
        WorldPlayer owner,
        long generation
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.owner=Objects.requireNonNull(owner,"owner");
        if(generation<=0L)
            throw new IllegalArgumentException(
                "World Mailbox generation="+generation
            );
        this.generation=generation;
        owned(()->null);
    }

    List<MailboxRewardDeliveryService.Snapshot> snapshot(){
        return owned(()->owner.mailbox().snapshot());
    }

    MailboxSessionSelection openReadOnlyView(){
        return owned(()->new MailboxSessionSelection(
            world,
            owner,
            generation,
            owner.mailbox()
        ));
    }

    /**
     * Open a rootless exact-client presentation scope for the same
     * WorldPlayer/generation/mailbox. No externally supplied store or
     * recovered Mailbox UI root is accepted.
     */
    WorldMailboxPresentationSession openRootlessPresentation(){
        return owned(()->new WorldMailboxPresentationSession(
            world,
            owner,
            generation
        ));
    }

    /**
     * Validate a hypothetical *complete* post-delivery semantic snapshot
     * before mutating the live account. Prevents unsavable or unrenderable
     * messages from entering a PlayerSnapshot or client-visible Mailbox.
     */
    MailboxRewardDeliveryService.Snapshot deliver(
        RewardDeliveryMessage message
    ){
        return owned(()->{
            RewardDeliveryMessage checked=
                Objects.requireNonNull(message,"message");
            MailboxRewardDeliveryService actual=owner.mailbox();

            if(actual.size()>=
                    MailboxInboxProjection.CLIENT_VISIBLE_ROW_LIMIT)
                throw new IllegalStateException(
                    "CUSTOM_LOCALLAB Mailbox visible row limit"
                );

            MailboxInboxProjection.validateSubject(
                checked.subject
            );

            MailboxRewardDeliveryService projected=
                new MailboxRewardDeliveryService(
                    LocalLabMailboxPersistence.MAX_MESSAGES
                );

            List<MailboxRewardDeliveryService.RestoredEntry> rows=
                new ArrayList<>();

            for(MailboxRewardDeliveryService.Snapshot snapshot:
                    actual.snapshot())
                rows.add(
                    new MailboxRewardDeliveryService.RestoredEntry(
                        snapshot.message,
                        snapshot.readState,
                        snapshot.claimState
                    )
                );

            projected.restore(rows);
            MailboxRewardDeliveryService.Snapshot pending=
                projected.deliver(checked);

            // Recovered client attachment constraints are separate from
            // persistence's long-quantity storage capacity.
            MailboxAttachmentProjection.containerBody(pending);
            LocalLabMailboxPersistence.encode(projected);

            MailboxRewardDeliveryService.Snapshot added=
                actual.deliver(checked);
            owner.markMailboxSnapshotKnown();
            return added;
        });
    }

    /**
     * Explicit trusted server-side read acknowledgement. No claim is
     * made that a live client row click has been decoded or routed.
     */
    boolean markRead(String messageId){
        return owned(()->{
            MailboxRewardDeliveryService mailbox=owner.mailbox();
            boolean changed=mailbox.markRead(messageId);
            if(changed)
                owner.markMailboxSnapshotKnown();
            return changed;
        });
    }

    /**
     * CUSTOM_LOCALLAB loss-prevention policy: never delete an envelope
     * carrying UNCLAIMED attachments. CLAIMED is only an acknowledgement
     * of an external successful settlement; this gateway never creates it.
     */
    MailboxRewardDeliveryService.Snapshot deleteSafe(
        String messageId
    ){
        return owned(()->{
            MailboxRewardDeliveryService mailbox=owner.mailbox();
            MailboxRewardDeliveryService.Snapshot current=
                mailbox.get(messageId);

            if(current==null)
                throw new IllegalArgumentException(
                    "unknown Mailbox message"
                );

            if(current.claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED)
                throw new IllegalStateException(
                    "cannot delete unclaimed Mailbox attachments"
                );

            MailboxRewardDeliveryService.Snapshot removed=
                mailbox.delete(messageId);

            owner.markMailboxSnapshotKnown();
            return removed;
        });
    }

    private interface Owned<T>{
        T run() throws Exception;
    }

    private <T> T owned(Owned<T> action){
        AtomicReference<T> value=new AtomicReference<>();
        boolean current;
        try{
            current=world.withOpenPlayerMutationOwnershipIfCurrent(
                owner,
                generation,
                ()->value.set(action.run())
            );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "World Mailbox operation failed",
                failure
            );
        }

        if(!current)
            throw new IllegalStateException(
                "stale World Mailbox player generation"
            );

        return value.get();
    }
}
