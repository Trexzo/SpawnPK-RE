package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Owner-fenced exact-v308 Mailbox packet composition, without opening a UI.
 *
 * Only a G21.9 gateway belonging to the registered WorldPlayer opens this
 * scope. All serialization takes place under the World-owned player
 * generation gate; transport failure can still leave a partial packet
 * sequence and is not a gameplay transaction.
 *
 * The row argument below is trusted server-side state, NOT a claim about
 * the original client's inbound row-click representation.
 */
final class WorldMailboxPresentationSession implements AutoCloseable {
    private final World world;
    private final WorldPlayer owner;
    private final long generation;
    private final MailboxSessionSelection view;
    private boolean closed;

    WorldMailboxPresentationSession(
        World world,
        WorldPlayer owner,
        long generation
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.owner=Objects.requireNonNull(owner,"owner");
        if(generation<=0)
            throw new IllegalArgumentException("generation="+generation);
        this.generation=generation;
        this.view=new MailboxSessionSelection(
            world,
            owner,
            generation,
            owner.mailbox()
        );
    }

    /** Publish clear, bounded ordered append rows and finalize, rootless. */
    int publishInbox(ServerPacketWriter writer)throws IOException{
        Objects.requireNonNull(writer,"writer");
        return owned(()->{
            List<MailboxRewardDeliveryService.Snapshot> rows=
                view.bindInbox();
            MailboxInboxProjection.publish(writer,rows);
            return rows.size();
        });
    }

    /**
     * A *trusted server-side* row index resolves against the currently
     * bound immutable inbox view. Preflight S2C53 and S2C126 BEFORE any
     * S2C250 subtype31 selection opcode or detail packet is written.
     */
    MailboxRewardDeliveryService.Snapshot
        publishTrustedRowDetail(
            int row,
            ServerPacketWriter writer
        )throws IOException{
        Objects.requireNonNull(writer,"writer");
        return owned(()->{
            MailboxRewardDeliveryService.Snapshot snapshot=
                view.selectBoundRow(row);

            MailboxInboxProjection.validateSubject(
                snapshot.message.subject
            );
            MailboxPresentationAdapter.claimStateCode(
                snapshot.claimState
            );
            MailboxAttachmentProjection.containerBody(snapshot);

            byte[] subject=BootstrapPackets.widgetText126(
                MailboxSelectedDetailProjection.SUBJECT_WIDGET,
                snapshot.message.subject
            );
            if(subject.length>0xffff)
                throw new IllegalArgumentException(
                    "Mailbox subject S2C126 frame too large"
                );

            // CUSTOM_LOCALLAB ordering: selection first, then content
            // and claim-state visibility. Never inferred as original
            // server ordering or proof of a live Mailbox root.
            MailboxPresentationAdapter.select(writer,row);
            MailboxSelectedDetailProjection.publish(writer,snapshot);
            return snapshot;
        });
    }

    private interface Owned<T> {
        T run()throws Exception;
    }

    private <T> T owned(Owned<T> action)throws IOException{
        final Object[] result={null};
        boolean admitted;
        try{
            admitted=world.withOpenPlayerMutationOwnershipIfCurrent(
                owner,
                generation,
                ()->{
                    if(closed)
                        throw new IllegalStateException(
                            "closed Mailbox presentation scope"
                        );
                    result[0]=action.run();
                }
            );
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IOException(
                "Mailbox wire publication failed",
                failure
            );
        }

        if(!admitted)
            throw new IllegalStateException(
                "stale Mailbox publication generation"
            );

        @SuppressWarnings("unchecked")
        T typed=(T)result[0];
        return typed;
    }

    @Override public void close(){
        synchronized(owner.mutationLock()){
            closed=true;
            view.close();
        }
    }
}
