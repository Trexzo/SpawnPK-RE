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
     * Read-only exact-v308 C2S185 Refresh control (widget 32185).
     * The request must already have been decoded by the existing typed
     * client packet boundary. This method does not attach a socket
     * dispatch route or claim any Mailbox root has been opened.
     *
     * Refresh deliberately tolerates stale selected message identity;
     * after a successful rebind, selection is cleared.
     */
    int publishRefreshFromWidget(
        WidgetActionClientRequest request,
        ServerPacketWriter writer
    )throws IOException{
        Objects.requireNonNull(request,"request");
        Objects.requireNonNull(writer,"writer");
        return owned(()->{
            MailboxWidgetIntentAdapter.Intent intent=
                view.resolve(request);

            if(intent==null||
                intent.kind!=MailboxWidgetIntentAdapter.Kind.REFRESH_INBOX)
                throw new IllegalArgumentException(
                    "not an exact Mailbox refresh widget request"
                );

            List<MailboxRewardDeliveryService.Snapshot> rows=
                view.bindInbox();

            // G21.2 validates every row before writing CLEAR.
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

    /**
     * Mark a previously trusted, selected row READ, and publish the exact
     * S2C250 subtype31 operation-2 row read-state update. No client row
     * click mapping or socket dispatcher is implied here.
     *
     * Commit occurs before packet publication. An IOException from the
     * transport therefore does NOT undo the semantic read acknowledgement;
     * an explicit inbox refresh can reconcile the wire.
     *
     * @return true only when UNREAD changed to READ (idempotent replay)
     */
    boolean publishSelectedReadState(
        ServerPacketWriter writer
    )throws IOException{
        Objects.requireNonNull(writer,"writer");
        return owned(()->{
            // Every guard runs before mutation or network publication:
            // registration/generation, selected identity and row range.
            int row=view.selectedBoundRow();
            String messageId=view.selectedMessageId();
            if(row<0||
                row>=MailboxInboxProjection.CLIENT_VISIBLE_ROW_LIMIT)
                throw new IllegalStateException(
                    "Mailbox selected row outside client view"
                );

            MailboxRewardDeliveryService.Snapshot current=
                owner.mailbox().get(messageId);
            if(current==null)
                throw new IllegalStateException(
                    "selected Mailbox message disappeared"
                );

            MailboxInboxProjection.validateSubject(
                current.message.subject
            );
            MailboxPresentationAdapter.readStateCode(
                MailboxRewardDeliveryService.ReadState.READ
            );

            boolean changed=owner.mailbox().markRead(messageId);
            if(changed)
                owner.markMailboxSnapshotKnown();

            MailboxPresentationAdapter.readState(
                writer,
                row,
                MailboxRewardDeliveryService.ReadState.READ
            );
            return changed;
        });
    }

    /**
     * CUSTOM_LOCALLAB guarded delete for the exact-v308 C2S185
     * widget 32184, after an explicitly trusted row selection.
     *
     * This boundary does not connect LocalSession or send any packet;
     * callers may explicitly publish the newly bound inbox afterward.
     * The original server's Mailbox deletion policy is unknown.
     */
    MailboxRewardDeliveryService.Snapshot deleteSafeFromWidget(
        WidgetActionClientRequest request
    )throws IOException{
        Objects.requireNonNull(request,"request");
        return owned(()->{
            MailboxWidgetIntentAdapter.Intent intent=
                view.resolve(request);

            if(intent==null||
                intent.kind!=
                    MailboxWidgetIntentAdapter.Kind.DELETE_MESSAGE||
                intent.messageId==null)
                throw new IllegalArgumentException(
                    "not an exact selected Mailbox delete request"
                );

            MailboxRewardDeliveryService.Snapshot current=
                owner.mailbox().get(intent.messageId);

            if(current==null)
                throw new IllegalStateException(
                    "selected Mailbox envelope no longer exists"
                );

            if(current.claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED)
                throw new IllegalStateException(
                    "CUSTOM_LOCALLAB: cannot delete unclaimed rewards"
                );

            // All checks above run inside the registered WorldPlayer's
            // mutation ownership fence, before any domain mutation.
            MailboxRewardDeliveryService.Snapshot removed=
                owner.mailbox().delete(intent.messageId);

            if(removed==null)
                throw new IllegalStateException(
                    "Mailbox envelope disappeared before deletion"
                );

            owner.markMailboxSnapshotKnown();
            // The old row selection is no longer valid after mutation.
            view.bindInbox();
            return removed;
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
