package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Owns manual C2S movement-request coordination after packet decoding.
 *
 * This preserves the existing LocalLab rules for UI closure, combat/player
 * interaction cancellation, pet-pickup cancellation and authoritative route
 * replacement while keeping socket/session concerns outside the handler.
 */
final class LocalMovementRequestHandler {
    interface SessionBridge {
        void clearDialogNumberKeys();
        void clearOpponentOverlay(
            ServerPacketWriter serverPackets,
            String tag,
            String reason
        )throws IOException;
    }

    private final boolean movementEnabled;
    private final BankState bank;
    private final LocalPetInventoryDialogHandler petDialogs;
    private final DevControlCenter devPanel;
    private final MovementState movement;
    private final CombatEngine combat;
    private final EquipmentState equipment;
    private final LocalPlayerInteractionHandler playerInteractions;
    private final LocalPetDropPickupHandler petDropPickup;
    private final NpcRegistry npcs;
    private final SessionBridge bridge;

    LocalMovementRequestHandler(
        boolean movementEnabled,
        BankState bank,
        LocalPetInventoryDialogHandler petDialogs,
        DevControlCenter devPanel,
        MovementState movement,
        CombatEngine combat,
        EquipmentState equipment,
        LocalPlayerInteractionHandler playerInteractions,
        LocalPetDropPickupHandler petDropPickup,
        NpcRegistry npcs,
        SessionBridge bridge
    ){
        this.movementEnabled=movementEnabled;
        this.bank=Objects.requireNonNull(bank,"bank");
        this.petDialogs=Objects.requireNonNull(petDialogs,"petDialogs");
        this.devPanel=Objects.requireNonNull(devPanel,"devPanel");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.combat=Objects.requireNonNull(combat,"combat");
        this.equipment=Objects.requireNonNull(equipment,"equipment");
        this.playerInteractions=Objects.requireNonNull(
            playerInteractions,"playerInteractions");
        this.petDropPickup=Objects.requireNonNull(
            petDropPickup,"petDropPickup");
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    void handle(
        MovementRequest req,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(req==null)return;

        if(!movementEnabled){
            System.out.println(
                tag+"M5_MOVEMENT_REQUEST "+req+
                " action=OBSERVE_ONLY"
            );
            return;
        }

        boolean bankWasOpen=bank.isOpen();
        if(bankWasOpen){
            bank.close(serverPackets);
            System.out.println(
                tag+
                "V5122_BANK_CLOSE_ON_MOVEMENT opcode="+req.opcode+
                " final="+req.finalX()+","+req.finalY()+
                " normalInventory3214Refresh=true"
            );
        }

        if(petDialogs.hasAnyOpen()||devPanel.isOpen()){
            serverPackets.fixed(219,new byte[0]);

            LocalPetInventoryDialogHandler.CloseState petDialogClose=
                petDialogs.clearAll();

            boolean mini=petDialogClose.miniConfigWasOpen;
            boolean color=petDialogClose.petColorWasOpen;
            boolean accessory=petDialogClose.petAccessoryWasOpen;
            boolean panel=devPanel.isOpen();

            devPanel.close();
            bridge.clearDialogNumberKeys();

            System.out.println(
                tag+
                "V5170_DIALOG_CLOSE_ON_MOVEMENT mini="+mini+
                " petColor="+color+
                " petAccessory="+accessory+
                " devPanel="+panel+
                " opcode="+req.opcode
            );
        }

        int clientStartX=
            req.waypointCount()>0
                ?req.x[0]
                :movement.x();
        int clientStartY=
            req.waypointCount()>0
                ?req.y[0]
                :movement.y();

        int startDrift=
            chebyshev(
                movement.x(),
                movement.y(),
                clientStartX,
                clientStartY
            );

        if(startDrift>1){
            System.out.println(
                tag+
                "V5123_MOVEMENT_START_DRIFT observeOnly=true clientStart="+
                clientStartX+","+clientStartY+
                " authority="+movement.x()+","+movement.y()+
                " chebyshev="+startDrift+
                " queuedBefore="+movement.queued()+
                " final="+req.finalX()+","+req.finalY()
            );
        }

        long now=System.currentTimeMillis();

        if(combat.consumeImmediateApproachEcho(req,now)){
            System.out.println(
                tag+
                "V5123_COMBAT_APPROACH_ECHO_IGNORED "+
                combat.approachEchoSummary(req)+
                " weapon="+equipment.weapon()+
                " authorityWorld="+movement.x()+","+movement.y()
            );
            return;
        }

        if(combat.active()){
            boolean cancelled=
                combat.cancelForManualMovement();

            if(cancelled){
                serverPackets.varShort(
                    81,
                    CombatSync.player81InteractionOnly(-1)
                );
                bridge.clearOpponentOverlay(
                    serverPackets,
                    tag,
                    "MANUAL_MOVEMENT"
                );
                System.out.println(
                    tag+
                    "V5123_COMBAT_CANCEL_ON_MANUAL_MOVEMENT final="+
                    req.finalX()+","+req.finalY()+
                    " weapon="+equipment.weapon()+
                    " clientInteractionTarget=CLEAR"
                );
            }
        }

        LocalPlayerInteractionHandler.Cancellation playerCancel=
            playerInteractions.cancelActive();

        if(playerCancel.hadAnything()){
            if(playerCancel.hadFacingInteraction){
                serverPackets.varShort(
                    81,
                    CombatSync.player81InteractionOnly(-1)
                );
            }

            System.out.println(
                tag+
                "V5141_PLAYER_INTERACTION_CANCEL reason=MANUAL_MOVEMENT clientInteractionTarget="+
                (playerCancel.hadFacingInteraction
                    ?"CLEAR"
                    :"UNCHANGED")+
                " pendingTrade="+playerCancel.hadTrade
            );
        }

        petDropPickup.cancelForMovement(
            serverPackets,
            tag
        );

        boolean replacingLiveRoute=
            npcs.pet()!=null&&
            (
                movement.queued()>0||
                npcs.needsFollow(movement)
            );

        String result=movement.accept(req);
        String petRouteReset="NONE";

        if(result.startsWith("ACCEPTED")&&
           replacingLiveRoute){
            petRouteReset=npcs.onOwnerRouteReplaced();
        }

        System.out.println(
            tag+"M5_MOVEMENT_REQUEST "+req+
            " action="+result+
            " authorityWorld="+
            movement.x()+","+movement.y()+
            " petRoute="+petRouteReset
        );
    }

    static int chebyshev(
        int x0,
        int y0,
        int x1,
        int y1
    ){
        return Math.max(
            Math.abs(x1-x0),
            Math.abs(y1-y0)
        );
    }
}
