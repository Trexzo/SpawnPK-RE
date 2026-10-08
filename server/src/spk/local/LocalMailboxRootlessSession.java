package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * G21.16: explicit opt-in LocalLab rootless Mailbox session bridge.
 *
 * Caller holds the authoritative World command context. This bridge is
 * scoped to a recovered client root but does not claim the original
 * server's Mailbox opening order or authorization policy. Every publisher rechecks
 * WorldPlayer identity and registered generation before touching state.
 *
 * Scope is installed after explicit CUSTOM_LOCALLAB ::mail sync OR the
 * recovered native ::mail command opening root 32019. Both retire on
 * C2S130 interface-close or session teardown.
 */
final class LocalMailboxRootlessSession implements AutoCloseable {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2116_ROOTLESS_MAILBOX_SYNC";
    /** Verified from pinned-v308 rs.n.c.c.a.a() bytecode. */
    static final int NATIVE_V308_MAILBOX_ROOT=32019;

    private final World world;
    private final WorldPlayer owner;
    private final long generation;
    private WorldMailboxPresentationSession active;
    private boolean nativeRootOpen;
    private boolean closed;

    LocalMailboxRootlessSession(
        World world,WorldPlayer owner,long generation
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.owner=Objects.requireNonNull(owner,"owner");
        if(generation<=0)
            throw new IllegalArgumentException("generation="+generation);
        this.generation=generation;
    }

    boolean isActive(){
        return !closed&&active!=null;
    }

    /** Distinguish a real S2C97 Mailbox root from ::mail sync. */
    boolean isNativeRootOpen(){
        return isActive()&&nativeRootOpen;
    }

    /**
     * Replace the previous bound inbox only after opening a fresh
     * generation-fenced view. Output remains rootless, even for ::mail.
     */
    int sync(ServerPacketWriter writer)throws IOException{
        Objects.requireNonNull(writer,"writer");
        if(closed)
            throw new IllegalStateException(
                "closed rootless Mailbox session"
            );

        WorldMailboxPresentationSession next=
            new WorldMailboxGateway(
                world,owner,generation
            ).openRootlessPresentation();

        try{
            int count=next.publishInbox(writer);
            WorldMailboxPresentationSession previous=active;
            active=next;
            if(previous!=null)
                previous.close();
            return count;
        }catch(IOException|RuntimeException failure){
            next.close();
            throw failure;
        }
    }

    /**
     * Open the exact pinned-v308 native root widget 32019 via certified
     * S2C97, then populate its bounded subtype31 inbox projection.
     *
     * Packet ordering is explicitly CUSTOM_LOCALLAB_G2117, not a claim
     * to have recovered the original live server's opening transaction.
     * Validation failures abort the buffered batch before any root bytes
     * can escape; transport failures after commit are not reversible.
     */
    int openNativeRoot(ServerPacketWriter writer)throws IOException{
        Objects.requireNonNull(writer,"writer");
        if(closed)
            throw new IllegalStateException(
                "closed Mailbox root presentation scope"
            );

        WorldMailboxPresentationSession next=
            new WorldMailboxGateway(
                world,owner,generation
            ).openRootlessPresentation();

        boolean begun=false;
        boolean committed=false;
        try{
            writer.beginBatch();
            begun=true;
            writer.fixed(
                97,
                BootstrapPackets.interface97(
                    NATIVE_V308_MAILBOX_ROOT
                )
            );
            int count=next.publishInbox(writer);
            writer.endBatch();
            begun=false;
            committed=true;

            WorldMailboxPresentationSession previous=active;
            active=next;
            nativeRootOpen=true;
            if(previous!=null)
                previous.close();
            return count;
        }finally{
            if(begun){
                try{
                    writer.abortBatch();
                }catch(Throwable ignored){
                    // Preserve the first publication failure.
                }
            }
            if(!committed)
                next.close();
        }
    }

    /**
     * Handle only native v308 inbox rows and safe Refresh in the active
     * explicitly synchronized scope. Suppress claim/delete controls so no
     * unowned settlement/deletion path can run through later UI handlers.
     *
     * Returns false for other widgets, preserving ordinary precedence.
     */
    boolean handleWidget(
        WidgetActionClientRequest request,
        ServerPacketWriter writer
    )throws IOException{
        Objects.requireNonNull(request,"request");
        Objects.requireNonNull(writer,"writer");
        if(!isActive())
            return false;

        int row=MailboxRowWidgetIntentAdapter.resolveIfRow(request);
        if(row>=0){
            active.publishRowDetailFromWidget(request,writer);
            return true;
        }

        int widget=request.widgetId();
        if(widget==MailboxWidgetIntentAdapter.REFRESH_INBOX_WIDGET){
            active.publishRefreshFromWidget(request,writer);
            return true;
        }

        if(widget==MailboxWidgetIntentAdapter.DEPOSIT_BANK_WIDGET||
           widget==MailboxWidgetIntentAdapter.DEPOSIT_INVENTORY_WIDGET||
           widget==MailboxWidgetIntentAdapter.DELETE_MESSAGE_WIDGET){
            // C2S185 is verified, but authorization to settle or delete
            // from a live Mailbox root has NOT been recovered.
            ClientRequestMetadata metadata=request.metadata();
            if(metadata.opcode!=185||
               metadata.provenance!=
                   ClientRequestProvenance.EXACT_CURRENT_CLIENT)
                throw new IllegalArgumentException(
                    "Mailbox control must be exact current C2S185"
                );
            return true;
        }

        return false;
    }

    void onInterfaceClose(){
        retire();
    }

    private void retire(){
        WorldMailboxPresentationSession previous=active;
        active=null;
        nativeRootOpen=false;
        if(previous!=null)
            previous.close();
    }

    @Override public void close(){
        closed=true;
        retire();
    }
}
