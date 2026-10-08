package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * G21.16: explicit opt-in LocalLab rootless Mailbox session bridge.
 *
 * Caller holds the authoritative World command context. This bridge is
 * neither a recovered top-level Mailbox root nor a claim that the original
 * server automatically presents this UI. Every G21.10+ publisher rechecks
 * WorldPlayer identity and registered generation before touching state.
 *
 * Scope is installed ONLY after the explicit CUSTOM_LOCALLAB ::mail sync
 * command, and retired on C2S130 interface-close or session teardown.
 */
final class LocalMailboxRootlessSession implements AutoCloseable {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2116_ROOTLESS_MAILBOX_SYNC";

    private final World world;
    private final WorldPlayer owner;
    private final long generation;
    private WorldMailboxPresentationSession active;
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
        if(previous!=null)
            previous.close();
    }

    @Override public void close(){
        closed=true;
        retire();
    }
}
