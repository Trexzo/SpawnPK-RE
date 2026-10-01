package spk.local;

import java.io.IOException;
import spk.content.api.ContentInteractionResult;
import spk.content.builtin.LocalLabCoreContentModule;

/**
 * Typed object-interaction coordinator for the exact current bank-object path.
 *
 * Raw object packet decoding remains in ClientPacketProbe. This handler owns the
 * existing immediate/deferred bank-open state and leaves all non-bank object
 * semantics explicitly unimplemented.
 */
final class LocalBankObjectInteractionHandler {
    @FunctionalInterface
    interface RootOpenAction {
        String open() throws IOException;
    }

    @FunctionalInterface
    interface RootOwner {
        String publish(
            RootOpenAction action
        ) throws IOException;
    }

    private final BankState bank;
    private final MovementState movement;
    private final InteractionApproachResolver approach;
    private final ContentRegistry contentRegistry;

    private ObjectInteraction pending;
    private long pendingDeadlineMs;
    private RootOwner rootOwner=
        action->action.open();
    private boolean rootOwnerInstalled;

    LocalBankObjectInteractionHandler(
        BankState bank,
        MovementState movement
    ){
        this(
            bank,
            movement,
            null
        );
    }

    LocalBankObjectInteractionHandler(
        BankState bank,
        MovementState movement,
        ContentRegistry contentRegistry
    ){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.approach=new InteractionApproachResolver(this.movement);
        this.contentRegistry=contentRegistry;
    }

    void installRootOwner(
        RootOwner owner
    ){
        RootOwner checked=
            java.util.Objects.requireNonNull(
                owner,
                "owner"
            );

        if(rootOwnerInstalled)
            throw new IllegalStateException(
                "Bank root owner already installed"
            );

        rootOwner=checked;
        rootOwnerInstalled=true;
    }

    String handle(ObjectInteraction request,ServerPacketWriter serverPackets)throws IOException{
        if(request==null)return null;

        ContentInteractionResult content=
            contentDecision(
                request
            );

        if(content==null){
            pending=null;
            return "OBJECT_INTERACTION "+request+
                " action=DECODED_NOT_IMPLEMENTED decoderAligned=true";
        }

        if(!LocalLabCoreContentModule
                .BANK_OBJECT_SERVICE
                .equals(content.outcome())){
            pending=null;
            return "OBJECT_INTERACTION "+request+
                " action=CONTENT_HANDLED_FAIL_CLOSED"+
                " outcome="+content.outcome();
        }

        if(adjacentTo(request.worldX,request.worldY)){
            pending=null;
            return openNow(
                request,serverPackets,"OPENED_ADJACENT_IMMEDIATE");
        }

        InteractionApproachResolver.Result approachResult=
            approach.queueAdjacent(
                request.worldX,
                request.worldY
            );

        if(!approachResult.queued()){
            pending=null;
            return "V5_BANK_INTERACTION "+request+
                " authorityWorld="+movement.x()+","+movement.y()+
                " distance="+chebyshev(
                    movement.x(),movement.y(),request.worldX,request.worldY)+
                " action=REJECTED_SERVER_APPROACH_"+approachResult.status+
                " approach="+approachResult;
        }

        pending=request;
        pendingDeadlineMs=System.currentTimeMillis()+10_000L;
        return "V5_BANK_INTERACTION "+request+
            " authorityWorld="+movement.x()+","+movement.y()+
            " distance="+chebyshev(
                movement.x(),movement.y(),request.worldX,request.worldY)+
            " action=DEFERRED_UNTIL_ADJACENT"+
            " serverApproach="+approachResult;
    }

    String tick(long now,ServerPacketWriter serverPackets)throws IOException{
        ObjectInteraction request=pending;
        if(request==null)return null;

        if(now>pendingDeadlineMs){
            pending=null;
            return "V5_BANK_INTERACTION "+request+
                " authorityWorld="+movement.x()+","+movement.y()+
                " action=CANCELLED_TIMEOUT_NOT_ADJACENT";
        }

        if(!adjacentTo(request.worldX,request.worldY)){
            if(movement.queued()==0){
                pending=null;
                return "V5_BANK_INTERACTION "+request+
                    " authorityWorld="+movement.x()+","+movement.y()+
                    " action=CANCELLED_PATH_ENDED_NOT_ADJACENT";
            }
            return null;
        }

        pending=null;
        movement.clearQueuedPath();
        return openNow(
            request,serverPackets,"OPENED_AFTER_AUTHORITATIVE_ARRIVAL");
    }

    boolean hasPending(){
        return pending!=null;
    }

    private ContentInteractionResult contentDecision(
        ObjectInteraction request
    ){
        if(contentRegistry==null||
           request==null||
           request.opcode!=132)
            return null;

        return contentRegistry
            .dispatchObjectOption(
                request.objectId,
                1,
                request.worldX,
                request.worldY
            );
    }

    private String openNow(
        ObjectInteraction request,
        ServerPacketWriter serverPackets,
        String reason
    )throws IOException{
        return rootOwner.publish(
            ()->{
                bank.open(
                    serverPackets
                );
                return "V5_BANK_OPEN "+request+
                    " authorityWorld="+movement.x()+","+movement.y()+
                    " distance="+chebyshev(
                        movement.x(),movement.y(),request.worldX,request.worldY)+
                    " root="+BankState.BANK_ROOT+
                    " bankContainer="+BankState.BANK_CONTAINER+
                    " bankInventoryRoot="+BankState.BANK_INVENTORY_ROOT+
                    " inventoryContainer="+BankState.BANK_INVENTORY_CONTAINER+
                    " bankOccupied="+bank.bankSlots()+"/"+bank.bankCapacity()+
                    " inventoryOccupied="+bank.inventorySlots()+"/"+bank.inventoryCapacity()+
                    " placeholders="+bank.placeholdersEnabled()+
                    " action="+reason;
            }
        );
    }

    private boolean adjacentTo(int x,int y){
        return chebyshev(movement.x(),movement.y(),x,y)<=1;
    }

    private static int chebyshev(int x0,int y0,int x1,int y1){
        return Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
    }
}
