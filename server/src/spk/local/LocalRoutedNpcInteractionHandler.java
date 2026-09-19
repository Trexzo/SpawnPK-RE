package spk.local;

import java.io.IOException;

/**
 * Residual definition-routed NPC interaction coordinator.
 *
 * LocalSession retains higher-priority special intercepts (pet Pick-up,
 * Yoshiganger Switch-effect and certified combat-dummy Attack). All remaining
 * decoded NPC actions are routed here through exact-current NPC definitions.
 */
final class LocalRoutedNpcInteractionHandler {
    private final NpcRegistry npcs;
    private final BankState bank;
    private final MovementState movement;

    private Integer pendingBankScene;
    private long pendingBankDeadlineMs;

    LocalRoutedNpcInteractionHandler(
        NpcRegistry npcs,
        BankState bank,
        MovementState movement
    ){
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
    }

    String handle(
        NpcAction request,
        NpcEntity clicked,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(request==null)return null;

        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(request,clicked);

        if(route.service==NpcInteractionRouter.Service.BANK && clicked!=null){
            if(adjacentTo(clicked.x,clicked.y)){
                pendingBankScene=null;
                return openBank(
                    clicked,
                    request,
                    route,
                    serverPackets,
                    "OPENED_ADJACENT_IMMEDIATE"
                );
            }

            pendingBankScene=clicked.sceneIndex;
            pendingBankDeadlineMs=System.currentTimeMillis()+10_000L;
            return "V511_NPC_BANK "+request+
                " clicked="+clicked+
                " route="+route+
                " distance="+chebyshev(
                    movement.x(),movement.y(),clicked.x,clicked.y)+
                " action=DEFERRED_UNTIL_ADJACENT";
        }

        NpcEntity pet=npcs.pet();
        return "V511_NPC_ACTION "+request+
            " route="+route+
            " result=DECODED_SEMANTIC_"+route.service+
            " clicked="+clicked+
            " petScene="+(pet==null?-1:pet.sceneIndex);
    }

    String tick(long now,ServerPacketWriter serverPackets)throws IOException{
        Integer scene=pendingBankScene;
        if(scene==null)return null;

        NpcEntity npc=npcs.scene(scene);
        if(npc==null||now>pendingBankDeadlineMs){
            pendingBankScene=null;
            return "V511_NPC_BANK scene="+scene+
                " action=CANCELLED_MISSING_OR_TIMEOUT";
        }

        if(!adjacentTo(npc.x,npc.y)){
            if(movement.queued()==0){
                pendingBankScene=null;
                return "V511_NPC_BANK scene="+scene+
                    " action=CANCELLED_PATH_ENDED_NOT_ADJACENT";
            }
            return null;
        }

        pendingBankScene=null;
        movement.clearQueuedPath();

        // Preserve the current R8.5 deferred-bank reconstruction exactly:
        // the arrival path synthesizes option-3/opcode17 before re-resolving.
        NpcAction synthetic=new NpcAction(17,scene);
        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(synthetic,npc);

        return openBank(
            npc,
            synthetic,
            route,
            serverPackets,
            "OPENED_AFTER_AUTHORITATIVE_ARRIVAL"
        );
    }

    boolean hasPendingBank(){
        return pendingBankScene!=null;
    }

    private String openBank(
        NpcEntity npc,
        NpcAction request,
        NpcInteractionRouter.Route route,
        ServerPacketWriter serverPackets,
        String reason
    )throws IOException{
        bank.open(serverPackets);
        return "V511_BANK_OPEN_NPC npc="+npc.definitionId+
            " scene="+npc.sceneIndex+
            " world="+npc.x+","+npc.y+
            " request="+request+
            " route="+route+
            " authorityWorld="+movement.x()+","+movement.y()+
            " distance="+chebyshev(
                movement.x(),movement.y(),npc.x,npc.y)+
            " root="+BankState.BANK_ROOT+
            " action="+reason;
    }

    private boolean adjacentTo(int x,int y){
        return chebyshev(movement.x(),movement.y(),x,y)<=1;
    }

    private static int chebyshev(int x0,int y0,int x1,int y1){
        return Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
    }
}
