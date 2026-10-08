package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generation-fenced, server-internal Mailbox view/selection.
 *
 * Session binding is supplied by the caller, not inferred from a socket or
 * a client row-click packet. A caller must bind the correct Mailbox service
 * for its authenticated WorldPlayer; no cross-account store mapping is
 * claimed here. Every view operation passes the World mutation-ownership
 * gate, including generation and registry object identity.
 *
 * All view fields are accessed while holding owner.mutationLock().
 */
final class MailboxSessionSelection implements AutoCloseable {
    private final World world;
    private final WorldPlayer owner;
    private final long generation;
    private final MailboxRewardDeliveryService mailbox;
    private List<MailboxRewardDeliveryService.Snapshot> rows=
        Collections.emptyList();
    private MailboxRewardDeliveryService.Snapshot selected;
    private boolean bound;
    private boolean closed;

    MailboxSessionSelection(
        World world,
        WorldPlayer owner,
        long expectedGeneration,
        MailboxRewardDeliveryService mailbox
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.owner=Objects.requireNonNull(owner,"owner");
        this.mailbox=Objects.requireNonNull(mailbox,"mailbox");
        if(expectedGeneration<=0L)
            throw new IllegalArgumentException(
                "expectedGeneration="+expectedGeneration
            );
        this.generation=expectedGeneration;
        owned(()->null);
    }

    /**
     * Explicit trusted server-side refresh. Does not publish packets or
     * infer a client UI root. Successful rebinding retires old selection.
     */
    List<MailboxRewardDeliveryService.Snapshot> bindInbox(){
        return owned(()->{
            List<MailboxRewardDeliveryService.Snapshot> candidate=
                new ArrayList<>(mailbox.snapshot());

            if(candidate.size()>
                    MailboxInboxProjection.CLIENT_VISIBLE_ROW_LIMIT)
                throw new IllegalArgumentException(
                    "Mailbox visible row limit exceeded"
                );

            Set<String> seen=new HashSet<>();
            for(MailboxRewardDeliveryService.Snapshot row:candidate){
                MailboxRewardDeliveryService.Snapshot checked=
                    Objects.requireNonNull(row,"row");
                RewardDeliveryMessage message=
                    Objects.requireNonNull(
                        checked.message,"row message"
                    );
                if(!seen.add(message.messageId))
                    throw new IllegalArgumentException(
                        "duplicate Mailbox row "+message.messageId
                    );
                MailboxPresentationAdapter.readStateCode(
                    checked.readState
                );
                MailboxInboxProjection.validateSubject(
                    message.subject
                );
            }

            rows=Collections.unmodifiableList(candidate);
            selected=null;
            bound=true;
            return rows;
        });
    }

    /**
     * Row index comes from an explicitly trusted server-side binding.
     * This is not a claim that an inbound C2S row-click is recovered.
     */
    MailboxRewardDeliveryService.Snapshot selectBoundRow(int row){
        return owned(()->{
            requireBound();
            if(row<0||row>=rows.size())
                throw new IllegalArgumentException(
                    "Mailbox row outside bound view: "+row
                );
            MailboxRewardDeliveryService.Snapshot candidate=
                rows.get(row);
            requireSameEntry(candidate);
            selected=candidate;
            return candidate;
        });
    }

    String selectedMessageId(){
        return owned(()->{
            requireBound();
            return selected==null
                ?null
                :requireSameEntry(selected).message.messageId;
        });
    }

    /**
     * Resolve the currently selected server-owned row using immutable
     * identity, never a client-provided row-click or recycled message ID.
     * Snapshot read/claim transitions retain the same message object.
     */
    int selectedBoundRow(){
        return owned(()->{
            requireBound();
            if(selected==null)
                throw new IllegalStateException(
                    "Mailbox read requires a selected row"
                );

            requireSameEntry(selected);

            for(int i=0;i<rows.size();i++){
                if(rows.get(i)==selected)
                    return i;
            }

            throw new IllegalStateException(
                "selected Mailbox row was retired"
            );
        });
    }

    MailboxWidgetIntentAdapter.Intent resolve(
        WidgetActionClientRequest request
    ){
        return owned(()->{
            WidgetActionClientRequest checked=
                Objects.requireNonNull(request,"request");

            // Refresh needs no selected envelope. In particular, an old
            // bound row may have been removed/replaced under the same ID.
            // Its stale identity must not prevent a safe exact C2S185
            // refresh from rebuilding the server-owned inbox view.
            if(checked.widgetId()==
                    MailboxWidgetIntentAdapter.REFRESH_INBOX_WIDGET)
                return MailboxWidgetIntentAdapter.resolve(
                    checked,
                    mailbox,
                    null
                );

            String selectedId=selected==null
                ?null
                :requireSameEntry(selected).message.messageId;
            return MailboxWidgetIntentAdapter.resolve(
                checked,
                mailbox,
                selectedId
            );
        });
    }

    private MailboxRewardDeliveryService.Snapshot requireSameEntry(
        MailboxRewardDeliveryService.Snapshot boundEntry
    ){
        MailboxRewardDeliveryService.Snapshot current=
            mailbox.get(boundEntry.message.messageId);

        // Checking only message ID is unsafe after deletion + redelivery.
        if(current==null||
            current.message!=boundEntry.message)
            throw new IllegalStateException(
                "stale Mailbox view row; refresh required"
            );

        return current;
    }

    private void requireBound(){
        if(!bound)
            throw new IllegalStateException(
                "Mailbox inbox not bound"
            );
    }

    private interface ViewAction<T>{
        T run();
    }

    private <T> T owned(ViewAction<T> action){
        AtomicReference<T> out=new AtomicReference<>();
        boolean admitted;
        try{
            admitted=world.withOpenPlayerMutationOwnershipIfCurrent(
                owner,
                generation,
                ()->{
                    if(closed)
                        throw new IllegalStateException(
                            "Mailbox view closed"
                        );
                    out.set(action.run());
                }
            );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Mailbox view owner operation failed",
                failure
            );
        }

        if(!admitted)
            throw new IllegalStateException(
                "stale Mailbox WorldPlayer generation"
            );

        return out.get();
    }

    @Override public void close(){
        synchronized(owner.mutationLock()){
            closed=true;
            selected=null;
            rows=Collections.emptyList();
            bound=false;
        }
    }
}
